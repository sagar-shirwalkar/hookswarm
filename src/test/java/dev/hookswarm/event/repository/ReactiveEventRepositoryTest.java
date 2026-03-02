package dev.hookswarm.event.repository;

import dev.hookswarm.event.model.Event;
import dev.hookswarm.BaseRepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

class ReactiveEventRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private ReactiveEventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll().block();
    }

    @Test
    void shouldSaveAndFindEvent() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Event event = new Event("evt123", "user.created", "{\"userId\":1}", null, now);

        StepVerifier.create(eventRepository.insert(event))
                .expectNextMatches(e -> e.id().equals("evt123"))
                .verifyComplete();

        StepVerifier.create(eventRepository.findById("evt123"))
                .expectNextMatches(e -> e.eventType().equals("user.created"))
                .verifyComplete();
    }
}
