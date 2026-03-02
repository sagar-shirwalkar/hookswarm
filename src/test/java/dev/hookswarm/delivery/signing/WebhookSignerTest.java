package dev.hookswarm.delivery.signing;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    void shouldSignPayload() {
        String payload = "{\"hello\":\"world\"}";
        String secret = "test-secret";
        String signature = signer.sign(payload, secret);
        assertThat(signature).startsWith("sha256=");
        assertThat(signature).hasSize(64 + 7); // sha256= + 64 hex chars
    }
}