package dev.hookswarm.delivery.repository;

import dev.hookswarm.delivery.model.DeadLetterEntry;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
                .param("deadAt", OffsetDateTime.ofInstant(entry.deadAt(), java.time.ZoneId.of("UTC")))
                .update();
    }

    private static final RowMapper<DeadLetterEntry> ROW_MAPPER = (rs, i) -> new DeadLetterEntry(
            rs.getString("id"),
            rs.getString("delivery_task_id"),
            rs.getString("event_id"),
            rs.getString("subscription_id"),
            rs.getInt("total_attempts"),
            rs.getString("last_error"),
            rs.getObject("dead_at", OffsetDateTime.class).toInstant()
    );

    public Optional<DeadLetterEntry> findById(String id) {
        return jdbc.sql("SELECT * FROM dead_letter_queue WHERE id = :id")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    public List<DeadLetterEntry> findAll(int limit, int offset) {
        return jdbc.sql("""
            SELECT * FROM dead_letter_queue
            ORDER BY dead_at DESC
            LIMIT :limit OFFSET :offset
            """)
                .param("limit", limit)
                .param("offset", offset)
                .query(ROW_MAPPER)
                .list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM dead_letter_queue")
                .query(Long.class)
                .single();
    }

    public boolean deleteById(String id) {
        int rows = jdbc.sql("DELETE FROM dead_letter_queue WHERE id = :id")
                .param("id", id)
                .update();
        return rows > 0;
    }

    public boolean deleteByDeliveryTaskId(String deliveryTaskId) {
        int rows = jdbc.sql("DELETE FROM dead_letter_queue WHERE delivery_task_id = :deliveryTaskId")
                .param("deliveryTaskId", deliveryTaskId)
                .update();
        return rows > 0;
    }

}