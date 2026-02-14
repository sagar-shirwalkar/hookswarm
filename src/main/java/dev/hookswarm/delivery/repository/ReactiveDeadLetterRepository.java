package dev.hookswarm.delivery.repository;

import dev.hookswarm.delivery.model.DeadLetterEntry;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveDeadLetterRepository extends ReactiveCrudRepository<DeadLetterEntry, String> {

    @Query("SELECT * FROM dead_letter_queue ORDER BY dead_at DESC OFFSET :offset LIMIT :limit")
    Flux<DeadLetterEntry> findAllWithPagination(@Param("offset") long offset,
                                                @Param("limit") int limit);

    Mono<Long> count();
}