package dev.hookswarm.delivery.repository;

import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.delivery.model.DeliveryTask;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface ReactiveDeliveryTaskRepository extends ReactiveCrudRepository<DeliveryTask, String> {

    Flux<DeliveryTask> findByEventId(String eventId);

    Flux<DeliveryTask> findBySubscriptionId(String subscriptionId);

    // ===== Status update methods =====
    @Modifying
    @Query("UPDATE delivery_tasks SET status = 'DELIVERED', updated_at = :updatedAt WHERE id = :id")
    Mono<Integer> markDelivered(@Param("id") String id,
                                @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("UPDATE delivery_tasks SET status = 'FAILED', attempt_count = :attemptCount, " +
            "next_attempt_at = :nextAttemptAt, updated_at = :updatedAt WHERE id = :id")
    Mono<Integer> markFailed(@Param("id") String id,
                             @Param("attemptCount") int attemptCount,
                             @Param("nextAttemptAt") OffsetDateTime nextAttemptAt,
                             @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("UPDATE delivery_tasks SET status = 'DEAD', updated_at = :updatedAt WHERE id = :id")
    Mono<Integer> markDead(@Param("id") String id,
                           @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("UPDATE delivery_tasks SET status = 'PENDING', next_attempt_at = :nextAttemptAt, " +
            "updated_at = :updatedAt WHERE id = :id")
    Mono<Integer> resetToPending(@Param("id") String id,
                                 @Param("nextAttemptAt") OffsetDateTime nextAttemptAt,
                                 @Param("updatedAt") OffsetDateTime updatedAt);

    // ===== Optional: Polling for legacy fallback (if you keep the old engine) =====
    @Query("SELECT * FROM delivery_tasks WHERE status IN ('PENDING', 'FAILED') " +
            "AND next_attempt_at <= :now ORDER BY next_attempt_at ASC LIMIT :limit")
    Flux<DeliveryTask> findDueTasks(@Param("now") OffsetDateTime now,
                                    @Param("limit") int limit);

    // ===== Count by status (useful for metrics) =====
    Mono<Long> countByStatus(DeliveryStatus status);

}