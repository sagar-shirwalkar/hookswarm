package dev.hookswarm.delivery.model;


import java.time.Instant;
import java.time.OffsetDateTime;

public record DeliveryTaskResponse(
        String id,
        String eventId,
        String subscriptionId,
        String url,               // maybe you don't want to expose full URL? up to you
        String status,
        int attemptCount,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static DeliveryTaskResponse from(DeliveryTask task) {
        return new DeliveryTaskResponse(
                task.id(),
                task.eventId(),
                task.subscriptionId(),
                task.url(),
                task.status().name(),
                task.attemptCount(),
                task.nextAttemptAt(),
                task.createdAt(),
                task.updatedAt()
        );
    }
}