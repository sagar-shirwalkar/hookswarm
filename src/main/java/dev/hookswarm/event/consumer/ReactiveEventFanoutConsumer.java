package dev.hookswarm.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hookswarm.common.UlidGenerator;
import dev.hookswarm.common.config.HookSwarmProperties;
import dev.hookswarm.common.queue.QueueMessage;
import dev.hookswarm.common.queue.ReactiveQueueService;
import dev.hookswarm.subscription.cache.ReactiveSubscriptionCache;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ReactiveEventFanoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReactiveEventFanoutConsumer.class);
    private static final String EVENT_STREAM = "events";
    private static final String FANOUT_GROUP = "fanout-group";
    private static final String FALLBACK_STREAM = "deliveries.single";

    private final ReactiveQueueService reactiveQueueService;
    private final ReactiveSubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final HookSwarmProperties properties;
    private final ReactiveSubscriptionCache subscriptionCache;
    private final String consumerId;
    private final int pollBatchSize;
    private final Duration pollBlockTimeout;
    private final int concurrency;

    // Sharding configuration
    private final boolean shardingEnabled;
    private final int numberOfShards;
    private final String streamPrefix;
    private final List<String> targetStreams; // streams to publish to
    // Metrics
    private final Counter eventsReceived;
    private final Counter subscriptionsFound;
    private final Counter tasksGenerated;
    private final Counter fanoutErrors;
    private final Timer fanoutProcessingTime;
    private final AtomicLong inFlightCount = new AtomicLong(0);

    public ReactiveEventFanoutConsumer(
            ReactiveQueueService reactiveQueueService,
            ReactiveSubscriptionService subscriptionService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            HookSwarmProperties properties,
            ReactiveSubscriptionCache subscriptionCache,
            @Value("${HOSTNAME:fanout-worker-${random.uuid}}") String consumerId,
            @Value("${hookswarm.fanout.poll-batch-size:20}") int pollBatchSize,
            @Value("${hookswarm.fanout.poll-block-timeout-ms:2000}") long pollBlockTimeoutMs,
            @Value("${hookswarm.fanout.concurrency:4}") int concurrency) {

        this.reactiveQueueService = reactiveQueueService;
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.subscriptionCache = subscriptionCache;
        this.consumerId = consumerId;
        this.pollBatchSize = pollBatchSize;
        this.pollBlockTimeout = Duration.ofMillis(pollBlockTimeoutMs);
        this.concurrency = concurrency;

        // Sharding setup
        var sharding = properties.delivery().sharding();
        this.shardingEnabled = sharding.enabled();
        this.numberOfShards = sharding.numberOfShards();
        this.streamPrefix = sharding.streamPrefix();
        this.targetStreams = new ArrayList<>();
        if (shardingEnabled) {
            for (int i = 0; i < numberOfShards; i++) {
                targetStreams.add(streamPrefix + "." + i);
            }
        } else {
            targetStreams.add(FALLBACK_STREAM);
        }

        this.eventsReceived = Counter.builder("hookswarm.fanout.events.received")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.subscriptionsFound = Counter.builder("hookswarm.fanout.subscriptions.found")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.tasksGenerated = Counter.builder("hookswarm.fanout.tasks.generated")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.fanoutErrors = Counter.builder("hookswarm.fanout.errors")
                .tag("consumer", consumerId)
                .register(meterRegistry);
        this.fanoutProcessingTime = Timer.builder("hookswarm.fanout.processing.time")
                .tag("consumer", consumerId)
                .register(meterRegistry);

        meterRegistry.gauge("hookswarm.fanout.inflight", inFlightCount);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        reactiveQueueService.createGroup(EVENT_STREAM, FANOUT_GROUP)
                .doOnSuccess(_ -> log.info("Fanout consumer group ready: {}", FANOUT_GROUP))
                .thenMany(consumeStream())
                .subscribe(
                        null,
                        error -> log.error("Fatal error in fanout consumer, terminating", error),
                        () -> log.warn("Fanout consumer completed - should never happen!")
                );
    }

    private Flux<Void> consumeStream() {
        return Flux.defer(this::pollMessages)
                .doOnNext(_ -> inFlightCount.incrementAndGet())
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

    private Flux<QueueMessage> pollMessages() {
        return reactiveQueueService.read(FANOUT_GROUP, consumerId, EVENT_STREAM,
                        pollBatchSize, pollBlockTimeout)
                .doOnNext(msg -> {
                    eventsReceived.increment();
                    log.debug("Received event message: {}", msg.id());
                });
    }

    private Mono<Void> processMessage(QueueMessage message) {
        return fanoutProcessingTime.record(() ->
                validateEventType(message)
                        .flatMap(eventData -> checkBackpressure().thenReturn(eventData))
                        .flatMap(this::fanout)
                        .then(ackMessage(message.id()))
                        .doOnError(error -> {
                            fanoutErrors.increment();
                            log.error("Failed to process message {}: {}", message.id(), error.getMessage());
                            reactiveQueueService.deadLetter(EVENT_STREAM, message.id(),
                                            message.body(), error)
                                    .doOnSuccess(unused -> log.info("Message {} moved to DLQ", message.id()))
                                    .subscribe();
                        })
                        .doFinally(signalType -> inFlightCount.decrementAndGet())
                        .onErrorResume(e -> Mono.empty())
        );
    }

    // Check total pending messages across all target streams and apply backpressure if needed.
    private Mono<Void> checkBackpressure() {
        return Flux.fromIterable(targetStreams)
                .flatMap(reactiveQueueService::streamLength)
                .reduce(0L, Long::sum)
                .flatMap(total -> {
                    if (total > properties.fanout().maxPendingDeliveries()) {
                        log.debug("Backpressure: {} pending deliveries, sleeping {}ms",
                                total, properties.fanout().backpressureDelay().toMillis());
                        return Mono.delay(properties.fanout().backpressureDelay()).then();
                    }
                    return Mono.empty();
                });
    }

    private Mono<Map<String, String>> validateEventType(QueueMessage msg) {
        String eventType = msg.body().get("eventType");
        if (eventType == null || eventType.isBlank()) {
            return Mono.error(new IllegalArgumentException("Missing or blank eventType in message: " + msg.id()));
        }
        return Mono.just(msg.body());
    }

    private Mono<Void> fanout(Map<String, String> eventData) {
        String eventType = eventData.get("eventType");
        return getSubscriptions(eventType)
                .flatMapMany(Flux::fromIterable)
                .collectList()
                .flatMap(subs -> Flux.fromIterable(subs)
                        .flatMap(sub -> publishToTarget(sub, eventData), concurrency * 2)
                        .then())
                .doOnSuccess(unused ->
                        log.info("Fanout complete for event {} (type: {})", eventData.get("eventId"), eventType));
    }

    private Mono<Void> publishToTarget(Subscription subscription, Map<String, String> eventData) {
        String stream;
        if (shardingEnabled) {
            int shardIndex = Math.abs(subscription.id().hashCode()) % numberOfShards;
            stream = streamPrefix + "." + shardIndex;
        } else {
            stream = FALLBACK_STREAM;
        }

        String taskId = UlidGenerator.newUlid();
        Map<String, String> message = Map.of(
                "taskId", taskId,
                "eventId", eventData.get("eventId"),
                "subscriptionId", subscription.id(),
                "url", subscription.url(),
                "secret", subscription.secret(),
                "payload", eventData.get("payload"),
                "retryCount", "0"
        );

        return reactiveQueueService.publish(stream, message)
                .doOnNext(msgId -> {
                    tasksGenerated.increment();
                    log.debug("Delivery task {} published to stream {} with message ID {}", taskId, stream, msgId);
                })
                .then();
    }

    private Mono<List<Subscription>> getSubscriptions(String eventType) {
        return subscriptionCache.get(eventType)
                .switchIfEmpty(
                        subscriptionService.getActiveByEventType(eventType)
                                .collectList()
                                .flatMap(subs -> subscriptionCache.put(eventType, subs).thenReturn(subs))
                );
    }

    private Mono<Void> ackMessage(String messageId) {
        return reactiveQueueService.ack(EVENT_STREAM, FANOUT_GROUP, messageId)
                .doOnSuccess(_ -> log.debug("Acknowledged event message {}", messageId));
    }

}