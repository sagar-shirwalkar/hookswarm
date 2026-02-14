package dev.hookswarm.subscription.model;

import java.time.OffsetDateTime;
import java.util.Set;

public record SubscriptionResponse(
        String id,
        String url,
        Set<String> eventTypes, // The secret is never returned in responses for security
        SubscriptionStatus status,
        int maxRetries,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static SubscriptionResponse from(Subscription sub) {
        return new SubscriptionResponse(
                sub.id(),
                sub.url(),
                sub.eventTypes(),
                sub.status(),
                sub.maxRetries(),
                sub.createdAt(),
                sub.updatedAt()
        );
    }
}