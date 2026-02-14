package dev.hookswarm.event.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hookswarm.common.UlidGenerator;
import dev.hookswarm.common.queue.ReactiveQueueService;
import dev.hookswarm.event.dto.CreateEventRequest;
import dev.hookswarm.event.dto.EventResponse;
import dev.hookswarm.event.model.Event;
import dev.hookswarm.event.repository.ReactiveEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class ReactiveEventService {

    private static final Logger log = LoggerFactory.getLogger(ReactiveEventService.class);
    private static final String EVENT_STREAM = "events";

    private final ReactiveEventRepository eventRepository;
    private final ReactiveQueueService queueService;
    private final ObjectMapper objectMapper;

    public ReactiveEventService(ReactiveEventRepository eventRepository,
                                ReactiveQueueService queueService,
                                ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.queueService = queueService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new event, persist it, and publish to the event stream.
     * Idempotency key is used to prevent duplicates.
     */
    public Mono<EventResponse> createEvent(CreateEventRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String eventId = UlidGenerator.newUlid();

        // If idempotency key exists, check for existing event
        Mono<Event> existingCheck = request.idempotencyKey() != null
                ? eventRepository.findByIdempotencyKey(request.idempotencyKey())
                : Mono.empty();

        return existingCheck
                .flatMap(existing -> Mono.just(EventResponse.from(existing)))
                .switchIfEmpty(
                        // No existing event – create new one
                        Mono.defer(() -> {
                            Event event = new Event(
                                    eventId,
                                    request.eventType(),
                                    request.payload().toString(), // store as JSON string
                                    request.idempotencyKey(),
                                    now
                            );

                            return eventRepository.save(event)
                                    .flatMap(savedEvent ->
                                            publishToStream(savedEvent)
                                                    .thenReturn(EventResponse.from(savedEvent))
                                    );
                        })
                )
                .doOnSuccess(response -> log.info("Event created: {}", response.id()))
                .doOnError(e -> log.error("Failed to create event", e));
    }

    /**
     * Publish the event to DragonflyDB stream for fanout processing.
     */
    private Mono<Void> publishToStream(Event event) {
        Map<String, String> message = Map.of(
                "eventId", event.id(),
                "eventType", event.eventType(),
                "payload", event.payload(),
                "timestamp", String.valueOf(event.createdAt().toInstant().toEpochMilli())
        );

        return queueService.publish(EVENT_STREAM, message)
                .doOnNext(msgId -> log.debug("Event {} published to stream as {}", event.id(), msgId))
                .then();
    }

    public Mono<EventResponse> getEvent(String id) {
        return eventRepository.findById(id)
                .map(EventResponse::from);
    }

    public Flux<EventResponse> getEventsByType(String eventType) {
        return eventRepository.findByEventTypeOrderByCreatedAtDesc(eventType)
                .map(EventResponse::from);
    }

    public Flux<EventResponse> listEvents(int page, int size) {
        return eventRepository.findAllWithPagination(size, (long) page * size)
                .map(EventResponse::from);
    }

}