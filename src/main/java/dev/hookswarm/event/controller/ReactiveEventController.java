package dev.hookswarm.event.controller;

import dev.hookswarm.event.dto.CreateEventRequest;
import dev.hookswarm.event.dto.EventResponse;
import dev.hookswarm.event.service.ReactiveEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/events")
public class ReactiveEventController {

    private final ReactiveEventService eventService;

    public ReactiveEventController(ReactiveEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        return eventService.createEvent(request);
    }

    @GetMapping("/{id}")
    public Mono<EventResponse> getEvent(@PathVariable String id) {
        return eventService.getEvent(id);
    }

    @GetMapping
    public Flux<EventResponse> listEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return eventService.listEvents(page, size);
    }

    @GetMapping(params = "eventType")
    public Flux<EventResponse> getEventsByType(@RequestParam String eventType) {
        return eventService.getEventsByType(eventType);
    }

}