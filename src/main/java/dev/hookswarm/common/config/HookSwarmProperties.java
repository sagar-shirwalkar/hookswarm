package dev.hookswarm.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "hookswarm")
public record HookSwarmProperties(
        Fanout fanout,
        Delivery delivery,
        BatchWriter batchWriter,
        Retry retry,
        CircuitBreaker circuitBreaker,
        Recovery recovery
) {
    public record Fanout(
            @DefaultValue("20") int pollBatchSize,
            @DefaultValue("2s") Duration pollBlockTimeout,
            @DefaultValue("10000") int maxPendingDeliveries,
            @DefaultValue("100") int largeSubscriptionThreshold,
            @DefaultValue("deliveries.normal") String normalStream,
            @DefaultValue("deliveries.large") String largeStream,
            @DefaultValue("100ms") Duration backpressureDelay,
            @DefaultValue("4") int concurrency
    ) {}

    public record Delivery(
            @DefaultValue("20") int pollBatchSize,
            @DefaultValue("2s") Duration pollBlockTimeout,
            @DefaultValue("20") int concurrency,
            @DefaultValue("10s") Duration httpTimeout,
            @DefaultValue("5") int perEndpointMaxConcurrency,
            // Deprecate this, replacing with sharding: @DefaultValue({"deliveries.normal", "deliveries.large"}) List<String> streams
            Sharding sharding
    ) {
        public record Sharding(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("8") int numberOfShards,
                @DefaultValue("deliveries.shard") String streamPrefix
        ) {}
    }

    public record BatchWriter(
            @DefaultValue("true") boolean enabled,
            // Deprecate this, replacing with sharding: @DefaultValue({"deliveries.normal"}) List<String> streams,
            @DefaultValue("500") int batchSize,
            @DefaultValue("100ms") Duration flushInterval,
            @DefaultValue("2") int concurrency
    ) {}

    public record Retry(
            @DefaultValue("10") long baseDelaySeconds,
            @DefaultValue("3600") long maxDelaySeconds,
            @DefaultValue("3") double multiplier,
            @DefaultValue("0.2") double jitterFactor
    ) {}

    public record CircuitBreaker(
            @DefaultValue("5") int failureThreshold,
            @DefaultValue("60") long openDurationSeconds
    ) {}

    public record Recovery(
            @DefaultValue("5") int staleThresholdMinutes,
            @DefaultValue("60000") long intervalMs,
            @DefaultValue("200") int batchSize
    ) {}

}