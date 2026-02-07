package dev.hookswarm.common;

import com.github.f4b6a3.ulid.UlidCreator;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class IdGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private IdGenerator() {}

    // Monotonic ULID : time-ordered, lexicographically sortable, B-tree friendly for primary keys.
    public static String newId() {
        return UlidCreator.getMonotonicUlid().toString();
    }

    // Generates a webhook signing secret: hsw_ + 32 bytes
    public static String newSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "hsw_" + HexFormat.of().formatHex(bytes);
    }

}