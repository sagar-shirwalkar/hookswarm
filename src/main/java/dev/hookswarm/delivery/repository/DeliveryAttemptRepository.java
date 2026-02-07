package dev.hookswarm.delivery.repository;

import dev.hookswarm.delivery.model.DeliveryAttempt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryAttemptRepository {

    private final JdbcClient jdbc;

    public DeliveryAttemptRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(DeliveryAttempt attempt) {
        jdbc.sql("""
                INSERT INTO delivery_attempts
                    (id, delivery_task_id, attempt_number, http_status_code,
                     response_body, latency_ms, error_message, attempted_at)
                VALUES
                    (:id, :deliveryTaskId, :attemptNumber, :httpStatusCode,
                     :responseBody, :latencyMs, :errorMessage, :attemptedAt)
                """)
                .param("id", attempt.id())
                .param("deliveryTaskId", attempt.deliveryTaskId())
                .param("attemptNumber", attempt.attemptNumber())
                .param("httpStatusCode", attempt.httpStatusCode())
                .param("responseBody", attempt.responseBody())
                .param("latencyMs", attempt.latency().toMillis())
                .param("errorMessage", attempt.errorMessage())
                .param("attemptedAt", attempt.attemptedAt())
                .update();
    }

}