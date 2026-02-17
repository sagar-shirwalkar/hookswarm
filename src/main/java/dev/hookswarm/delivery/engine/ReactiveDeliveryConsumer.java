package dev.hookswarm.delivery.engine;


import dev.hookswarm.common.UlidGenerator;
import dev.hookswarm.common.config.HookSwarmProperties;
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
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@Component
public class ReactiveDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReactiveDeliveryConsumer.class);

    private static final String DELIVERY_GROUP = "delivery-group";
    private static final String SIGNATURE_HEADER = "X-Hook-Signature";
    private static final String FALLBACK_STREAM = "deliveries.single";

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
    private final int perEndpointMaxConcurrency;
    private final List<String> targetStreams; // streams to consume from

    // Metrics
    private final Counter messagesReceived;
    private final Counter deliveriesSuccess;
    private final Counter deliveriesFailure;
    private final Counter circuitBreakerOpens;
    private final Timer deliveryTimer;
    private final AtomicLong inFlightCount = new AtomicLong(0);

    private final Function<QueueMessage, Mono<DeliveryTask>> deserializer;
    private final Function<Instant, OffsetDateTime> toOffsetDateTime;

    private final ConcurrentHashMap<String, Semaphore> endpointSemaphores = new ConcurrentHashMap<>();

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
            HookSwarmProperties properties,
            @Value("${HOSTNAME:delivery-worker-${random.uuid}}") String consumerId,
            @Value("${hookswarm.delivery.poll-batch-size:20}") int pollBatchSize,
            @Value("${hookswarm.delivery.poll-block-timeout-ms:2000}") long pollBlockTimeoutMs,
            @Value("${hookswarm.delivery.concurrency:20}") int concurrency,
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

        var sharding = properties.delivery().sharding();
        if (sharding.enabled()) {
            this.targetStreams = new ArrayList<>();
            for (int i = 0; i < sharding.numberOfShards(); i++) {
                targetStreams.add(sharding.streamPrefix() + "." + i);
            }
        } else {
            this.targetStreams = List.of(FALLBACK_STREAM);
        }
        this.perEndpointMaxConcurrency = properties.delivery().perEndpointMaxConcurrency();

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
        log.info("Starting ReactiveDeliveryConsumer with ID: {}, streams: {}", consumerId, targetStreams);
        Flux.fromIterable(targetStreams)
                .flatMap(stream -> queueService.createGroup(stream, DELIVERY_GROUP))
                .thenMany(consumeStreams())
                .subscribe(
                        null,
                        error -> log.error("Fatal error in delivery consumer", error),
                        () -> log.warn("Delivery consumer completed unexpectedly")
                );
    }

    private Flux<Void> consumeStreams() {
        return Flux.fromIterable(targetStreams)
                .flatMap(stream ->
                        Flux.defer(() -> pollMessages(stream))
                                .map(msg -> new StreamMessage(stream, msg))
                                .doOnNext(sm -> {
                                    messagesReceived.increment();
                                    inFlightCount.incrementAndGet();
                                })
                                .flatMap(this::processMessage, concurrency)
                )
                .doFinally(signal -> inFlightCount.set(0))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofMinutes(1))
                        .doBeforeRetry(signal ->
                                log.error("Consumer error, retrying: {}", signal.failure().getMessage())
                        ))
                .repeat();
    }

    private Flux<QueueMessage> pollMessages(String stream) {
        return queueService.read(DELIVERY_GROUP, consumerId, stream, pollBatchSize, pollBlockTimeout);
    }

    private Mono<Void> processMessage(StreamMessage sm) {
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start();

            return deserializer.apply(sm.message)
                    .flatMap(this::ensureTaskExists)
                    .flatMap(this::executeDelivery)
                    .flatMap(context -> ackMessage(sm.stream, sm.message)
                            .doOnSuccess(unused -> sample.stop(deliveryTimer))
                    )
                    .doFinally(signal -> inFlightCount.decrementAndGet())
                    .onErrorResume(error -> handleProcessingError(sm.stream, sm.message, error));
        });
    }

    private Mono<DeliveryTask> ensureTaskExists(DeliveryTask task) {
        return taskRepository.findById(task.id())
                .switchIfEmpty(Mono.error(new TaskNotFoundException(task.id())))
                .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofSeconds(2))
                        .filter(throwable -> throwable instanceof TaskNotFoundException))
                .doOnError(e -> log.error("Task {} still missing after retries", task.id()));
    }

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

    private Mono<DeliveryContext> attemptDelivery(DeliveryTask task, Subscription subscription) {
        if (perEndpointMaxConcurrency <= 0) {
            return doAttemptDelivery(task, subscription);
        }
        String endpointKey = subscription.url();
        Semaphore semaphore = endpointSemaphores.computeIfAbsent(endpointKey,
                k -> new Semaphore(perEndpointMaxConcurrency));
        return Mono.using(
                () -> {
                    try {
                        semaphore.acquire();
                        return semaphore;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                },
                s -> doAttemptDelivery(task, subscription),
                Semaphore::release
        ).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<DeliveryContext> doAttemptDelivery(DeliveryTask task, Subscription subscription) {
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

    private Mono<DeliveryContext> handleCircuitOpen(DeliveryTask task, Subscription subscription) {
        circuitBreakerOpens.increment();
        OffsetDateTime now = toOffsetDateTime.apply(Instant.now());

        return taskRepository.resetToPending(task.id(), now.plusMinutes(5), now)
                .doOnSuccess(unused ->
                        log.debug("Task {} deferred - circuit open", task.id())
                )
                .thenReturn(new DeliveryContext(task, subscription, null));
    }

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

    private Mono<Void> ackMessage(String stream, QueueMessage message) {
        return queueService.ack(stream, DELIVERY_GROUP, message.id());
    }

    private Mono<Void> handleProcessingError(String stream, QueueMessage message, Throwable error) {
        log.error("Failed to process message {} from stream {}: {}", message.id(), stream, error.getMessage());

        return queueService.deadLetter(stream, message.id(), message.body(), error)
                .doOnError(e -> log.error("Failed to DLQ message {} from stream {}", message.id(), stream, e))
                .onErrorResume(e -> Mono.empty());
    }

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

    private static class TaskNotFoundException extends RuntimeException {
        TaskNotFoundException(String id) {
            super("Task not found: " + id);
        }
    }

    private record StreamMessage(String stream, QueueMessage message) {}

    private record DeliveryContext(
            DeliveryTask task,
            Subscription subscription,
            DeliveryResult result
    ) {}
}

