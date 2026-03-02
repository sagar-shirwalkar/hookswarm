package dev.hookswarm.event.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.hookswarm.event.dto.CreateEventRequest;
import dev.hookswarm.event.dto.EventResponse;
import dev.hookswarm.event.service.ReactiveEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(ReactiveEventController.class)
class ReactiveEventControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveEventService eventService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateEvent() {
        ObjectNode payload = objectMapper.createObjectNode().put("userId", 1);
        CreateEventRequest request = new CreateEventRequest("user.created", payload, null);
        EventResponse response = new EventResponse("evt123", "user.created", payload.asText(), null, OffsetDateTime.now(ZoneOffset.UTC));

        when(eventService.createEvent(any(CreateEventRequest.class))).thenReturn(Mono.just(response));

        webTestClient.post().uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("evt123")
                .jsonPath("$.eventType").isEqualTo("user.created");
    }

    @Test
    void shouldReturnBadRequestWhenEventTypeMissing() {
        ObjectNode invalidPayload = objectMapper.createObjectNode();
        invalidPayload.set("payload", objectMapper.createObjectNode().put("userId", 1));
        // missing eventType

        webTestClient.post().uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidPayload)
                .exchange()
                .expectStatus().isBadRequest();
    }

}