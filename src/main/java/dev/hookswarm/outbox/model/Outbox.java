package dev.hookswarm.outbox.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.OffsetDateTime;

@Table("outbox")
public record Outbox(
        @Id String id,
        String eventId,
        String eventType,
        boolean processed,
        OffsetDateTime createdAt,
        OffsetDateTime processedAt
) {}