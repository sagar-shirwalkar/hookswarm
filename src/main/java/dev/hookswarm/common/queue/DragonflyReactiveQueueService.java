package dev.hookswarm.common.queue;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class DragonflyReactiveQueueService implements ReactiveQueueService {

    private static final Logger log = LoggerFactory.getLogger(DragonflyReactiveQueueService.class);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ReactiveStreamOperations<String, String, String> streamOps;
    private final MeterRegistry meterRegistry;

    // Metrics
    private final Timer publishTimer;
    private final Timer readTimer;
    private final Timer ackTimer;
    private final Timer createGroupTimer;

    public DragonflyReactiveQueueService(
            ReactiveRedisTemplate<String, String> redisTemplate,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.streamOps = redisTemplate.opsForStream();
        this.meterRegistry = meterRegistry;

        this.publishTimer = Timer.builder("hookswarm.redis.publish")
                .description("Time to publish a message to a stream")
                .register(meterRegistry);
        this.readTimer = Timer.builder("hookswarm.redis.read")
                .description("Time to read a batch from a stream")
                .register(meterRegistry);
        this.ackTimer = Timer.builder("hookswarm.redis.ack")
                .description("Time to acknowledge a message")
                .register(meterRegistry);
        this.createGroupTimer = Timer.builder("hookswarm.redis.createGroup")
                .description("Time to create a consumer group")
                .register(meterRegistry);
    }

    @Override
    public Mono<String> publish(String stream, Map<String, String> body) {
        RecordId id = RecordId.autoGenerate();
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .in(stream)
                .withId(id)
                .ofMap(body);

        return publishTimer.record(() ->
                streamOps.add(record)
                        .map(RecordId::getValue)
                        .doOnError(e -> log.error("Failed to publish to stream {}: {}", stream, e.getMessage()))
        );
    }

    @Override
    public Mono<Void> createGroup(String stream, String group) {
        return createGroupTimer.record(() ->
                streamOps.createGroup(stream, ReadOffset.latest(), group)
                        .onErrorResume(e -> {
                            // Group already exists – this is normal
                            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                                log.debug("Consumer group {} already exists on stream {}", group, stream);
                                return Mono.empty();
                            }
                            log.error("Failed to create group {}/{}: {}", stream, group, e.getMessage());
                            return Mono.error(e);
                        })
                        .then()
        );
    }

    @Override
    public Flux<QueueMessage> read(String group, String consumer, String stream, int count, Duration block) {
        StreamReadOptions options = StreamReadOptions.empty()
                .count(count)
                .block(block);

        Timer.Sample sample = Timer.start(meterRegistry);

        return streamOps.read(
                        Consumer.from(group, consumer),
                        options,
                        StreamOffset.create(stream, ReadOffset.lastConsumed())
                )
                .map(this::toQueueMessage)  // FIX: Convert MapRecord to QueueMessage
                .doOnComplete(() -> {
                    sample.stop(readTimer);
                    if (log.isTraceEnabled()) {
                        log.trace("Read batch from {}/{}", stream, group);
                    }
                })
                .doOnError(e -> {
                    sample.stop(readTimer);
                    log.error("Failed to read from {}/{}: {}", stream, group, e.getMessage());
                });
    }

    @Override
    public Mono<Void> ack(String stream, String group, String messageId) {
        return ackTimer.record(() ->
                streamOps.acknowledge(stream, group, messageId)
                        .then()
                        .doOnSuccess(unused -> {
                            if (log.isTraceEnabled()) {
                                log.trace("Acked message {} in {}/{}", messageId, stream, group);
                            }
                        })
                        .doOnError(e -> log.error("Failed to ack {} in {}/{}: {}", messageId, stream, group, e.getMessage()))
        );
    }

    @Override
    public Mono<Void> deadLetter(String fromStream, String messageId, Map<String, String> body, Throwable error) {
        String dlqStream = "dlq:" + fromStream;

        // Add error metadata
        Map<String, String> dlqBody = new HashMap<>(body);
        dlqBody.put("_error", error.getMessage());
        dlqBody.put("_original_stream", fromStream);
        dlqBody.put("_original_message_id", messageId);
        dlqBody.put("_dead_at", String.valueOf(System.currentTimeMillis()));

        return publish(dlqStream, dlqBody)
                .flatMap(id -> ack(fromStream, extractGroup(fromStream), messageId))
                .doOnSuccess(unused -> log.warn("Message {} moved to DLQ {}", messageId, dlqStream))
                .then();
    }

    @Override
    public Mono<Long> streamLength(String stream) {
        return streamOps.size(stream);
    }

    @Override
    public Mono<Map<String, Object>> pendingSummary(String stream, String group) {
        return streamOps.pending(stream, group)
                .map(summary -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("total", summary.getTotalPendingMessages());

                    // Correct way to get min/max IDs from range
                    Range<String> idRange = summary.getIdRange();
                    result.put("minId", idRange.getLowerBound().getValue().orElse(null));
                    result.put("maxId", idRange.getUpperBound().getValue().orElse(null));
                    result.put("consumers", summary.getPendingMessagesPerConsumer());

                    return result;
                })
                .doOnError(e -> log.error("Failed to get pending summary for {}/{}: {}",
                        stream, group, e.getMessage()));
    }

    // ========== Private Helpers ==========

    private QueueMessage toQueueMessage(MapRecord<String, String, String> record) {
        return new QueueMessage(record.getId().getValue(), record.getValue());
    }

    /**
     * Extract consumer group name from stream name.
     * This is a convention – you may want to pass the group explicitly.
     */
    private String extractGroup(String stream) {
        if (stream.startsWith("events")) {
            return "fanout-group";
        } else if (stream.startsWith("deliveries")) {
            return "delivery-group";
        }
        return "default-group";
    }

}
