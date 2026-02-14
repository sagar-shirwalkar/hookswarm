package dev.hookswarm.subscription.controller;

import dev.hookswarm.subscription.model.CreateSubscriptionRequest;
import dev.hookswarm.subscription.model.SubscriptionResponse;
import dev.hookswarm.subscription.model.UpdateSubscriptionRequest;
import dev.hookswarm.subscription.service.ReactiveSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class ReactiveSubscriptionController {

    private final ReactiveSubscriptionService subscriptionService;

    public ReactiveSubscriptionController(ReactiveSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<SubscriptionResponse> createSubscription(@Valid @RequestBody CreateSubscriptionRequest request) {
        return subscriptionService.create(request)
                .map(SubscriptionResponse::from);
    }

    @GetMapping("/{id}")
    public Mono<SubscriptionResponse> getSubscription(@PathVariable String id) {
        return subscriptionService.getById(id)
                .map(SubscriptionResponse::from);
    }

    @GetMapping
    public Flux<SubscriptionResponse> listAllActive() {
        return subscriptionService.getAllActive()
                .map(SubscriptionResponse::from);
    }

    @PutMapping("/{id}")
    public Mono<SubscriptionResponse> updateSubscription(
            @PathVariable String id,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        return subscriptionService.update(id, request)
                .map(SubscriptionResponse::from);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteSubscription(@PathVariable String id) {
        return subscriptionService.delete(id);
    }

    // Optional endpoint to get subscriptions by event type (used by fanout)
    @GetMapping(params = "eventType")
    public Flux<SubscriptionResponse> getActiveByEventType(@RequestParam String eventType) {
        return subscriptionService.getActiveByEventType(eventType)
                .map(SubscriptionResponse::from);
    }

}