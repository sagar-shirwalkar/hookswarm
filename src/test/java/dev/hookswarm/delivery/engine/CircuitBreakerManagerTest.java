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
    void openCircuit_transitionsToHalfOpenAfterDuration() throws InterruptedException {
        CircuitBreakerManager cb = newManager(1, 1); // 1 second open duration

        cb.recordFailure(SUB_ID);
        // Within 1 second, circuit is still OPEN
        assertThat(cb.isOpen(SUB_ID)).isTrue();

        // Wait for open duration to elapse
        Thread.sleep(1100);

        // Now transitions to HALF_OPEN (allows probe -> returns false)
        assertThat(cb.isOpen(SUB_ID)).isFalse();
    }

    @Test
    void halfOpen_closesOnSuccess() throws InterruptedException {
        CircuitBreakerManager cb = newManager(1, 1);

        cb.recordFailure(SUB_ID);
        Thread.sleep(1100); // Transition to HALF_OPEN
        cb.isOpen(SUB_ID);  // Trigger transition

        cb.recordSuccess(SUB_ID);
        assertThat(cb.isOpen(SUB_ID)).isFalse();
    }

    @Test
    void halfOpen_reopensOnFailure() throws InterruptedException {
        CircuitBreakerManager cb = newManager(1, 1);

        cb.recordFailure(SUB_ID);
        assertThat(cb.isOpen(SUB_ID)).isTrue();

        Thread.sleep(1100); // Transition to HALF_OPEN
        assertThat(cb.isOpen(SUB_ID)).isFalse(); // HALF_OPEN allows probe

        cb.recordFailure(SUB_ID); // probe failed → re-open
        assertThat(cb.isOpen(SUB_ID)).isTrue(); // Back to OPEN (1 second timer restarts)
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