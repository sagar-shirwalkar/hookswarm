package dev.hookswarm.delivery.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAttemptResponseTest {

    @Test
    void from_mapsAllFields() {
        DeliveryAttempt attempt = new DeliveryAttempt(
                "att_01", "task_01", 2, 503,
                "Service Unavailable", Duration.ofMillis(245),
                "HTTP 503", Instant.now()
        );

        DeliveryAttemptResponse response = DeliveryAttemptResponse.from(attempt);

        assertThat(response.id()).isEqualTo("att_01");
        assertThat(response.deliveryTaskId()).isEqualTo("task_01");
        assertThat(response.attemptNumber()).isEqualTo(2);
        assertThat(response.httpStatusCode()).isEqualTo(503);
        assertThat(response.responseBody()).isEqualTo("Service Unavailable");
        assertThat(response.latencyMs()).isEqualTo(245);
        assertThat(response.errorMessage()).isEqualTo("HTTP 503");
    }

    @Test
    void from_handlesSuccessfulAttempt() {
        DeliveryAttempt attempt = new DeliveryAttempt(
                "att_02", "task_01", 1, 200,
                "{\"ok\":true}", Duration.ofMillis(42),
                null, Instant.now()
        );

        DeliveryAttemptResponse response = DeliveryAttemptResponse.from(attempt);

        assertThat(response.httpStatusCode()).isEqualTo(200);
        assertThat(response.errorMessage()).isNull();
        assertThat(response.latencyMs()).isEqualTo(42);
    }

}