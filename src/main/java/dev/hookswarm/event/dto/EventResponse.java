package dev.hookswarm.event.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import dev.hookswarm.event.model.Event;

import java.time.Instant;

public record EventResponse(
        String id,
        String eventType,
        @JsonRawValue String payload,   // inlined as raw JSON, not a quoted string
        String idempotencyKey,
        Instant createdAt
) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.id(),
                event.eventType(),
                event.payload(),
                event.idempotencyKey(),
                event.createdAt()
        );
    }

}