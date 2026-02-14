package dev.hookswarm.subscription.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.OffsetDateTime;
import java.util.Set;

@Table("subscriptions")
public record Subscription(
        @Id String id,
        String url,
        String secret,
        Set<String> eventTypes,
        SubscriptionStatus status,
        int maxRetries,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}