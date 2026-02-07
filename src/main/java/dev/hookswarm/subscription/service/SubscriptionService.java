package dev.hookswarm.subscription.service;

import dev.hookswarm.common.IdGenerator;
import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.common.exception.ResourceNotFoundException;
import dev.hookswarm.subscription.dto.CreateSubscriptionRequest;
import dev.hookswarm.subscription.dto.SubscriptionResponse;
import dev.hookswarm.subscription.dto.UpdateSubscriptionRequest;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import dev.hookswarm.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository repository;

    public SubscriptionService(SubscriptionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Subscription create(CreateSubscriptionRequest request) {
        Instant now = Instant.now();

        Subscription subscription = new Subscription(
                IdGenerator.newId(),
                request.url(),
                IdGenerator.newSecret(),
                request.eventTypesOrDefault(),
                SubscriptionStatus.ACTIVE,
                request.maxRetriesOrDefault(),
                now,
                now
        );

        repository.insert(subscription);
        log.info("Created subscription {} -> {} (types: {})",
                subscription.id(), subscription.url(), subscription.eventTypes());
        return subscription;
    }

    @Transactional
    public Subscription update(String id, UpdateSubscriptionRequest request) {
        if (!request.hasChanges()) {
            throw new IllegalArgumentException("No fields to update");
        }

        Subscription current = getById(id);

        Subscription updated = current.withUpdate(
                request.url() != null ? request.url() : current.url(),
                request.eventTypes() != null ? request.eventTypes() : current.eventTypes(),
                request.status() != null ? request.status() : current.status(),
                request.maxRetries() != null ? request.maxRetries() : current.maxRetries()
        );

        repository.update(updated);
        log.info("Updated subscription {}", id);
        return updated;
    }

    @Transactional(readOnly = true)
    public Subscription getById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<SubscriptionResponse> list(int page, int size) {
        int offset = page * size;
        var subscriptions = repository.findAll(size, offset)
                .stream()
                .map(SubscriptionResponse::from)
                .toList();

        long total = repository.count();
        return PagedResponse.of(subscriptions, page, size, total);
    }

    @Transactional
    public void delete(String id) {
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException("Subscription", id);
        }
        log.info("Deleted subscription {}", id);
    }

}