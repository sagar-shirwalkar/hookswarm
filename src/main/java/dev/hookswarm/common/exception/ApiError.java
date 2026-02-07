package dev.hookswarm.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String message,
        Instant timestamp,
        Map<String, String> fieldErrors
) {

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, Instant.now(), null);
    }

    public static ApiError withFieldErrors(int status, String message, Map<String, String> fieldErrors) {
        return new ApiError(status, message, Instant.now(), fieldErrors);
    }

}