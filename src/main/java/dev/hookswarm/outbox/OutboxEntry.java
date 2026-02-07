package dev.hookswarm.outbox;

import java.time.Instant;

public record OutboxEntry(
        String id,
        String eventId,
        String eventType,
        boolean processed,
        Instant createdAt,
        Instant processedAt
) {}