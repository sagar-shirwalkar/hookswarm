package dev.hookswarm.delivery.repository;

import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.delivery.model.DeliveryTask;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
public class DeliveryTaskRepository {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final String INSERT_SQL = """
            INSERT INTO delivery_tasks
                (id, event_id, subscription_id, status, attempt_count,
                 next_attempt_at, created_at, updated_at)
            VALUES
                (:id, :eventId, :subscriptionId, :status, :attemptCount,
                 :nextAttemptAt, :createdAt, :updatedAt)
            """;

    private final NamedParameterJdbcTemplate namedJdbc;
    private final JdbcClient jdbc;

    private static final RowMapper<DeliveryTask> ROW_MAPPER = (rs, i) -> new DeliveryTask(
            rs.getString("id"),
            rs.getString("event_id"),
            rs.getString("subscription_id"),
            DeliveryStatus.valueOf(rs.getString("status")),
            rs.getInt("attempt_count"),
            rs.getObject("next_attempt_at", OffsetDateTime.class).toInstant(),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant()
    );

    public DeliveryTaskRepository(NamedParameterJdbcTemplate namedJdbc, JdbcClient jdbc) {
        this.namedJdbc = namedJdbc;
        this.jdbc = jdbc;
    }

    public void insertBatch(List<DeliveryTask> tasks) {
        if (tasks.isEmpty()) return;

        SqlParameterSource[] batchParams = tasks.stream()
                .map(this::toParams)
                .toArray(SqlParameterSource[]::new);

        namedJdbc.batchUpdate(INSERT_SQL, batchParams);
    }

    // Polling

    @Transactional
    public List<DeliveryTask> findDueAndMarkInFlight(int limit, Instant now) {
        List<DeliveryTask> due = jdbc.sql("""
                SELECT * FROM delivery_tasks
                WHERE status IN ('PENDING', 'FAILED')
                  AND next_attempt_at <= :now
                ORDER BY next_attempt_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
                .param("now", OffsetDateTime.ofInstant(now, UTC))
                .param("limit", limit)
                .query(ROW_MAPPER)
                .list();

        if (due.isEmpty()) return due;

        List<String> ids = due.stream().map(DeliveryTask::id).toList();

        namedJdbc.update("""
                UPDATE delivery_tasks
                SET status = 'IN_FLIGHT', updated_at = :now
                WHERE id IN (:ids)
                """,
                new MapSqlParameterSource()
                        .addValue("ids", ids)
                        .addValue("now", OffsetDateTime.ofInstant(now, UTC)));

        return due.stream()
                .map(t -> new DeliveryTask(
                        t.id(), t.eventId(), t.subscriptionId(),
                        DeliveryStatus.IN_FLIGHT, t.attemptCount(),
                        t.nextAttemptAt(), t.createdAt(), now))
                .toList();
    }

    // Status Transitions

    public void markDelivered(String id, Instant now) {
        jdbc.sql("""
                UPDATE delivery_tasks
                SET status = 'DELIVERED', updated_at = :now
                WHERE id = :id
                """)
                .param("id", id)
                .param("now", OffsetDateTime.ofInstant(now, UTC))
                .update();
    }

    public void markFailed(String id, int attemptCount, Instant nextAttemptAt, Instant now) {
        jdbc.sql("""
                UPDATE delivery_tasks
                SET status = 'FAILED', attempt_count = :attemptCount,
                    next_attempt_at = :nextAttemptAt, updated_at = :now
                WHERE id = :id
                """)
                .param("id", id)
                .param("attemptCount", attemptCount)
                .param("nextAttemptAt", OffsetDateTime.ofInstant(nextAttemptAt, UTC)) // Fixed
                .param("now", OffsetDateTime.ofInstant(now, UTC))
                .update();
    }

    public void markDead(String id, int attemptCount, Instant now) {
        jdbc.sql("""
                UPDATE delivery_tasks
                SET status = 'DEAD', attempt_count = :attemptCount, updated_at = :now
                WHERE id = :id
                """)
                .param("id", id)
                .param("attemptCount", attemptCount)
                .param("now", OffsetDateTime.ofInstant(now, UTC))
                .update();
    }

    public Optional<DeliveryTask> findById(String id) {
        return jdbc.sql("SELECT * FROM delivery_tasks WHERE id = :id")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    public void resetToPending(String id, Instant nextAttemptAt, Instant now) {
        jdbc.sql("""
            UPDATE delivery_tasks
            SET status = 'PENDING', next_attempt_at = :nextAttemptAt, updated_at = :now
            WHERE id = :id
            """)
                .param("id", id)
                .param("nextAttemptAt", OffsetDateTime.ofInstant(nextAttemptAt, UTC)) // Fixed
                .param("now", OffsetDateTime.ofInstant(now, UTC))
                .update();
    }

    public List<DeliveryTask> findByEventId(String eventId) {
        return jdbc.sql("""
            SELECT * FROM delivery_tasks
            WHERE event_id = :eventId
            ORDER BY created_at DESC
            """)
                .param("eventId", eventId)
                .query(ROW_MAPPER)
                .list();
    }

    public List<DeliveryTask> findBySubscriptionId(String subscriptionId, int limit, int offset) {
        return jdbc.sql("""
            SELECT * FROM delivery_tasks
            WHERE subscription_id = :subscriptionId
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """)
                .param("subscriptionId", subscriptionId)
                .param("limit", limit)
                .param("offset", offset)
                .query(ROW_MAPPER)
                .list();
    }

    public long countBySubscriptionId(String subscriptionId) {
        return jdbc.sql("""
            SELECT COUNT(*) FROM delivery_tasks
            WHERE subscription_id = :subscriptionId
            """)
                .param("subscriptionId", subscriptionId)
                .query(Long.class)
                .single();
    }

    // Stale recovery

    public List<DeliveryTask> findStaleInFlight(Instant threshold, int limit) {
        return jdbc.sql("""
            SELECT * FROM delivery_tasks
            WHERE status = 'IN_FLIGHT'
              AND updated_at < :threshold
            ORDER BY updated_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
                .param("threshold", OffsetDateTime.ofInstant(threshold, UTC))
                .param("limit", limit)
                .query(ROW_MAPPER)
                .list();
    }

    // Full reset, used by DLQ replay

    public void resetForReplay(String id, Instant now) {
        jdbc.sql("""
            UPDATE delivery_tasks
            SET status = 'PENDING', attempt_count = 0,
                next_attempt_at = :now, updated_at = :now
            WHERE id = :id
            """)
                .param("id", id)
                .param("now", OffsetDateTime.ofInstant(now, UTC))
                .update();
    }

    // Helpers

    private MapSqlParameterSource toParams(DeliveryTask task) {
        return new MapSqlParameterSource()
                .addValue("id", task.id())
                .addValue("eventId", task.eventId())
                .addValue("subscriptionId", task.subscriptionId())
                .addValue("status", task.status().name())
                .addValue("attemptCount", task.attemptCount())
                // FIX: These must also be OffsetDateTime for the batch insert to work
                .addValue("nextAttemptAt", OffsetDateTime.ofInstant(task.nextAttemptAt(), UTC))
                .addValue("createdAt", OffsetDateTime.ofInstant(task.createdAt(), UTC))
                .addValue("updatedAt", OffsetDateTime.ofInstant(task.updatedAt(), UTC));
    }

}