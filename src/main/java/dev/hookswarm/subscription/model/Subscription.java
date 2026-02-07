package dev.hookswarm.subscription.model;

import java.time.Instant;
import java.util.Set;

public record Subscription(
        String id,
        String url,
        String secret,
        Set<String> eventTypes,
        SubscriptionStatus status,
        int maxRetries,
        Instant createdAt,
        Instant updatedAt
) {

    // Empty eventTypes = wildcard (receives all event types).
    public Subscription {
        eventTypes = eventTypes != null ? Set.copyOf(eventTypes) : Set.of();
    }

    public Subscription withUpdate(String url, Set<String> eventTypes,
                                   SubscriptionStatus status, int maxRetries) {
        return new Subscription(
                this.id, url, this.secret, eventTypes, status, maxRetries,
                this.createdAt, Instant.now()
        );
    }

}