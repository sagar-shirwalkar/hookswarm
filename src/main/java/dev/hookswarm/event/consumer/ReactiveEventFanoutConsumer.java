package dev.hookswarm.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hookswarm.common.UlidGenerator;
import dev.hookswarm.common.queue.QueueMessage;
import dev.hookswarm.common.queue.ReactiveQueueService;
import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.delivery.model.DeliveryTask;
import dev.hookswarm.delivery.repository.ReactiveDeliveryTaskRepository;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.service.ReactiveSubscriptionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ReactiveEventFanoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReactiveEventFanoutConsumer.class);
    private static final String EVENT_STREAM = "events";
    private static final String DELIVERY_STREAM = "deliveries";
    private static final String FANOUT_GROUP = "fanout-group";

    private final ReactiveQueueService reactiveQueueService;
    private final ReactiveSubscriptionService subscriptionService;
    private final ReactiveDeliveryTaskRepository deliveryTaskRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String consumerId;
    private final int pollBatchSize;
    private final Duration pollBlockTimeout;
    private final int concurrency;

    // Metrics
    private final Counter eventsReceived;
    private final Counter subscriptionsFound;
    private final Counter tasksCreated;
    private final Counter tasksPublished;
    private final Counter fanoutErrors;
    private final Timer fanoutProcessingTime;
    private final AtomicLong inFlightCount = new AtomicLong(0); // for gauge

    public ReactiveEventFanoutConsumer(
            ReactiveQueueService reactiveQueueService,
            ReactiveSubscriptionService subscriptionService,
            ReactiveDeliveryTaskRepository deliveryTaskRepository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${HOSTNAME:fanout-worker-${random.uuid}}") String consumerId,
            @Value("${hookswarm.fanout.poll-batch-size:20}") int pollBatchSize,
            @Value("${hookswarm.fanout.poll-block-timeout-ms:2000}") long pollBlockTimeoutMs,
            @Value("${hookswarm.fanout.concurrency:4}") int concurrency) {

        this.reactiveQueueService = reactiveQueueService;
        this.subscriptionService = subscriptionService;
        this.deliveryTaskRepository = deliveryTaskRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.consumerId = consumerId;
        this.pollBatchSize = pollBatchSize;
        this.pollBlockTimeout = Duration.ofMillis(pollBlockTimeoutMs);
        this.concurrency = concurrency;

        // Register metrics
        this.eventsReceived = Counter.builder("hookswarm.fanout.events.received")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.subscriptionsFound = Counter.builder("hookswarm.fanout.subscriptions.found")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.tasksCreated = Counter.builder("hookswarm.fanout.tasks.created")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.tasksPublished = Counter.builder("hookswarm.fanout.tasks.published")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.fanoutErrors = Counter.builder("hookswarm.fanout.errors")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.fanoutProcessingTime = Timer.builder("hookswarm.fanout.processing.time")
                .tag("consumer", consumerId)
                .register(meterRegistry);

        // Gauge for in-flight messages
        meterRegistry.gauge("hookswarm.fanout.inflight", inFlightCount);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        reactiveQueueService.createGroup(EVENT_STREAM, FANOUT_GROUP)
                .doOnSuccess(unused -> log.info("Fanout consumer group ready: {}", FANOUT_GROUP))
                .thenMany(consumeStream())
                .subscribe(
                        null,
                        error -> log.error("Fatal error in fanout consumer, terminating", error),
                        () -> log.warn("Fanout consumer completed (should never happen)")
                );
    }

    /**
     * Infinite reactive stream: polls events, fans out, acks.
     */
    private Flux<Void> consumeStream() {
        return Flux.defer(this::pollMessages)
                .doOnNext(msg -> inFlightCount.incrementAndGet())
                .flatMapSequential(this::processMessage, concurrency)
                .doFinally(signalType -> {
                    if (signalType == SignalType.ON_COMPLETE || signalType == SignalType.CANCEL) {
                        inFlightCount.set(0);
                    }
                })
                .doOnError(e -> log.error("Error in consumeStream, restarting", e))
                .retry()
                .repeat();
    }

    /**
     * Poll a batch of messages from the event stream.
     */
    private Flux<QueueMessage> pollMessages() {
        return reactiveQueueService.read(FANOUT_GROUP, consumerId, EVENT_STREAM,
                        pollBatchSize, pollBlockTimeout)
                .doOnNext(msg -> {
                    eventsReceived.increment();
                    log.debug("Received event message: {}", msg.id());
                });
    }

    /**
     * Process a single event message:
     * 1. Validate event type
     * 2. Fetch active subscriptions
     * 3. For each subscription: create and persist DeliveryTask, then publish to deliveries stream
     * 4. Acknowledge event message after all tasks are successfully created and published
     */
    private Mono<Void> processMessage(QueueMessage message) {
        return fanoutProcessingTime.record(() ->
                validateEventType(message)
                        .flatMap(eventData -> fanout(eventData)
                                .then(Mono.defer(() -> ackMessage(message.id())))
                        )
                        .doOnError(error -> {
                            fanoutErrors.increment();
                            log.error("Failed to process message {}: {}", message.id(), error.getMessage());
                            reactiveQueueService.deadLetter(EVENT_STREAM, message.id(),
                                            message.body(), error)
                                    .doOnSuccess(unused -> log.info("Message {} moved to DLQ", message.id()))
                                    .subscribe();
                        })
                        .doFinally(signalType -> inFlightCount.decrementAndGet())
                        .onErrorResume(e -> Mono.empty()) // error already handled
        );
    }

    /**
     * Ensure eventType is present and non‑blank.
     */
    private Mono<Map<String, String>> validateEventType(QueueMessage msg) {
        String eventType = msg.body().get("eventType");
        if (eventType == null || eventType.isBlank()) {
            return Mono.error(new IllegalArgumentException("Missing or blank eventType in message: " + msg.id()));
        }
        return Mono.just(msg.body());
    }

    /**
     * Fan out event to all matching subscriptions.
     * For each subscription:
     * - Create and persist a DeliveryTask (reactive)
     * - Publish a corresponding message to the deliveries stream
     * - Count successes and errors
     */
    private Mono<Void> fanout(Map<String, String> eventData) {
        String eventType = eventData.get("eventType");
        return subscriptionService.getActiveByEventType(eventType)
                .flatMap(subscription -> processSubscription(subscription, eventData), concurrency * 2)
                .doOnComplete(() -> log.info("Fanout complete for event {} (type: {})",
                        eventData.get("eventId"), eventType))
                .then();
    }

    /**
     * Process a single subscription:
     * 1. Create DeliveryTask entity
     * 2. Save it to PostgreSQL (reactive)
     * 3. Publish a message to the deliveries stream
     */
    private Mono<Void> processSubscription(Subscription subscription, Map<String, String> eventData) {
        subscriptionsFound.increment();

        // Create the task entity
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String taskId = UlidGenerator.newUlid();
        DeliveryTask task = new DeliveryTask(
                taskId,
                eventData.get("eventId"),
                subscription.id(),
                subscription.url(),
                subscription.secret(),
                eventData.get("payload"),
                DeliveryStatus.PENDING,
                0,
                now, // immediate delivery
                now,
                now
        );

        // Save task to DB, then publish to stream
        return deliveryTaskRepository.save(task)
                .doOnSuccess(savedTask -> {
                    tasksCreated.increment();
                    log.debug("DeliveryTask {} created for subscription {}", savedTask.id(), subscription.id());
                })
                .flatMap(savedTask -> publishDeliveryTask(savedTask, eventData))
                .doOnError(e -> log.error("Failed to process subscription {} for event {}: {}",
                        subscription.id(), eventData.get("eventId"), e.getMessage()));
    }

    /**
     * Publish a message to the deliveries stream containing all data needed for delivery.
     */
    private Mono<Void> publishDeliveryTask(DeliveryTask task, Map<String, String> eventData) {
        Map<String, String> message = Map.of(
                "taskId", task.id(),
                "eventId", task.eventId(),
                "subscriptionId", task.subscriptionId(),
                "url", task.url(),
                "secret", task.secret(),
                "payload", task.payload(),
                "retryCount", String.valueOf(task.attemptCount())
        );

        return reactiveQueueService.publish(DELIVERY_STREAM, message)
                .doOnNext(msgId -> {
                    tasksPublished.increment();
                    log.debug("Delivery task {} published to stream with message ID {}", task.id(), msgId);
                })
                .then();
    }

    /**
     * Acknowledge the original event message.
     */
    private Mono<Void> ackMessage(String messageId) {
        return reactiveQueueService.ack(EVENT_STREAM, FANOUT_GROUP, messageId)
                .doOnSuccess(unused -> log.debug("Acknowledged event message {}", messageId));
    }

}