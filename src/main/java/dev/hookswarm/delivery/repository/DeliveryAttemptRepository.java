package dev.hookswarm.delivery.repository;

import dev.hookswarm.delivery.model.DeliveryAttempt;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

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

    private static final RowMapper<DeliveryAttempt> ROW_MAPPER = (rs, i) -> new DeliveryAttempt(
            rs.getString("id"),
            rs.getString("delivery_task_id"),
            rs.getInt("attempt_number"),
            rs.getInt("http_status_code"),
            rs.getString("response_body"),
            Duration.ofMillis(rs.getLong("latency_ms")),
            rs.getString("error_message"),
            rs.getObject("attempted_at", OffsetDateTime.class).toInstant()
    );

    public List<DeliveryAttempt> findByDeliveryTaskId(String deliveryTaskId) {
        return jdbc.sql("""
            SELECT * FROM delivery_attempts
            WHERE delivery_task_id = :deliveryTaskId
            ORDER BY attempt_number ASC
            """)
                .param("deliveryTaskId", deliveryTaskId)
                .query(ROW_MAPPER)
                .list();
    }

}