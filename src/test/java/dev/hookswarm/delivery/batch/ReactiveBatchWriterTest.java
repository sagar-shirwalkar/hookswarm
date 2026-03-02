package dev.hookswarm.delivery.batch;

import dev.hookswarm.common.config.HookSwarmProperties;
import dev.hookswarm.common.queue.ReactiveQueueService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactiveBatchWriterTest {

    @Mock
    private ReactiveQueueService queueService;
    @Mock
    private R2dbcEntityTemplate r2dbcTemplate;
    @Mock
    private HookSwarmProperties properties;
    @Mock
    private HookSwarmProperties.BatchWriter batchWriterProps;
    @Mock
    private HookSwarmProperties.Delivery deliveryProps;
    @Mock
    private HookSwarmProperties.Delivery.Sharding shardingProps;

    private MeterRegistry meterRegistry;
    private ReactiveBatchWriter batchWriter;

    @BeforeEach
    void setUp() {

        meterRegistry = new SimpleMeterRegistry();

        // Mock props
        when(properties.batchWriter()).thenReturn(batchWriterProps);
        when(properties.delivery()).thenReturn(deliveryProps);
        when(deliveryProps.sharding()).thenReturn(shardingProps);

        // Mock sharding config
        when(shardingProps.enabled()).thenReturn(true);
        when(shardingProps.numberOfShards()).thenReturn(2);
        when(shardingProps.streamPrefix()).thenReturn("deliveries.test");

        // Mock batch writer config
        when(batchWriterProps.enabled()).thenReturn(true);
        when(batchWriterProps.batchSize()).thenReturn(10);
        when(batchWriterProps.flushInterval()).thenReturn(Duration.ofMillis(50));
        when(batchWriterProps.concurrency()).thenReturn(1);

        // Mock queueService methods to return empty reactive types
        when(queueService.createGroup(anyString(), anyString())).thenReturn(Mono.empty());
        when(queueService.read(anyString(), anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(Flux.empty());  // Return empty Flux instead of null
        //when(queueService.ack(anyString(), anyString(), anyString())).thenReturn(Mono.empty());

        batchWriter = new ReactiveBatchWriter(queueService, r2dbcTemplate, properties, meterRegistry, "test-writer");

    }

    @Test
    void startShouldNotThrow() {
        //when(queueService.createGroup(anyString(), anyString())).thenReturn(Mono.empty());
        // Just verify start() doesn't throw
        batchWriter.start();

        // Give it a moment to process
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // If no exception, test passes
    }
}