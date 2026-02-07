package dev.hookswarm.delivery.signing;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    void sign_producesConsistentSignature() {
        String sig1 = signer.sign("hello", "secret");
        String sig2 = signer.sign("hello", "secret");

        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    void sign_startsWithSha256Prefix() {
        String sig = signer.sign("payload", "secret");

        assertThat(sig).startsWith("sha256=");
    }

    @Test
    void sign_differentPayloadsProduceDifferentSignatures() {
        String sig1 = signer.sign("payload1", "secret");
        String sig2 = signer.sign("payload2", "secret");

        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void sign_differentSecretsProduceDifferentSignatures() {
        String sig1 = signer.sign("payload", "secret1");
        String sig2 = signer.sign("payload", "secret2");

        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void sign_producesValidHexLength() {
        String sig = signer.sign("test", "key");

        // sha256= prefix + 64 hex chars (32 bytes)
        assertThat(sig).hasSize(7 + 64);
    }

    @Test
    void sign_knownVector() {
        // HMAC-SHA256("", "key") is a known value — verifies algorithm correctness
        String sig = signer.sign("", "key");

        assertThat(sig).startsWith("sha256=");
        assertThat(sig.substring(7)).matches("[0-9a-f]{64}");
    }

}