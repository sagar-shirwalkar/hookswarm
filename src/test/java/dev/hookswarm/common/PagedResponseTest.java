package dev.hookswarm.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PagedResponseTest {

    @Test
    void of_calculatesTotalPagesCorrectly() {
        PagedResponse<String> response = PagedResponse.of(
                List.of("a", "b", "c"), 0, 10, 25);

        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3); // ceil(25/10)
    }

    @Test
    void of_singlePage() {
        PagedResponse<String> response = PagedResponse.of(
                List.of("a"), 0, 20, 1);

        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void of_emptyContent() {
        PagedResponse<String> response = PagedResponse.of(
                List.of(), 0, 20, 0);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalPages()).isEqualTo(0);
    }

    @Test
    void of_exactlyFull() {
        PagedResponse<String> response = PagedResponse.of(
                List.of("a", "b"), 0, 2, 4);

        assertThat(response.totalPages()).isEqualTo(2); // exactly 4/2
    }

}