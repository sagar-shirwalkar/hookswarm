package dev.hookswarm.subscription.controller;

import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.subscription.dto.CreateSubscriptionRequest;
import dev.hookswarm.subscription.dto.SubscriptionResponse;
import dev.hookswarm.subscription.dto.UpdateSubscriptionRequest;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(
            @Valid @RequestBody CreateSubscriptionRequest request) {

        Subscription created = service.create(request);

        // Reveal full secret only on creation
        SubscriptionResponse response = SubscriptionResponse.from(created, true);

        return ResponseEntity
                .created(URI.create("/api/v1/subscriptions/" + created.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public SubscriptionResponse getById(@PathVariable String id) {
        return SubscriptionResponse.from(service.getById(id));
    }

    @GetMapping
    public PagedResponse<SubscriptionResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @PatchMapping("/{id}")
    public SubscriptionResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        return SubscriptionResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

}