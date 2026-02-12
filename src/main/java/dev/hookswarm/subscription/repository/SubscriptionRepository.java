package dev.hookswarm.subscription.repository;

import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class SubscriptionRepository {

    private final JdbcClient jdbc;

    public SubscriptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Subscription> ROW_MAPPER = (rs, i) -> new Subscription(
            rs.getString("id"),
            rs.getString("url"),
            rs.getString("secret"),
            pgArrayToSet(rs.getArray("event_types")),
            SubscriptionStatus.valueOf(rs.getString("status")),
            rs.getInt("max_retries"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant()
    );

    public void insert(Subscription subscription) {
        jdbc.sql("""
                INSERT INTO subscriptions (id, url, secret, event_types, status, max_retries, created_at, updated_at)
                VALUES (:id, :url, :secret, CAST(:eventTypes AS text[]), :status, :maxRetries, :createdAt, :updatedAt)
                """)
                .param("id", subscription.id())
                .param("url", subscription.url())
                .param("secret", subscription.secret())
                .param("eventTypes", setToPgLiteral(subscription.eventTypes()))
                .param("status", subscription.status().name())
                .param("maxRetries", subscription.maxRetries())
                .param("createdAt", java.time.OffsetDateTime.ofInstant(subscription.createdAt(), java.time.ZoneId.of("UTC")))
                .param("updatedAt", java.time.OffsetDateTime.ofInstant(subscription.updatedAt(), java.time.ZoneId.of("UTC")))
                .update();
    }

    public Optional<Subscription> findById(String id) {
        return jdbc.sql("SELECT * FROM subscriptions WHERE id = :id")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    public List<Subscription> findAll(int limit, int offset) {
        return jdbc.sql("""
                SELECT * FROM subscriptions
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
                """)
                .param("limit", limit)
                .param("offset", offset)
                .query(ROW_MAPPER)
                .list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM subscriptions")
                .query(Long.class)
                .single();
    }

    public void update(Subscription subscription) {
        int rows = jdbc.sql("""
                UPDATE subscriptions
                SET url = :url, event_types = CAST(:eventTypes AS text[]),
                    status = :status, max_retries = :maxRetries, updated_at = :updatedAt
                WHERE id = :id
                """)
                .param("id", subscription.id())
                .param("url", subscription.url())
                .param("eventTypes", setToPgLiteral(subscription.eventTypes()))
                .param("status", subscription.status().name())
                .param("maxRetries", subscription.maxRetries())
                .param("updatedAt", java.time.OffsetDateTime.ofInstant(subscription.updatedAt(), java.time.ZoneId.of("UTC")))
                .update();

        if (rows == 0) {
            throw new IllegalStateException("Subscription update affected 0 rows: " + subscription.id());
        }
    }

    public boolean deleteById(String id) {
        int rows = jdbc.sql("DELETE FROM subscriptions WHERE id = :id")
                .param("id", id)
                .update();
        return rows > 0;
    }

    public List<Subscription> findAllActive() {
        return jdbc.sql("SELECT * FROM subscriptions WHERE status = 'ACTIVE'")
                .query(ROW_MAPPER)
                .list();
    }

    /**
     * Returns ACTIVE subscriptions that either:
     *   - have an empty event_types array (wildcard : receive everything), OR
     *   - explicitly include the given eventType
     */
    public List<Subscription> findActiveByEventType(String eventType) {
        return jdbc.sql("""
                SELECT * FROM subscriptions
                WHERE status = 'ACTIVE'
                  AND (cardinality(event_types) = 0 OR :eventType = ANY(event_types))
                """)
                .param("eventType", eventType)
                .query(ROW_MAPPER)
                .list();
    }

    //
    // Postgres array helpers
    //

    // Postgres TEXT[] to Java Set
    private static Set<String> pgArrayToSet(Array pgArray) {
        if (pgArray == null) return Set.of();
        try {
            String[] values = (String[]) pgArray.getArray();
            return values.length > 0
                    ? Arrays.stream(values).collect(Collectors.toUnmodifiableSet())
                    : Set.of();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read PostgreSQL array", e);
        }
    }

    // Java Set to Postgres array literal string, use with CAST(:param AS text[]) in queries
    private static String setToPgLiteral(Set<String> values) {
        if (values == null || values.isEmpty()) return "{}";
        return values.stream()
                .map(v -> "\"" + v + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

}