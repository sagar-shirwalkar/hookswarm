package dev.hookswarm.delivery.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

@Component
public class ReactiveRetryPolicy {

    private static final BiFunction<Double, Long, Long> applyJitter =
            (factor, delay) -> (long) (delay * ThreadLocalRandom.current()
                    .nextDouble(1.0 - factor, 1.0 + factor));

    private final IntFunction<Duration> delayCalculator;

    public ReactiveRetryPolicy(
            @Value("${hookswarm.retry.base-delay-seconds:10}") long baseDelaySeconds,
            @Value("${hookswarm.retry.max-delay-seconds:3600}") long maxDelaySeconds,
            @Value("${hookswarm.retry.multiplier:3}") double multiplier,
            @Value("${hookswarm.retry.jitter-factor:0.2}") double jitterFactor) {

        validate(baseDelaySeconds, maxDelaySeconds, multiplier, jitterFactor);

        this.delayCalculator = attempt -> Duration.ofSeconds(
            applyJitter.apply(
                jitterFactor,
                Math.min(
                    maxDelaySeconds,
                    (long) (baseDelaySeconds * Math.pow(multiplier, attempt - 1))
                )
            )
        );

    }

    public Mono<OffsetDateTime> nextAttemptTime(int attempt) {
        return Mono.just(attempt)
                .map(delayCalculator::apply)
                .map(delay -> OffsetDateTime.now(ZoneOffset.UTC).plus(delay));
    }

    private void validate(long base, long max, double mult, double jitter) {
        if (base <= 0 || max < base || mult <= 1.0 || jitter < 0 || jitter > 1) {
            throw new IllegalArgumentException("Invalid retry configuration");
        }
    }
}
