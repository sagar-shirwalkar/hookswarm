package dev.hookswarm.delivery.model;

import java.time.Instant;

public record DeliveryTaskResponse(
        String id,
        String eventId,
        String subscriptionId,
        DeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeliveryTaskResponse from(DeliveryTask task) {
        return new DeliveryTaskResponse(
                task.id(), task.eventId(), task.subscriptionId(),
                task.status(), task.attemptCount(), task.nextAttemptAt(),
                task.createdAt(), task.updatedAt()
        );
    }
}