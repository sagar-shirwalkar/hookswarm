package dev.hookswarm.outbox.repository;

import dev.hookswarm.outbox.model.Outbox;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface ReactiveOutboxRepository extends ReactiveCrudRepository<Outbox, String> {

    @Query("SELECT * FROM outbox WHERE processed = false ORDER BY created_at ASC LIMIT :limit")
    Flux<Outbox> findUnprocessed(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE outbox SET processed = true, processed_at = :processedAt WHERE id = :id")
    Mono<Integer> markProcessed(@Param("id") String id,
                                @Param("processedAt") OffsetDateTime processedAt);
}