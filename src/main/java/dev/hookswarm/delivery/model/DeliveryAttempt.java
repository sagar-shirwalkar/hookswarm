package dev.hookswarm.delivery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.OffsetDateTime;

@Table("delivery_attempts")
public record DeliveryAttempt(
        @Id String id,
        String deliveryTaskId,
        int attemptNumber,
        Integer httpStatusCode,
        String responseBody,
        long latencyMs,
        String errorMessage,
        OffsetDateTime attemptedAt
) {}