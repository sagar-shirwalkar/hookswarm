package dev.hookswarm.event.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("events")
public record Event(
        @Id String id,
        String eventType,
        String payload,          // jsonb – stored as text, driver handles conversion
        String idempotencyKey,
        OffsetDateTime createdAt
) {}