package dev.hookswarm.delivery.controller;

import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.common.exception.ApiError;
import dev.hookswarm.delivery.model.DeadLetterResponse;
import dev.hookswarm.delivery.model.DeliveryAttemptResponse;
import dev.hookswarm.delivery.model.DeliveryTaskResponse;
import dev.hookswarm.delivery.service.ReactiveDeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
public class ReactiveDeliveryController {

    private final ReactiveDeliveryService deliveryService;

    public ReactiveDeliveryController(ReactiveDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    // ------------------------------------------------------------------------
    // Delivery Tasks
    // ------------------------------------------------------------------------

    @GetMapping("/deliveries/{id}")
    public Mono<DeliveryTaskResponse> getTask(@PathVariable String id) {
        return deliveryService.getTask(id)
                .map(DeliveryTaskResponse::from);
    }

    @GetMapping("/deliveries")
    public Mono<ResponseEntity<?>> listTasks(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String subscriptionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (eventId != null) {
            return deliveryService.getTasksByEventId(eventId)
                    .map(DeliveryTaskResponse::from)
                    .collectList()
                    .map(ResponseEntity::ok);
        }

        if (subscriptionId != null) {
            return deliveryService.getTasksBySubscriptionId(subscriptionId, page, size)
                    .map(paged -> new PagedResponse<>(
                            paged.content().stream()
                                    .map(DeliveryTaskResponse::from)
                                    .toList(),
                            paged.page(),
                            paged.size(),
                            paged.totalElements(),
                            paged.totalPages()
                    ))
                    .map(ResponseEntity::ok);
        }

        return Mono.just(ResponseEntity.badRequest()
                .body(ApiError.of(400, "Specify either eventId or subscriptionId query parameter")));
    }

    @GetMapping("/deliveries/{id}/attempts")
    public Flux<DeliveryAttemptResponse> getAttempts(@PathVariable String id) {
        return deliveryService.getAttempts(id)
                .map(DeliveryAttemptResponse::from);
    }

    @PostMapping("/deliveries/{id}/retry")
    public Mono<DeliveryTaskResponse> retryTask(@PathVariable String id) {
        return deliveryService.retryTask(id)
                .map(DeliveryTaskResponse::from);
    }

    // ------------------------------------------------------------------------
    // Dead Letter Queue
    // ------------------------------------------------------------------------

    @GetMapping("/dlq")
    public Mono<PagedResponse<DeadLetterResponse>> listDeadLetters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return deliveryService.listDeadLetters(page, size)
                .map(paged -> new PagedResponse<>(
                        paged.content().stream()
                                .map(DeadLetterResponse::from)
                                .toList(),
                        paged.page(),
                        paged.size(),
                        paged.totalElements(),
                        paged.totalPages()
                ));
    }

    @PostMapping("/dlq/{id}/replay")
    public Mono<DeliveryTaskResponse> replayDeadLetter(@PathVariable String id) {
        return deliveryService.replayDeadLetter(id)
                .map(DeliveryTaskResponse::from);
    }

}