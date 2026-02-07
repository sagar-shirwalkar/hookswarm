package dev.hookswarm.subscription.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.Set;

public record CreateSubscriptionRequest(

        // DTO CreateSubscriptionRequest
        @NotBlank(message = "url is required")
        @URL(message = "must be a valid URL")
        @Size(max = 2048, message = "url must not exceed 2048 characters")
        String url,

        Set<String> eventTypes,

        @Min(value = 1, message = "maxRetries must be at least 1")
        @Max(value = 20, message = "maxRetries must not exceed 20")
        Integer maxRetries
) {

    private static final int DEFAULT_MAX_RETRIES = 5;

    public int maxRetriesOrDefault() {
        return maxRetries != null ? maxRetries : DEFAULT_MAX_RETRIES;
    }

    public Set<String> eventTypesOrDefault() {
        return eventTypes != null ? eventTypes : Set.of();
    }

}