package dev.hookswarm.delivery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.OffsetDateTime;

@Table("dead_letter_queue")
public record DeadLetterEntry(
        @Id String id,
        String deliveryTaskId,
        String eventId,
        String subscriptionId,
        int totalAttempts,
        String lastError,
        OffsetDateTime deadAt
) {}