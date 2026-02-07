package dev.hookswarm.delivery.model;

import java.time.Duration;

// Returned by DeliveryWorker to the DeliveryEngine which decides what to do based on success/failure
public record DeliveryResult(
        boolean success,
        int httpStatusCode,
        Duration latency,
        String errorMessage
) {

    public static DeliveryResult success(int statusCode, Duration latency) {
        return new DeliveryResult(true, statusCode, latency, null);
    }

    public static DeliveryResult failure(int statusCode, Duration latency, String error) {
        return new DeliveryResult(false, statusCode, latency, error);
    }

    public static DeliveryResult error(Duration latency, String error) {
        return new DeliveryResult(false, 0, latency, error);
    }

}