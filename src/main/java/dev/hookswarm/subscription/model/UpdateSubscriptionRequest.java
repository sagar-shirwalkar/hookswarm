package dev.hookswarm.subscription.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.Set;

public record UpdateSubscriptionRequest(

        @URL(message = "must be a valid URL")
        @Size(max = 2048, message = "url must not exceed 2048 characters")
        String url,

        String secret,

        Set<String> eventTypes,

        SubscriptionStatus status,

        @Min(value = 1, message = "maxRetries must be at least 1")
        @Max(value = 20, message = "maxRetries must not exceed 20")
        Integer maxRetries
) {}