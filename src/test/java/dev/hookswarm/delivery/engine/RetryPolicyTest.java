package dev.hookswarm.delivery.engine;


import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    // No jitter for deterministic tests
    private final RetryPolicy policy = new RetryPolicy(10, 3600, 3, 0.0);

    // With jitter for bounds testing
    private final RetryPolicy jitteredPolicy = new RetryPolicy(10, 3600, 3, 0.2);

    @Test
    void firstRetry_isBaseDelay() {
        Instant before = Instant.now().plusSeconds(9);
        Instant next = policy.nextAttemptTime(1);
        Instant after = Instant.now().plusSeconds(11);

        assertThat(next).isBetween(before, after);
    }

    @Test
    void secondRetry_isBaseTimesMultiplier() {
        Instant before = Instant.now().plusSeconds(29);
        Instant next = policy.nextAttemptTime(2);
        Instant after = Instant.now().plusSeconds(31);

        // 10 * 3^1 = 30 seconds
        assertThat(next).isBetween(before, after);
    }

    @Test
    void thirdRetry_isBaseTimesMultiplierSquared() {
        Instant before = Instant.now().plusSeconds(89);
        Instant next = policy.nextAttemptTime(3);
        Instant after = Instant.now().plusSeconds(91);

        // 10 * 3^2 = 90 seconds
        assertThat(next).isBetween(before, after);
    }

    @Test
    void delayIsCappedAtMax() {
        // Attempt 10: 10 * 3^9 = 196,830s : exceeds 3600s max
        Instant before = Instant.now().plusSeconds(3599);
        Instant next = policy.nextAttemptTime(10);
        Instant after = Instant.now().plusSeconds(3601);

        assertThat(next).isBetween(before, after);
    }

    @Test
    void jitter_staysWithinBounds() {
        // 20% jitter on base delay of 10s -> 8s to 12s
        for (int i = 0; i < 50; i++) {
            Instant next = jitteredPolicy.nextAttemptTime(1);
            Instant lowerBound = Instant.now().plusSeconds(7);  // slight margin
            Instant upperBound = Instant.now().plusSeconds(13);

            assertThat(next).isBetween(lowerBound, upperBound);
        }
    }

    @Test
    void delayIsAlwaysPositive() {
        for (int attempt = 1; attempt <= 20; attempt++) {
            Instant next = jitteredPolicy.nextAttemptTime(attempt);
            assertThat(next).isAfter(Instant.now());
        }
    }

}