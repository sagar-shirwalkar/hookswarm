package dev.hookswarm.delivery.model;

import java.time.Instant;

public record DeliveryAttemptResponse(
        String id,
        String deliveryTaskId,
        int attemptNumber,
        int httpStatusCode,
        String responseBody,
        long latencyMs,
        String errorMessage,
        Instant attemptedAt
) {
    public static DeliveryAttemptResponse from(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.id(), attempt.deliveryTaskId(), attempt.attemptNumber(),
                attempt.httpStatusCode(), attempt.responseBody(),
                attempt.latency().toMillis(), attempt.errorMessage(),
                attempt.attemptedAt()
        );
    }
}