package dev.hookswarm.delivery.model;

import java.time.Duration;
import java.time.Instant;

public record DeliveryAttempt(
        String id,
        String deliveryTaskId,
        int attemptNumber,
        int httpStatusCode,
        String responseBody,
        Duration latency,
        String errorMessage,
        Instant attemptedAt
) {}