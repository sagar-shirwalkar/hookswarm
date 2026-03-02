package dev.hookswarm.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hookswarm.common.config.HookSwarmProperties;
import dev.hookswarm.common.queue.QueueMessage;
import dev.hookswarm.common.queue.ReactiveQueueService;
import dev.hookswarm.subscription.cache.ReactiveSubscriptionCache;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import dev.hookswarm.subscription.service.ReactiveSubscriptionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactiveEventFanoutConsumerTest {

    @Mock
    private ReactiveQueueService queueService;
    @Mock
    private ReactiveSubscriptionService subscriptionService;
    @Mock
    private ReactiveSubscriptionCache subscriptionCache;
    @Mock
    private HookSwarmProperties properties;
    @Mock
    private HookSwarmProperties.Fanout fanoutProps;
    @Mock
    private HookSwarmProperties.Delivery deliveryProps;
    @Mock
    private HookSwarmProperties.Delivery.Sharding shardingProps;

    private MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private ObjectMapper objectMapper = new ObjectMapper();
    private ReactiveEventFanoutConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(properties.fanout()).thenReturn(fanoutProps);
        lenient().when(fanoutProps.maxPendingDeliveries()).thenReturn(10);
        lenient().when(fanoutProps.backpressureDelay()).thenReturn(Duration.ofMillis(10));
        lenient().when(fanoutProps.normalStream()).thenReturn("deliveries.normal");
        lenient().when(fanoutProps.largeStream()).thenReturn("deliveries.large");
        lenient().when(fanoutProps.largeSubscriptionThreshold()).thenReturn(2);
        lenient().when(fanoutProps.concurrency()).thenReturn(2);
        lenient().when(fanoutProps.pollBatchSize()).thenReturn(5);
        lenient().when(fanoutProps.pollBlockTimeout()).thenReturn(Duration.ofMillis(100));

        lenient().when(properties.delivery()).thenReturn(deliveryProps);
        lenient().when(deliveryProps.sharding()).thenReturn(shardingProps);
        lenient().when(shardingProps.enabled()).thenReturn(true);
        lenient().when(shardingProps.numberOfShards()).thenReturn(2);
        lenient().when(shardingProps.streamPrefix()).thenReturn("deliveries.test");

        // when(queueService.createGroup(anyString(), anyString())).thenReturn(Mono.empty());
        // Stub subscription service to return empty (should not be called if cache works)
        when(subscriptionService.getActiveByEventType(anyString())).thenReturn(Flux.empty());

        consumer = new ReactiveEventFanoutConsumer(
                queueService, subscriptionService, objectMapper, meterRegistry,
                properties, subscriptionCache, "test-consumer", 5, 100, 2);
    }

    @Test
    void shouldFanoutEventToShards() {
        QueueMessage message = new QueueMessage("msg1", Map.of(
                "eventId", "evt1",
                "eventType", "user.created",
                "payload", "{\"userId\":1}",
                "timestamp", "123456"
        ));
        List<Subscription> subscriptions = List.of(
                new Subscription("sub1", "http://url1", "secret1", Set.of("user.created"), SubscriptionStatus.ACTIVE, 3, null, null),
                new Subscription("sub2", "http://url2", "secret2", Set.of("user.created"), SubscriptionStatus.ACTIVE, 3, null, null)
        );

        // when(queueService.read(anyString(), anyString(), eq("events"), anyInt(), any())).thenReturn(Flux.just(message));
        when(queueService.streamLength(anyString())).thenReturn(Mono.just(0L));
        when(subscriptionCache.get("user.created")).thenReturn(Mono.just(subscriptions));
        when(queueService.publish(anyString(), anyMap())).thenReturn(Mono.just("pubMsgId"));
        when(queueService.ack(anyString(), anyString(), eq("msg1"))).thenReturn(Mono.empty());

        StepVerifier.create(consumer.processMessage(message)).verifyComplete();

        verify(queueService, times(1)).ack(anyString(), anyString(), eq("msg1"));
        verify(queueService, times(2)).publish(anyString(), anyMap());
    }
}