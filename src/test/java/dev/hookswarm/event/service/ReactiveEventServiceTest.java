package dev.hookswarm.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hookswarm.common.queue.ReactiveQueueService;
import dev.hookswarm.event.dto.CreateEventRequest;
import dev.hookswarm.event.model.Event;
import dev.hookswarm.event.repository.ReactiveEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactiveEventServiceTest {

    @Mock
    private ReactiveEventRepository eventRepository;

    @Mock
    private ReactiveQueueService queueService;

    @InjectMocks
    private ReactiveEventService eventService;

    @Test
    void shouldCreateEventSuccessfully() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode payload = mapper.readTree("{\"userId\":1}");
        CreateEventRequest request = new CreateEventRequest("user.created", payload, null);
        Event savedEvent = new Event("evt123", "user.created", "{\"userId\":1}", null, OffsetDateTime.now(ZoneOffset.UTC));

        when(eventRepository.save(any(Event.class))).thenReturn(Mono.just(savedEvent));
        when(queueService.publish(eq("events"), any(Map.class))).thenReturn(Mono.just("msgId"));

        StepVerifier.create(eventService.createEvent(request))
                .expectNextMatches(response -> response.id().equals("evt123"))
                .verifyComplete();

        verify(eventRepository, times(1)).save(any(Event.class));
        verify(queueService, times(1)).publish(eq("events"), any(Map.class));
    }

    @Test
    void shouldReturnExistingEventWhenIdempotencyKeyExists() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode payload = mapper.readTree("{\"userId\":1}");
        String idempotencyKey = "idem-001";
        CreateEventRequest request = new CreateEventRequest("user.created", payload, idempotencyKey);
        Event existingEvent = new Event("evt123", "user.created", "{\"userId\":1}", idempotencyKey, OffsetDateTime.now(ZoneOffset.UTC));

        when(eventRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Mono.just(existingEvent));

        StepVerifier.create(eventService.createEvent(request))
                .expectNextMatches(response -> response.id().equals("evt123"))
                .verifyComplete();

        verify(eventRepository, never()).save(any(Event.class));
        verify(queueService, never()).publish(anyString(), any());
    }
}