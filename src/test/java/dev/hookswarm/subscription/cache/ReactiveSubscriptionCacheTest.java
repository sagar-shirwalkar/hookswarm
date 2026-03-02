package dev.hookswarm.subscription.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactiveSubscriptionCacheTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ReactiveSubscriptionCache cache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        cache = new ReactiveSubscriptionCache(redisTemplate, objectMapper);
    }

    @Test
    void shouldPutAndGet() {
        Subscription sub = new Subscription("sub1", "url", "secret", Set.of("user.created"), SubscriptionStatus.ACTIVE, 3, null, null);
        List<Subscription> subs = List.of(sub);
        String key = "subscriptions:event:user.created";
        String expectedJson = "[{\"id\":\"sub1\",\"url\":\"url\",\"secret\":\"secret\",\"eventTypes\":[\"user.created\"],\"status\":\"ACTIVE\",\"maxRetries\":3,\"createdAt\":null,\"updatedAt\":null}]";

        when(valueOps.set(eq(key), eq(expectedJson), any(Duration.class))).thenReturn(Mono.just(true));
        when(valueOps.get(key)).thenReturn(Mono.just(expectedJson));

        StepVerifier.create(cache.put("user.created", subs).then(cache.get("user.created")))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).id()).isEqualTo("sub1");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenCacheMiss() {
        String key = "subscriptions:event:unknown";
        when(valueOps.get(key)).thenReturn(Mono.empty());

        StepVerifier.create(cache.get("unknown"))
                .verifyComplete(); // expects no items
    }

}