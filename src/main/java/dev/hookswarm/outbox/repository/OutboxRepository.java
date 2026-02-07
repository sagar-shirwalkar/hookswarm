package dev.hookswarm.outbox.repository;

import dev.hookswarm.outbox.OutboxEntry;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class OutboxRepository {

    private final JdbcClient jdbc;

    public OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<OutboxEntry> ROW_MAPPER = (rs, i) -> new OutboxEntry(
            rs.getString("id"),
            rs.getString("event_id"),
            rs.getString("event_type"),      // ← add this
            rs.getBoolean("processed"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("processed_at", OffsetDateTime.class) != null
                    ? rs.getObject("processed_at", OffsetDateTime.class).toInstant()
                    : null
    );

    public void insert(OutboxEntry entry) {
        jdbc.sql("""
            INSERT INTO outbox (id, event_id, event_type, processed, created_at)
            VALUES (:id, :eventId, :eventType, :processed, :createdAt)
            """)
                .param("id", entry.id())
                .param("eventId", entry.eventId())
                .param("eventType", entry.eventType())  // ← add this
                .param("processed", entry.processed())
                .param("createdAt", entry.createdAt())
                .update();
    }

    /**
     * Fetches unprocessed entries with row-level locking.
     * FOR UPDATE (locks rows so no other poller instance grabs them)
     * SKIP LOCKED (if another instance already locked a row, skip it)
     * -- This makes the outbox safe for multiple competing poller instances
     */
    public List<OutboxEntry> findUnprocessedForUpdate(int limit) {
        return jdbc.sql("""
                SELECT * FROM outbox
                WHERE processed = FALSE
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
                .param("limit", limit)
                .query(ROW_MAPPER)
                .list();
    }

    public void markProcessed(List<String> ids, Instant processedAt) {
        if (ids.isEmpty()) return;

        jdbc.sql("""
                UPDATE outbox
                SET processed = TRUE, processed_at = :processedAt
                WHERE id IN (:ids)
                """)
                .param("ids", ids)
                .param("processedAt", processedAt)
                .update();
    }

}