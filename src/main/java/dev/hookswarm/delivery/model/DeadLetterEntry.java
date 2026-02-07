package dev.hookswarm.delivery.model;

import java.time.Instant;

public record DeadLetterEntry(
        String id,
        String deliveryTaskId,
        String eventId,
        String subscriptionId,
        int totalAttempts,
        String lastError,
        Instant deadAt
) {}