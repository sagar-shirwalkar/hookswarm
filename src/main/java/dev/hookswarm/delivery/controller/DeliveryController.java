package dev.hookswarm.delivery.controller;


import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.delivery.model.DeadLetterResponse;
import dev.hookswarm.delivery.model.DeliveryAttemptResponse;
import dev.hookswarm.delivery.model.DeliveryTaskResponse;
import dev.hookswarm.delivery.service.DeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    // Delivery Tasks

    @GetMapping("/deliveries/{id}")
    public DeliveryTaskResponse getTask(@PathVariable String id) {
        return DeliveryTaskResponse.from(service.getTask(id));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<?> listTasks(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String subscriptionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (eventId != null) {
            return ResponseEntity.ok(service.getTasksByEventId(eventId));
        }
        if (subscriptionId != null) {
            return ResponseEntity.ok(service.getTasksBySubscriptionId(subscriptionId, page, size));
        }

        return ResponseEntity.badRequest().body(
                dev.hookswarm.common.exception.ApiError.of(400,
                        "Specify either eventId or subscriptionId query parameter"));
    }

    @GetMapping("/deliveries/{id}/attempts")
    public List<DeliveryAttemptResponse> getAttempts(@PathVariable String id) {
        return service.getAttempts(id);
    }

    @PostMapping("/deliveries/{id}/retry")
    public DeliveryTaskResponse retryTask(@PathVariable String id) {
        return service.retryTask(id);
    }

    //Dead Letter Queue

    @GetMapping("/dlq")
    public PagedResponse<DeadLetterResponse> listDeadLetters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listDeadLetters(page, size);
    }

    @PostMapping("/dlq/{id}/replay")
    public DeliveryTaskResponse replayDeadLetter(@PathVariable String id) {
        return service.replayDeadLetter(id);
    }

}