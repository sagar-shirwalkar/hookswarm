package dev.hookswarm.delivery.model;

public record DeliveryResult(
        boolean success,
        String responseBody,
        int statusCode,
        long latencyMs,
        String errorMessage   // new field, null for success
) {
    public static DeliveryResult success(String responseBody, int statusCode, long latencyMs) {
        return new DeliveryResult(true, responseBody, statusCode, latencyMs, null);
    }

    public static DeliveryResult failure(String errorMessage, int statusCode, long latencyMs) {
        return new DeliveryResult(false, null, statusCode, latencyMs, errorMessage);
    }
}