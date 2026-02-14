package dev.hookswarm.delivery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("delivery_tasks")
public record DeliveryTask(
        @Id String id,
        String eventId,
        String subscriptionId,
        String url,
        String secret,
        String payload,
        DeliveryStatus status,
        int attemptCount,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}