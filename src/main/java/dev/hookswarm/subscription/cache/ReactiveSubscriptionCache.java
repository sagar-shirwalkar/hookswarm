package dev.hookswarm.subscription.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hookswarm.subscription.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

// Subscription Cache (Reactive, Dragonfly) :
// Cache service that stores subscriptions by event type.
// The cache key will be subscriptions:event:{eventType}.
// The value will be a JSON array of subscription objects (or just IDs if we only need URLs/secrets).
// To keep it simple, store the full Subscription object as JSON.
// This allows the fan‑out consumer to get all necessary data without a second DB hit.
@Service
public class ReactiveSubscriptionCache {

    private static final Logger log = LoggerFactory.getLogger(ReactiveSubscriptionCache.class);
    private static final String KEY_PREFIX = "subscriptions:event:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ReactiveSubscriptionCache(ReactiveRedisTemplate<String, String> redisTemplate,
                                     ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<List<Subscription>> get(String eventType) {
        String key = KEY_PREFIX + eventType;
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        List<Subscription> subs = objectMapper.readValue(json, new TypeReference<>() {});
                        return Mono.just(subs);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize subscription cache for eventType: {}", eventType, e);
                        return Mono.empty();
                    }
                });
    }

    public Mono<Void> put(String eventType, List<Subscription> subscriptions) {
        String key = KEY_PREFIX + eventType;
        try {
            String json = objectMapper.writeValueAsString(subscriptions);
            return redisTemplate.opsForValue().set(key, json, TTL).then();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize subscription cache for eventType: {}", eventType, e);
            return Mono.empty();
        }
    }

    public Mono<Void> evict(String eventType) {
        String key = KEY_PREFIX + eventType;
        return redisTemplate.opsForValue().delete(key).then();
    }

    public Mono<Void> evictByPattern(String eventTypePattern) {
        // For simplicity, we can just evict the exact key; if event types change, we need to evict.
        // In subscription update, we know the old and new event types, so we can evict both.
        return evict(eventTypePattern);
    }

}