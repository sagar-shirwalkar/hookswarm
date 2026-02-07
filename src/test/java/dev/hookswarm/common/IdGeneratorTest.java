package dev.hookswarm.common;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorTest {

    @Test
    void newId_returnsUlidFormat() {
        String id = IdGenerator.newId();

        assertThat(id).hasSize(26);
        assertThat(id).matches("[0-9A-Z]{26}");
    }

    @Test
    void newId_isMonotonicallyIncreasing() {
        String id1 = IdGenerator.newId();
        String id2 = IdGenerator.newId();
        String id3 = IdGenerator.newId();

        assertThat(id1).isLessThan(id2);
        assertThat(id2).isLessThan(id3);
    }

    @Test
    void newId_generatesUniqueValues() {
        Set<String> ids = IntStream.range(0, 1000)
                .mapToObj(i -> IdGenerator.newId())
                .collect(Collectors.toSet());

        assertThat(ids).hasSize(1000);
    }

    @Test
    void newSecret_hasCorrectFormat() {
        String secret = IdGenerator.newSecret();

        assertThat(secret).startsWith("hsw_");
        assertThat(secret).hasSize(4 + 64); // prefix + 32 bytes hex
    }

    @Test
    void newSecret_generatesUniqueValues() {
        Set<String> secrets = IntStream.range(0, 100)
                .mapToObj(i -> IdGenerator.newSecret())
                .collect(Collectors.toSet());

        assertThat(secrets).hasSize(100);
    }

}