package dev.hookswarm.delivery.engine;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveRetryPolicyTest {

    private final ReactiveRetryPolicy retryPolicy = new ReactiveRetryPolicy(10, 3600, 3, 0.2);

    @Test
    void shouldCalculateNextAttemptTime() {
        StepVerifier.create(retryPolicy.nextAttemptTime(1))
                .assertNext(time -> {
                    Duration delay = Duration.between(OffsetDateTime.now(), time);
                    assertThat(delay.toMillis()).isBetween(7000L, 36000L);
                })
                .verifyComplete();
    }
}