package dev.hookswarm.event.model;

import java.time.Instant;

public record Event(
        String id,
        String eventType,
        String payload,         // stored as JSON string, column is JSONB
        String idempotencyKey,  // nullable, client-provided for dedup
        Instant createdAt
) {}