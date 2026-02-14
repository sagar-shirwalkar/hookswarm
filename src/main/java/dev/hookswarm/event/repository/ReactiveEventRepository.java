package dev.hookswarm.event.repository;

import dev.hookswarm.event.model.Event;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveEventRepository extends ReactiveCrudRepository<Event, String> {

    Mono<Event> findByIdempotencyKey(String idempotencyKey);

    Flux<Event> findByEventTypeOrderByCreatedAtDesc(String eventType);

    // Optional: paginated queries for the dashboard
    @Query("SELECT * FROM events ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Event> findAllWithPagination(@Param("limit") int limit, @Param("offset") long offset);

}