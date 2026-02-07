package dev.hookswarm.delivery.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

// Exponential backoff with jitter
// ±20% jitter prevents thundering herd on recovery.
@Component
public class RetryPolicy {

    private final long baseDelaySeconds;
    private final long maxDelaySeconds;
    private final double multiplier;
    private final double jitterFactor;

    public RetryPolicy(
            @Value("${hookswarm.retry.base-delay-seconds:10}") long baseDelaySeconds,
            @Value("${hookswarm.retry.max-delay-seconds:3600}") long maxDelaySeconds,
            @Value("${hookswarm.retry.multiplier:3}") double multiplier,
            @Value("${hookswarm.retry.jitter-factor:0.2}") double jitterFactor) {
        this.baseDelaySeconds = baseDelaySeconds;
        this.maxDelaySeconds = maxDelaySeconds;
        this.multiplier = multiplier;
        this.jitterFactor = jitterFactor;
    }

    public Instant nextAttemptTime(int attemptNumber) {
        long delaySeconds = (long) (baseDelaySeconds * Math.pow(multiplier, attemptNumber - 1));
        delaySeconds = Math.min(delaySeconds, maxDelaySeconds);

        // Jitter: ±jitterFactor
        double jitter = delaySeconds * jitterFactor * (ThreadLocalRandom.current().nextDouble() * 2 - 1);
        delaySeconds = Math.max(1, delaySeconds + (long) jitter);

        return Instant.now().plusSeconds(delaySeconds);
    }

}