package dev.hookswarm.delivery.model;


import java.time.OffsetDateTime;

public record DeadLetterResponse(
        String id,
        String deliveryTaskId,
        String eventId,
        String subscriptionId,
        int totalAttempts,
        String lastError,
        OffsetDateTime deadAt
) {
    public static DeadLetterResponse from(DeadLetterEntry entry) {
        return new DeadLetterResponse(
                entry.id(),
                entry.deliveryTaskId(),
                entry.eventId(),
                entry.subscriptionId(),
                entry.totalAttempts(),
                entry.lastError(),
                entry.deadAt()
        );
    }

}