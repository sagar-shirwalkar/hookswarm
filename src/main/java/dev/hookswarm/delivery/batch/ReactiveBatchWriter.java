package dev.hookswarm.delivery.batch;


import dev.hookswarm.common.UlidGenerator;
import dev.hookswarm.common.config.HookSwarmProperties;
import dev.hookswarm.common.queue.QueueMessage;
import dev.hookswarm.common.queue.ReactiveQueueService;
import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.delivery.model.DeliveryTask;
import io.micrometer.core.instrument.MeterRegistry;
import io.r2dbc.spi.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ReactiveBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(ReactiveBatchWriter.class);
    private static final String BATCH_WRITER_GROUP = "batch-writer-group";
    private static final String FALLBACK_STREAM = "deliveries.single";

    private final ReactiveQueueService queueService;
    private final R2dbcEntityTemplate r2dbcTemplate;
    private final HookSwarmProperties properties;
    private final MeterRegistry meterRegistry;
    private final String consumerId;
    private final List<String> targetStreams; // streams to consume from

    // Batching state
    private final Sinks.Many<StreamMessage> batchSink = Sinks.many().multicast().onBackpressureBuffer();
    private final AtomicLong batchCounter = new AtomicLong(0);
    private final AtomicLong inFlightCount = new AtomicLong(0);

    public ReactiveBatchWriter(
            ReactiveQueueService queueService,
            R2dbcEntityTemplate r2dbcTemplate,
            HookSwarmProperties properties,
            MeterRegistry meterRegistry,
            @Value("${HOSTNAME:batch-writer-${random.uuid}}") String consumerId) {
        this.queueService = queueService;
        this.r2dbcTemplate = r2dbcTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.consumerId = consumerId;

        // Determine target streams based on sharding configuration
        var sharding = properties.delivery().sharding();
        if (sharding.enabled()) {
            this.targetStreams = new ArrayList<>();
            for (int i = 0; i < sharding.numberOfShards(); i++) {
                targetStreams.add(sharding.streamPrefix() + "." + i);
            }
        } else {
            this.targetStreams = List.of(FALLBACK_STREAM);
        }

        meterRegistry.gauge("hookswarm.batchwriter.queue.size", batchSink, Sinks.Many::currentSubscriberCount);
        meterRegistry.gauge("hookswarm.batchwriter.inflight", inFlightCount, AtomicLong::get);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.batchWriter().enabled()) {
            log.info("Batch writer is disabled.");
            return;
        }

        log.info("Starting ReactiveBatchWriter with streams: {}", targetStreams);

        // Create consumer groups for each stream
        Flux.fromIterable(targetStreams)
                .flatMap(stream -> queueService.createGroup(stream, BATCH_WRITER_GROUP))
                .thenMany(consumeStreams())
                .subscribe(
                        null,
                        error -> log.error("Fatal error in batch writer", error),
                        () -> log.warn("Batch writer completed (should never happen)")
                );

        // Start the flusher
        startFlusher();
    }

    private Flux<Void> consumeStreams() {
        return Flux.interval(Duration.ZERO, properties.batchWriter().flushInterval())
                .flatMap(tick -> Flux.fromIterable(targetStreams)
                        .flatMap(stream -> queueService.read(BATCH_WRITER_GROUP, consumerId, stream,
                                        properties.batchWriter().batchSize(), Duration.ZERO)
                                .map(message -> new StreamMessage(stream, message)))
                        .flatMap(this::bufferMessage)
                        .then(), properties.batchWriter().concurrency())
                .doOnError(e -> log.error("Error in consumeStreams", e))
                .retry()
                .repeat();
    }

    private Mono<Void> bufferMessage(StreamMessage sm) {
        inFlightCount.incrementAndGet();
        return Mono.fromRunnable(() -> {
            Sinks.EmitResult result = batchSink.tryEmitNext(sm);
            if (result.isFailure()) {
                log.warn("Failed to emit message to batch sink: {}", result);
            }
        }).then();
    }

    private void startFlusher() {
        batchSink.asFlux()
                .bufferTimeout(properties.batchWriter().batchSize(),
                        properties.batchWriter().flushInterval())
                .doOnNext(this::flushBatch)
                .doOnError(e -> log.error("Error in flusher", e))
                .retry()
                .subscribe();
    }

    private void flushBatch(List<StreamMessage> batch) {
        if (batch.isEmpty()) return;

        log.debug("Flushing batch of {} delivery tasks", batch.size());
        List<DeliveryTask> tasks = batch.stream()
                .map(sm -> toDeliveryTask(sm.message))
                .toList();

        // Use DatabaseClient to perform a batch insert with Statement.add()
        r2dbcTemplate.getDatabaseClient()
                .inConnection(connection -> {
                    // Create the SQL statement with positional parameters
                    String sql = "INSERT INTO delivery_tasks " +
                    "(id, event_id, subscription_id, url, secret, payload, status, " +
                    "attempt_count, next_attempt_at, created_at, updated_at) " +
                    "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)";

                    io.r2dbc.spi.Statement statement = connection.createStatement(sql);
                    // Add each task as a set of parameters
                    for (DeliveryTask task : tasks) {
                        statement.bind(0, task.id())
                                .bind(1, task.eventId())
                                .bind(2, task.subscriptionId())
                                .bind(3, task.url())
                                .bind(4, task.secret())
                                .bind(5, task.payload())
                                .bind(6, task.status().name())
                                .bind(7, task.attemptCount())
                                .bind(8, task.nextAttemptAt())
                                .bind(9, task.createdAt())
                                .bind(10, task.updatedAt())
                                .add();
                    }

                    // Execute the batch and return the number of rows affected
                    return Mono.from(statement.execute())
                            .flatMapMany(Result::getRowsUpdated)
                            .reduce(0L, Long::sum);
                })
                .doOnSuccess(totalRows -> {
                    log.debug("Inserted {} delivery tasks (batch)", totalRows);
                    // Ack all messages in the batch
                    Flux.fromIterable(batch)
                            .flatMap(sm -> queueService.ack(sm.stream, BATCH_WRITER_GROUP, sm.message.id()))
                            .doOnComplete(() -> batchCounter.addAndGet(batch.size()))
                            .subscribe();
                })
                .doOnError(e -> {
                    log.error("Failed to insert batch, will retry messages individually", e);
                })
                .subscribe();
    }

    private DeliveryTask toDeliveryTask(QueueMessage msg) {
        Map<String, String> body = msg.body();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new DeliveryTask(
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
        );
    }

    // Pairs a stream name with a queue message for acknowledgment
    private record StreamMessage(String stream, QueueMessage message) {}
    
}
