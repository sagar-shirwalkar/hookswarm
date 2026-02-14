package dev.hookswarm.delivery.model;


import java.time.OffsetDateTime;

public record DeliveryAttemptResponse(
        String id,
        String deliveryTaskId,
        int attemptNumber,
        Integer httpStatusCode,
        String responseBody,
        long latencyMs,
        OffsetDateTime attemptedAt
) {
    public static DeliveryAttemptResponse from(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.id(),
                attempt.deliveryTaskId(),
                attempt.attemptNumber(),
                attempt.httpStatusCode(),
                attempt.responseBody(),
                attempt.latencyMs(),
                attempt.attemptedAt()
        );
    }
}