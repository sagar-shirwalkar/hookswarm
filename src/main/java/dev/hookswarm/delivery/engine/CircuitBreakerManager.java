package dev.hookswarm.delivery.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

// In-mem circuit breaker (per-subscription)
// OPEN on "failureThreshold" consec. failures
// HALF_OPEN once "openDuration" completes, allows one probe delivery, if successful moves to CLOSED else OPEN
@Component
public class CircuitBreakerManager {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerManager.class);

    private final int failureThreshold;
    private final Duration openDuration;
    private final ConcurrentHashMap<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public CircuitBreakerManager(
            @Value("${hookswarm.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${hookswarm.circuit-breaker.open-duration-seconds:60}") long openDurationSeconds) {
        this.failureThreshold = failureThreshold;
        this.openDuration = Duration.ofSeconds(openDurationSeconds);
    }

    public boolean isOpen(String subscriptionId) {
        CircuitState state = circuits.get(subscriptionId);
        if (state == null) return false;

        return switch (state.status) {
            case CLOSED -> false;
            case OPEN -> {
                // If open duration has elapsed transition to HALF_OPEN
                if (Instant.now().isAfter(state.openedAt.plus(openDuration))) {
                    circuits.put(subscriptionId, state.toHalfOpen());
                    log.info("Circuit HALF_OPEN for subscription {}", subscriptionId);
                    yield false; // allow one probe
                }
                yield true;
            }
            case HALF_OPEN -> false; // allow probe
        };
    }

    public void recordSuccess(String subscriptionId) {
        CircuitState state = circuits.get(subscriptionId);
        if (state != null && state.status != Status.CLOSED) {
            log.info("Circuit CLOSED for subscription {}", subscriptionId);
        }
        circuits.remove(subscriptionId);
    }

    public void recordFailure(String subscriptionId) {
        circuits.compute(subscriptionId, (id, current) -> {
            if (current == null) {
                current = CircuitState.initial();
            }

            CircuitState next = switch (current.status) {
                case CLOSED -> {
                    int newCount = current.consecutiveFailures + 1;
                    if (newCount >= failureThreshold) {
                        log.warn("Circuit OPEN for subscription {} after {} failures",
                                id, newCount);
                        yield current.toOpen(newCount);
                    }
                    yield current.withFailure(newCount);
                }
                case HALF_OPEN -> {
                    log.warn("Circuit re-OPENED for subscription {} (probe failed)", id);
                    yield current.toOpen(current.consecutiveFailures + 1);
                }
                case OPEN -> current; // already open, no change
            };
            return next;
        });
    }

    enum Status { CLOSED, OPEN, HALF_OPEN }

    record CircuitState(
            Status status,
            int consecutiveFailures,
            Instant openedAt
    ) {

        static CircuitState initial() {
            return new CircuitState(Status.CLOSED, 0, null);
        }

        CircuitState withFailure(int count) {
            return new CircuitState(Status.CLOSED, count, null);
        }

        CircuitState toOpen(int failureCount) {
            return new CircuitState(Status.OPEN, failureCount, Instant.now());
        }

        CircuitState toHalfOpen() {
            return new CircuitState(Status.HALF_OPEN, consecutiveFailures, openedAt);
        }

    }

}