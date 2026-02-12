package dev.hookswarm.event.repository;

import dev.hookswarm.event.model.Event;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class EventRepository {

    private final JdbcClient jdbc;

    public EventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Event> ROW_MAPPER = (rs, i) -> new Event(
            rs.getString("id"),
            rs.getString("event_type"),
            rs.getString("payload"),
            rs.getString("idempotency_key"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant()
    );

    public void insert(Event event) {
        jdbc.sql("""
                INSERT INTO events (id, event_type, payload, idempotency_key, created_at)
                VALUES (:id, :eventType, :payload::jsonb, :idempotencyKey, :createdAt)
                """)
                .param("id", event.id())
                .param("eventType", event.eventType())
                .param("payload", event.payload())
                .param("idempotencyKey", event.idempotencyKey())
                .param("createdAt", OffsetDateTime.ofInstant(event.createdAt(), java.time.ZoneId.of("UTC")))
                .update();
    }

    public Optional<Event> findById(String id) {
        return jdbc.sql("SELECT * FROM events WHERE id = :id")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    public Optional<Event> findByIdempotencyKey(String key) {
        return jdbc.sql("SELECT * FROM events WHERE idempotency_key = :key")
                .param("key", key)
                .query(ROW_MAPPER)
                .optional();
    }

    public List<Event> findAll(int limit, int offset) {
        return jdbc.sql("""
                SELECT * FROM events
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
                """)
                .param("limit", limit)
                .param("offset", offset)
                .query(ROW_MAPPER)
                .list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM events")
                .query(Long.class)
                .single();
    }

}