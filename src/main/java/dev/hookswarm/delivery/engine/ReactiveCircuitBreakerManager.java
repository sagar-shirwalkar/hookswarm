package dev.hookswarm.delivery.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * High-performance reactive circuit breaker using pure functions and lazy evaluation.
 * Optimized for high-throughput webhook delivery systems.
 */
@Component
public class ReactiveCircuitBreakerManager {

    private static final Logger log = LoggerFactory.getLogger(ReactiveCircuitBreakerManager.class);

    private final int failureThreshold;
    private final Duration openDuration;
    private final ConcurrentHashMap<String, CircuitState> circuits = new ConcurrentHashMap<>();

    private final BiFunction<CircuitState, Integer, CircuitState> transitionOnFailure;
    private final Function<CircuitState, CircuitState> transitionToHalfOpen;
    private final Supplier<Instant> nowSupplier;

    public ReactiveCircuitBreakerManager(
            @Value("${hookswarm.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${hookswarm.circuit-breaker.open-duration-seconds:60}") long openDurationSeconds) {

        this.failureThreshold = failureThreshold;
        this.openDuration = Duration.ofSeconds(openDurationSeconds);
        this.nowSupplier = Instant::now;

        // Pre-define state transition functions
        this.transitionOnFailure = (state, threshold) -> {
            int newCount = state.consecutiveFailures + 1;
            return newCount >= threshold
                    ? state.toOpen(newCount, nowSupplier.get())
                    : state.withFailure(newCount);
        };

        this.transitionToHalfOpen = state ->
                new CircuitState(Status.HALF_OPEN, state.consecutiveFailures, state.openedAt);
    }

    // Check if circuit is open. Uses Mono.defer for true laziness.
    public Mono<Boolean> isOpen(String subscriptionId) {
        return Mono.defer(() -> {
            CircuitState state = circuits.get(subscriptionId);

            // Fast path: no state or not open
            if (state == null || state.status != Status.OPEN) {
                return Mono.just(false);
            }

            // Check if should transition to HALF_OPEN
            Instant now = nowSupplier.get();
            if (now.isAfter(state.openedAt.plus(openDuration))) {
                return Mono.fromRunnable(() -> {
                    circuits.computeIfPresent(subscriptionId, (id, current) ->
                            current.status == Status.OPEN && now.isAfter(current.openedAt.plus(openDuration))
                                    ? transitionToHalfOpen.apply(current)
                                    : current
                    );
                    log.info("Circuit HALF_OPEN for subscription {}", subscriptionId);
                }).thenReturn(false);
            }

            return Mono.just(true);
        });
    }

    // Record success - closes circuit.
    public Mono<Void> recordSuccess(String subscriptionId) {
        return Mono.defer(() -> {
            CircuitState removed = circuits.remove(subscriptionId);
            if (removed != null && log.isDebugEnabled()) {
                log.debug("Circuit CLOSED for subscription {}", subscriptionId);
            }
            return Mono.empty();
        });
    }

    // Record failure - may open circuit.
    public Mono<Void> recordFailure(String subscriptionId) {
        return Mono.defer(() -> {
            circuits.compute(subscriptionId, (id, current) -> {
                CircuitState state = current != null ? current : CircuitState.initial();

                return switch (state.status) {
                    case CLOSED -> {
                        CircuitState next = transitionOnFailure.apply(state, failureThreshold);
                        if (next.status == Status.OPEN) {
                            log.warn("Circuit OPEN for subscription {} after {} failures",
                                    id, next.consecutiveFailures);
                        }
                        yield next;
                    }
                    case HALF_OPEN -> {
                        log.warn("Circuit re-OPENED for subscription {} (probe failed)", id);
                        yield state.toOpen(state.consecutiveFailures + 1, nowSupplier.get());
                    }
                    case OPEN -> state; // Already open, no change
                };
            });
            return Mono.empty();
        });
    }

    public Mono<Status> getStatus(String subscriptionId) {
        return Mono.defer(() -> {
            CircuitState state = circuits.get(subscriptionId);
            return Mono.just(state != null ? state.status : Status.CLOSED);
        });
    }

    // Clear circuit state - testing/admin ops
    public Mono<Void> reset(String subscriptionId) {
        return Mono.defer(() -> {
            circuits.remove(subscriptionId);
            return Mono.empty();
        });
    }

    public Mono<Integer> getCircuitCount() {
        return Mono.defer(() -> Mono.just(circuits.size()));
    }

    enum Status { CLOSED, OPEN, HALF_OPEN }

    record CircuitState(Status status, int consecutiveFailures, Instant openedAt) {

        static CircuitState initial() {
            return new CircuitState(Status.CLOSED, 0, null);
        }

        CircuitState withFailure(int count) {
            return new CircuitState(Status.CLOSED, count, null);
        }

        CircuitState toOpen(int failureCount, Instant now) {
            return new CircuitState(Status.OPEN, failureCount, now);
        }
    }

}
