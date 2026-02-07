package dev.hookswarm.delivery.model;

import java.time.Instant;

public record DeliveryTask(
        String id,
        String eventId,
        String subscriptionId,
        DeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant updatedAt
) {}