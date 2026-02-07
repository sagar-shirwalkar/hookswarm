package dev.hookswarm.delivery.engine;


import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerManagerTest {

    private static final String SUB_ID = "sub_01";

    @Test
    void initialState_isClosed() {
        CircuitBreakerManager cb = newManager(3, 60);

        assertThat(cb.isOpen(SUB_ID)).isFalse();
    }

    @Test
    void staysClosedBelowThreshold() {
        CircuitBreakerManager cb = newManager(3, 60);

        cb.recordFailure(SUB_ID);
        cb.recordFailure(SUB_ID);

        assertThat(cb.isOpen(SUB_ID)).isFalse();
    }

    @Test
    void opensAtThreshold() {
        CircuitBreakerManager cb = newManager(3, 60);

        cb.recordFailure(SUB_ID);
        cb.recordFailure(SUB_ID);
        cb.recordFailure(SUB_ID);

        assertThat(cb.isOpen(SUB_ID)).isTrue();
    }

    @Test
    void successResetsFailureCount() {
        CircuitBreakerManager cb = newManager(3, 60);

        cb.recordFailure(SUB_ID);
        cb.recordFailure(SUB_ID);
        cb.recordSuccess(SUB_ID);  // resets
        cb.recordFailure(SUB_ID);

        assertThat(cb.isOpen(SUB_ID)).isFalse();
    }

    @Test
    void openCircuit_transitionsToHalfOpenAfterDuration() {
        // 0 seconds open duration → immediately transitions
        CircuitBreakerManager cb = newManager(1, 0);

        cb.recordFailure(SUB_ID);
        assertThat(cb.isOpen(SUB_ID)).isTrue();

        // Next check: open duration (0s) has elapsed → half-open → allows probe
        assertThat(cb.isOpen(SUB_ID)).isFalse();
    }

    @Test
    void halfOpen_closesOnSuccess() {
        CircuitBreakerManager cb = newManager(1, 0);

        cb.recordFailure(SUB_ID);
        // Trigger transition to HALF_OPEN
        cb.isOpen(SUB_ID);

        cb.recordSuccess(SUB_ID);
        assertThat(cb.isOpen(SUB_ID)).isFalse();
    }

    @Test
    void halfOpen_reopensOnFailure() {
        CircuitBreakerManager cb = newManager(1, 0);

        cb.recordFailure(SUB_ID);
        // Trigger transition to HALF_OPEN
        cb.isOpen(SUB_ID);

        cb.recordFailure(SUB_ID);  // probe failed → re-open
        assertThat(cb.isOpen(SUB_ID)).isTrue();
    }

    @Test
    void independentPerSubscription() {
        CircuitBreakerManager cb = newManager(2, 60);

        cb.recordFailure("sub_A");
        cb.recordFailure("sub_A");

        assertThat(cb.isOpen("sub_A")).isTrue();
        assertThat(cb.isOpen("sub_B")).isFalse();
    }

    private CircuitBreakerManager newManager(int threshold, long openSeconds) {
        return new CircuitBreakerManager(threshold, openSeconds);
    }

}