package dev.hookswarm.common.queue;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DragonflyReactiveQueueServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveStreamOperations<String, Object, Object> streamOps;  // ✅ Changed to Object, Object

    private DragonflyReactiveQueueService queueService;

    @BeforeEach
    void setUp() {
        // ✅ Now types match
        when(redisTemplate.opsForStream()).thenReturn(streamOps);

        // ✅ Use SimpleMeterRegistry for cleaner tests
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        queueService = new DragonflyReactiveQueueService(redisTemplate, meterRegistry);
    }

    @Test
    void shouldPublishMessage() {
        Map<String, String> body = Map.of("key", "value");
        RecordId recordId = RecordId.of("123-0");

        // ✅ Mock the add method
        when(streamOps.add(any())).thenReturn(Mono.just(recordId));

        StepVerifier.create(queueService.publish("teststream", body))
                .expectNext("123-0")
                .verifyComplete();
    }
}
