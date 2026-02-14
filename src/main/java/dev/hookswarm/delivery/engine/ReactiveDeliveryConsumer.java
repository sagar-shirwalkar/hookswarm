package dev.hookswarm.delivery.engine;

import dev.hookswarm.common.UlidGenerator;
import dev.hookswarm.common.queue.QueueMessage;
import dev.hookswarm.common.queue.ReactiveQueueService;
import dev.hookswarm.delivery.model.*;
import dev.hookswarm.delivery.repository.*;
import dev.hookswarm.delivery.signing.WebhookSigner;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.service.ReactiveSubscriptionService;
import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@Component
public class ReactiveDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReactiveDeliveryConsumer.class);

    private static final String DELIVERY_STREAM = "deliveries";
    private static final String DELIVERY_GROUP = "delivery-group";
    private static final String SIGNATURE_HEADER = "X-Hook-Signature";

    private final ReactiveQueueService queueService;
    private final ReactiveSubscriptionService subscriptionService;
    private final ReactiveDeliveryTaskRepository taskRepository;
    private final ReactiveDeliveryAttemptRepository attemptRepository;
    private final ReactiveDeadLetterRepository deadLetterRepository;
    private final ReactiveCircuitBreakerManager circuitBreaker;
    private final ReactiveRetryPolicy retryPolicy;
    private final WebhookSigner webhookSigner;
    private final WebClient webClient;

    // Configuration
    private final String consumerId;
    private final int pollBatchSize;
    private final Duration pollBlockTimeout;
    private final int concurrency;
    private final Duration httpTimeout;

    // Metrics
    private final Counter messagesReceived;
    private final Counter deliveriesSuccess;
    private final Counter deliveriesFailure;
    private final Counter circuitBreakerOpens;
    private final Timer deliveryTimer;
    private final AtomicLong inFlightCount = new AtomicLong(0);

    private final Function<QueueMessage, Mono<DeliveryTask>> deserializer;
    private final Function<Instant, OffsetDateTime> toOffsetDateTime;

    public ReactiveDeliveryConsumer(
            ReactiveQueueService queueService,
            ReactiveSubscriptionService subscriptionService,
            ReactiveDeliveryTaskRepository taskRepository,
            ReactiveDeliveryAttemptRepository attemptRepository,
            ReactiveDeadLetterRepository deadLetterRepository,
            ReactiveCircuitBreakerManager circuitBreaker,
            ReactiveRetryPolicy retryPolicy,
            WebhookSigner webhookSigner,
            WebClient.Builder webClientBuilder,
            MeterRegistry meterRegistry,
            @Value("${HOSTNAME:delivery-worker-${random.uuid}}") String consumerId,
            @Value("${hookswarm.delivery.poll-batch-size:20}") int pollBatchSize,
            @Value("${hookswarm.delivery.poll-block-timeout-ms:2000}") long pollBlockTimeoutMs,
            @Value("${hookswarm.delivery.concurrency:10}") int concurrency,
            @Value("${hookswarm.delivery.http-timeout-seconds:10}") long httpTimeoutSeconds) {

        this.queueService = queueService;
        this.subscriptionService = subscriptionService;
        this.taskRepository = taskRepository;
        this.attemptRepository = attemptRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.circuitBreaker = circuitBreaker;
        this.retryPolicy = retryPolicy;
        this.webhookSigner = webhookSigner;
        this.webClient = webClientBuilder
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        this.consumerId = consumerId;
        this.pollBatchSize = pollBatchSize;
        this.pollBlockTimeout = Duration.ofMillis(pollBlockTimeoutMs);
        this.concurrency = concurrency;
        this.httpTimeout = Duration.ofSeconds(httpTimeoutSeconds);

        // Predefine pure funcs
        this.deserializer = this::deserializeTask;
        this.toOffsetDateTime = instant -> OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);

        // Register metrics
        this.messagesReceived = Counter.builder("hookswarm.delivery.messages.received")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.deliveriesSuccess = Counter.builder("hookswarm.delivery.success")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.deliveriesFailure = Counter.builder("hookswarm.delivery.failure")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.circuitBreakerOpens = Counter.builder("hookswarm.delivery.circuitbreaker.open")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.deliveryTimer = Timer.builder("hookswarm.delivery.duration")
                .tag("consumer", consumerId)
                .register(meterRegistry);

        meterRegistry.gauge("hookswarm.delivery.inflight", inFlightCount);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        log.info("Starting ReactiveDeliveryConsumer with ID: {}", consumerId);
        queueService.createGroup(DELIVERY_STREAM, DELIVERY_GROUP)
                .doOnSuccess(unused -> log.info("Delivery consumer {} ready", consumerId))
                .thenMany(consumeStream())
                .subscribe(
                        null,
                        error -> log.error("Fatal error in delivery consumer", error),
                        () -> log.warn("Delivery consumer completed unexpectedly")
                );
    }

    /**
     * Main consumption loop with backpressure and error recovery.
     */
    private Flux<Void> consumeStream() {
        return Flux.defer(this::pollMessages)
                .doOnNext(msg -> {
                    messagesReceived.increment();
                    inFlightCount.incrementAndGet();
                })
                .flatMap(this::processMessage, concurrency)
                .doFinally(signal -> inFlightCount.set(0))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofMinutes(1))
                        .doBeforeRetry(signal ->
                                log.error("Consumer error, retrying: {}", signal.failure().getMessage())
                        ))
                .repeat();
    }

    private Flux<QueueMessage> pollMessages() {
        return queueService.read(
                DELIVERY_GROUP,
                consumerId,
                DELIVERY_STREAM,
                pollBatchSize,
                pollBlockTimeout
        );
    }

    /**
     * Process single message - optimized flat chain.
     */
    private Mono<Void> processMessage(QueueMessage message) {
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start();

            return deserializer.apply(message)
                    .flatMap(taskRepository::save)
                    .flatMap(this::executeDelivery)
                    .flatMap(context -> ackMessage(message)
                            .doOnSuccess(unused -> sample.stop(deliveryTimer))
                    )
                    .doFinally(signal -> inFlightCount.decrementAndGet())
                    .onErrorResume(error -> handleProcessingError(message, error));
        });
    }

    /**
     * Execute delivery with circuit breaker check
     */
    private Mono<DeliveryContext> executeDelivery(DeliveryTask task) {
        return subscriptionService.getById(task.subscriptionId())
                .flatMap(subscription ->
                        circuitBreaker.isOpen(subscription.id())
                                .flatMap(isOpen -> isOpen
                                        ? handleCircuitOpen(task, subscription)
                                        : attemptDelivery(task, subscription)
                                )
                );
    }

    /**
     * Attempt webhook delivery
     */
    private Mono<DeliveryContext> attemptDelivery(DeliveryTask task, Subscription subscription) {
        Instant start = Instant.now();

        return webClient.post()
                .uri(task.url())
                .headers(headers -> {
                    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                    headers.set(SIGNATURE_HEADER, webhookSigner.sign(task.payload(), subscription.secret()));
                })
                .bodyValue(task.payload())
                .exchangeToMono(response -> {
                    long latencyMs = Duration.between(start, Instant.now()).toMillis();

                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> new DeliveryContext(
                                    task,
                                    subscription,
                                    createResult(response.statusCode().value(), body, latencyMs)
                            ))
                            .flatMap(ctx -> ctx.result.success()
                                    ? handleSuccess(ctx)
                                    : handleFailure(ctx)
                            );
                })
                .timeout(httpTimeout)
                .onErrorResume(error -> handleDeliveryError(task, subscription, start, error));
    }

    /**
     * Handle successful delivery - parallel DB writes.
     */
    private Mono<DeliveryContext> handleSuccess(DeliveryContext ctx) {
        OffsetDateTime now = toOffsetDateTime.apply(Instant.now());

        DeliveryAttempt attempt = new DeliveryAttempt(
                UlidGenerator.newUlid(),
                ctx.task.id(),
                ctx.task.attemptCount() + 1,
                ctx.result.statusCode(),
                ctx.result.responseBody(),
                ctx.result.latencyMs(),
                null,
                now
        );

        return Mono.zip(
                        attemptRepository.save(attempt),
                        circuitBreaker.recordSuccess(ctx.subscription.id()),
                        taskRepository.markDelivered(ctx.task.id(), now)
                )
                .doOnSuccess(unused -> {
                    deliveriesSuccess.increment();
                    if (log.isDebugEnabled()) {
                        log.debug("Task {} delivered successfully", ctx.task.id());
                    }
                })
                .thenReturn(ctx);
    }

    /**
     * Handle failed delivery
     */
    private Mono<DeliveryContext> handleFailure(DeliveryContext ctx) {
        deliveriesFailure.increment();
        OffsetDateTime now = toOffsetDateTime.apply(Instant.now());
        int newAttempt = ctx.task.attemptCount() + 1;

        DeliveryAttempt attempt = new DeliveryAttempt(
                UlidGenerator.newUlid(),
                ctx.task.id(),
                newAttempt,
                ctx.result.statusCode(),
                ctx.result.responseBody(),
                ctx.result.latencyMs(),
                ctx.result.errorMessage(),
                now
        );

        return attemptRepository.save(attempt)
                .then(circuitBreaker.recordFailure(ctx.subscription.id()))
                .then(Mono.defer(() -> {
                    if (newAttempt >= ctx.subscription.maxRetries()) {
                        return moveToDLQ(ctx, newAttempt, now);
                    } else {
                        return scheduleRetry(ctx, newAttempt, now);
                    }
                }))
                .thenReturn(ctx);
    }

    /**
     * Move task to dead letter queue.
     */
    private Mono<Void> moveToDLQ(DeliveryContext ctx, int attempts, OffsetDateTime now) {
        DeadLetterEntry dead = new DeadLetterEntry(
                UlidGenerator.newUlid(),
                ctx.task.id(),
                ctx.task.eventId(),
                ctx.task.subscriptionId(),
                attempts,
                ctx.result.errorMessage(),
                now
        );

        return Mono.zip(
                        deadLetterRepository.save(dead),
                        taskRepository.markDead(ctx.task.id(), now)
                )
                .doOnSuccess(unused ->
                        log.warn("Task {} moved to DLQ after {} attempts", ctx.task.id(), attempts)
                )
                .then();
    }

    /**
     * Schedule retry with exponential backoff.
     */
    private Mono<Void> scheduleRetry(DeliveryContext ctx, int newAttempt, OffsetDateTime now) {
        return retryPolicy.nextAttemptTime(newAttempt)
                .flatMap(nextAttempt ->
                        taskRepository.markFailed(ctx.task.id(), newAttempt, nextAttempt, now)
                )
                .doOnSuccess(unused -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Task {} scheduled for retry (attempt {})", ctx.task.id(), newAttempt);
                    }
                }).then();
    }

    /**
     * Handle circuit breaker open state.
     */
    private Mono<DeliveryContext> handleCircuitOpen(DeliveryTask task, Subscription subscription) {
        circuitBreakerOpens.increment();
        OffsetDateTime now = toOffsetDateTime.apply(Instant.now());

        return taskRepository.resetToPending(task.id(), now.plusMinutes(5), now)
                .doOnSuccess(unused ->
                        log.debug("Task {} deferred - circuit open", task.id())
                )
                .thenReturn(new DeliveryContext(task, subscription, null));
    }

    /**
     * Handle delivery errors (timeout, connection refused, etc).
     */
    private Mono<DeliveryContext> handleDeliveryError(
            DeliveryTask task,
            Subscription subscription,
            Instant start,
            Throwable error) {

        long latencyMs = Duration.between(start, Instant.now()).toMillis();
        DeliveryResult result = DeliveryResult.failure(
                error.getMessage(),
                0,
                latencyMs
        );

        return handleFailure(new DeliveryContext(task, subscription, result));
    }

    /**
     * Acknowledge message after successful processing.
     */
    private Mono<Void> ackMessage(QueueMessage message) {
        return queueService.ack(DELIVERY_STREAM, DELIVERY_GROUP, message.id());
    }

    /**
     * Handle processing errors - move to DLQ without ack.
     */
    private Mono<Void> handleProcessingError(QueueMessage message, Throwable error) {
        log.error("Failed to process message {}: {}", message.id(), error.getMessage());

        return queueService.deadLetter(DELIVERY_STREAM, message.id(), message.body(), error)
                .doOnError(e -> log.error("Failed to DLQ message {}", message.id(), e))
                .onErrorResume(e -> Mono.empty()); // Don't fail the stream
    }

    /**
     * Deserialize queue message to delivery task.
     */
    private Mono<DeliveryTask> deserializeTask(QueueMessage msg) {
        return Mono.defer(() -> {
            Map<String, String> body = msg.body();
            OffsetDateTime now = toOffsetDateTime.apply(Instant.now());

            return Mono.just(new DeliveryTask(
                    body.get("taskId"),
                    body.get("eventId"),
                    body.get("subscriptionId"),
                    body.get("url"),
                    body.get("secret"),
                    body.get("payload"),
                    DeliveryStatus.PENDING,
                    0,
                    now,
                    now,
                    now
            ));
        });
    }

    /**
     * Create delivery result from HTTP response.
     */
    private DeliveryResult createResult(int statusCode, String body, long latencyMs) {
        if (statusCode >= 200 && statusCode < 300) {
            return DeliveryResult.success(body, statusCode, latencyMs);
        } else {
            return DeliveryResult.failure(
                    "HTTP " + statusCode + ": " + body,
                    statusCode,
                    latencyMs
            );
        }
    }

    // ========== Internal Context Record ==========

    /**
     * Immutable context for delivery processing.
     */
    private record DeliveryContext(
            DeliveryTask task,
            Subscription subscription,
            DeliveryResult result
    ) {}

}
