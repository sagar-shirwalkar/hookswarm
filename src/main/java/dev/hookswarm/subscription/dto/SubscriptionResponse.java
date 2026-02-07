package dev.hookswarm.subscription.dto;

import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;

import java.time.Instant;
import java.util.Set;

public record SubscriptionResponse(
        String id,
        String url,
        String secret,
        Set<String> eventTypes,
        SubscriptionStatus status,
        int maxRetries,
        Instant createdAt,
        Instant updatedAt
) {

    public static SubscriptionResponse from(Subscription sub) {
        return from(sub, false);
    }

    public static SubscriptionResponse from(Subscription sub, boolean revealSecret) {
        String displaySecret = revealSecret
                ? sub.secret()
                : maskSecret(sub.secret());

        return new SubscriptionResponse(
                sub.id(),
                sub.url(),
                displaySecret,
                sub.eventTypes(),
                sub.status(),
                sub.maxRetries(),
                sub.createdAt(),
                sub.updatedAt()
        );
    }

    private static String maskSecret(String secret) {
        if (secret == null || secret.length() < 8) return "****";
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }

}