package dev.hookswarm.delivery.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;


class ReactiveCircuitBreakerManagerTest {

    private ReactiveCircuitBreakerManager circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new ReactiveCircuitBreakerManager(2, 5); // threshold=2, openDuration=5s
    }

    @Test
    void shouldBeClosedInitially() {
        StepVerifier.create(circuitBreaker.isOpen("sub1"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void shouldOpenAfterTwoFailures() {
        circuitBreaker.recordFailure("sub1").block();
        circuitBreaker.recordFailure("sub1").block();

        StepVerifier.create(circuitBreaker.isOpen("sub1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void shouldCloseAfterSuccess() {
        circuitBreaker.recordFailure("sub1").block();
        circuitBreaker.recordFailure("sub1").block(); // open
        circuitBreaker.recordSuccess("sub1").block();

        StepVerifier.create(circuitBreaker.isOpen("sub1"))
                .expectNext(false)
                .verifyComplete();
    }
}