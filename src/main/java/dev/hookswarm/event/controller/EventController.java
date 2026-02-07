package dev.hookswarm.event.controller;

import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.event.dto.CreateEventRequest;
import dev.hookswarm.event.model.EventIngestResult;
import dev.hookswarm.event.dto.EventResponse;
import dev.hookswarm.event.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EventResponse> ingest(
            @Valid @RequestBody CreateEventRequest request) {

        EventIngestResult result = service.ingest(request);
        EventResponse response = EventResponse.from(result.event());

        if (result.created()) {
            return ResponseEntity
                    .created(URI.create("/api/v1/events/" + result.event().id()))
                    .body(response);
        }

        // Idempotent duplicate, return 200 not 201
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable String id) {
        return EventResponse.from(service.getById(id));
    }

    @GetMapping
    public PagedResponse<EventResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

}