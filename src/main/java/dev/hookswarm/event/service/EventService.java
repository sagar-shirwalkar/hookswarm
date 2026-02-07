package dev.hookswarm.event.service;

import dev.hookswarm.common.IdGenerator;
import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.common.exception.ResourceNotFoundException;
import dev.hookswarm.event.dto.CreateEventRequest;
import dev.hookswarm.event.model.Event;
import dev.hookswarm.event.model.EventIngestResult;
import dev.hookswarm.event.dto.EventResponse;
import dev.hookswarm.event.repository.EventRepository;
import dev.hookswarm.outbox.OutboxEntry;
import dev.hookswarm.outbox.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final OutboxRepository outboxRepository;

    public EventService(EventRepository eventRepository, OutboxRepository outboxRepository) {
        this.eventRepository = eventRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Ingest an event. If an idempotency key is provided and already exists,
     * returns the existing event (created=false). Otherwise, inserts the event
     * AND an outbox entry in the same transaction.
     */
    @Transactional
    public EventIngestResult ingest(CreateEventRequest request) {

        // Idempotency check
        if (request.idempotencyKey() != null) {
            var existing = eventRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.debug("Idempotent hit for key={}", request.idempotencyKey());
                return new EventIngestResult(existing.get(), false);
            }
        }

        Instant now = Instant.now();

        Event event = new Event(
                IdGenerator.newId(),
                request.eventType(),
                request.payload().toString(),   // JsonNode → JSON string
                request.idempotencyKey(),
                now
        );

        // Both writes in the same transaction, outbox pattern
        eventRepository.insert(event);
        outboxRepository.insert(
                new OutboxEntry(
                        IdGenerator.newId(),
                        event.id(),
                        event.eventType(),
                        false,
                        now,
                        null
                )
        );

        log.info("Ingested event id={} type={}", event.id(), event.eventType());
        return new EventIngestResult(event, true);
    }

    @Transactional(readOnly = true)
    public Event getById(String id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> list(int page, int size) {
        int offset = page * size;
        var events = eventRepository.findAll(size, offset)
                .stream()
                .map(EventResponse::from)
                .toList();
        long total = eventRepository.count();
        return PagedResponse.of(events, page, size, total);
    }

}