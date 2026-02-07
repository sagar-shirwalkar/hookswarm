package dev.hookswarm.delivery.repository;

import dev.hookswarm.delivery.model.DeadLetterEntry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DeadLetterRepository {

    private final JdbcClient jdbc;

    public DeadLetterRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(DeadLetterEntry entry) {
        jdbc.sql("""
                INSERT INTO dead_letter_queue
                    (id, delivery_task_id, event_id, subscription_id,
                     total_attempts, last_error, dead_at)
                VALUES
                    (:id, :deliveryTaskId, :eventId, :subscriptionId,
                     :totalAttempts, :lastError, :deadAt)
                """)
                .param("id", entry.id())
                .param("deliveryTaskId", entry.deliveryTaskId())
                .param("eventId", entry.eventId())
                .param("subscriptionId", entry.subscriptionId())
                .param("totalAttempts", entry.totalAttempts())
                .param("lastError", entry.lastError())
                .param("deadAt", entry.deadAt())
                .update();
    }

}