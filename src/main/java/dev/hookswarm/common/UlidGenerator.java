package dev.hookswarm.common;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates sortable, URL‑safe IDs (similar to ULID but Base64 encoded).
 * Format: 48 bits timestamp (milliseconds) + 80 bits randomness.
 * Encoded as URL‑safe Base64 without padding -> 22 characters.
 */
public final class UlidGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger COUNTER = new AtomicInteger(RANDOM.nextInt());

    private UlidGenerator() {}

    public static String newUlid() {
        return newUlid(Instant.now());
    }

    public static String newUlid(Instant instant) {
        long timestamp = instant.toEpochMilli();
        int randomness = COUNTER.incrementAndGet();

        byte[] bytes = new byte[16]; // 128 bits total

        // timestamp (48 bits), first 6 bytes
        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) timestamp;

        // counter (32 bits), next 4 bytes
        bytes[6] = (byte) (randomness >>> 24);
        bytes[7] = (byte) (randomness >>> 16);
        bytes[8] = (byte) (randomness >>> 8);
        bytes[9] = (byte) randomness;

        // remaining 48 bits (6 bytes) filled with secure random
        byte[] randomBytes = new byte[6];
        RANDOM.nextBytes(randomBytes);
        System.arraycopy(randomBytes, 0, bytes, 10, 6);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}