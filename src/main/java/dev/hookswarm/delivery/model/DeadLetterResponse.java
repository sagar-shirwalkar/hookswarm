package dev.hookswarm.delivery.model;

import java.time.Instant;

public record DeadLetterResponse(
        String id,
        String deliveryTaskId,
        String eventId,
        String subscriptionId,
        int totalAttempts,
        String lastError,
        Instant deadAt
) {
    public static DeadLetterResponse from(DeadLetterEntry entry) {
        return new DeadLetterResponse(
                entry.id(), entry.deliveryTaskId(), entry.eventId(),
                entry.subscriptionId(), entry.totalAttempts(),
                entry.lastError(), entry.deadAt()
        );
    }
}