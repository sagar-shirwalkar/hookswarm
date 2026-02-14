package dev.hookswarm.subscription.service;

import dev.hookswarm.common.UlidGenerator;
import dev.hookswarm.subscription.model.CreateSubscriptionRequest;
import dev.hookswarm.subscription.model.UpdateSubscriptionRequest;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import dev.hookswarm.subscription.repository.ReactiveSubscriptionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Service
public class ReactiveSubscriptionService {

    private final ReactiveSubscriptionRepository repository;

    public ReactiveSubscriptionService(ReactiveSubscriptionRepository repository) {
        this.repository = repository;
    }

    public Mono<Subscription> create(CreateSubscriptionRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Subscription sub = new Subscription(
                UlidGenerator.newUlid(),
                request.url(),
                request.secret(),
                request.eventTypes() != null ? request.eventTypes() : Set.of(), // empty = wildcard
                SubscriptionStatus.ACTIVE,
                request.maxRetries() != null ? request.maxRetries() : 5,
                now,
                now
        );
        return repository.save(sub);
    }

    public Mono<Subscription> getById(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Subscription not found: " + id)));
    }

    public Flux<Subscription> getAllActive() {
        return repository.findByStatus(SubscriptionStatus.ACTIVE);
    }

    public Flux<Subscription> getActiveByEventType(String eventType) {
        return repository.findActiveByEventType(eventType);
    }

    public Mono<Subscription> update(String id, UpdateSubscriptionRequest request) {
        return getById(id)
                .flatMap(existing -> {
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    Subscription updated = new Subscription(
                            existing.id(),
                            request.url() != null ? request.url() : existing.url(),
                            request.secret() != null ? request.secret() : existing.secret(),
                            request.eventTypes() != null ? request.eventTypes() : existing.eventTypes(),
                            request.status() != null ? request.status() : existing.status(),
                            request.maxRetries() != null ? request.maxRetries() : existing.maxRetries(),
                            existing.createdAt(),
                            now
                    );
                    return repository.save(updated);
                });
    }

    public Mono<Void> delete(String id) {
        return repository.deleteById(id);
    }

}