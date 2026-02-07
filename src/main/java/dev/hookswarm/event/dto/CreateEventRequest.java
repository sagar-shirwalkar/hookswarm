package dev.hookswarm.event.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(

        @NotBlank(message = "eventType is required")
        @Size(max = 255, message = "eventType must not exceed 255 characters")
        String eventType,

        @NotNull(message = "payload is required")
        JsonNode payload,

        @Size(max = 255, message = "idempotencyKey must not exceed 255 characters")
        String idempotencyKey   // optional, null means no dedup
) {}