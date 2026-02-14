package dev.hookswarm.subscription.repository;

import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ReactiveSubscriptionRepository extends ReactiveCrudRepository<Subscription, String> {

    Flux<Subscription> findByStatus(SubscriptionStatus status);

    @Query("SELECT * FROM subscriptions WHERE status = 'ACTIVE' AND " +
            "(cardinality(event_types) = 0 OR :eventType = ANY(event_types))")
    Flux<Subscription> findActiveByEventType(@Param("eventType") String eventType);

}