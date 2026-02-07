package dev.hookswarm.subscription.dto;


import dev.hookswarm.TestFixtures;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionResponseTest {

    @Test
    void from_masksSecretByDefault() {
        Subscription sub = TestFixtures.subscription();

        SubscriptionResponse response = SubscriptionResponse.from(sub);

        assertThat(response.secret()).isNotEqualTo(sub.secret());
        assertThat(response.secret()).startsWith("hsw_");
        assertThat(response.secret()).contains("****");
    }

    @Test
    void from_revealsSecretWhenRequested() {
        Subscription sub = TestFixtures.subscription();

        SubscriptionResponse response = SubscriptionResponse.from(sub, true);

        assertThat(response.secret()).isEqualTo(sub.secret());
    }

    @Test
    void from_mapsAllFields() {
        Subscription sub = TestFixtures.subscription();

        SubscriptionResponse response = SubscriptionResponse.from(sub, true);

        assertThat(response.id()).isEqualTo(sub.id());
        assertThat(response.url()).isEqualTo(sub.url());
        assertThat(response.eventTypes()).isEqualTo(sub.eventTypes());
        assertThat(response.status()).isEqualTo(sub.status());
        assertThat(response.maxRetries()).isEqualTo(sub.maxRetries());
        assertThat(response.createdAt()).isEqualTo(sub.createdAt());
    }

    @Test
    void from_shortSecretGetsMasked() {
        Subscription sub = new Subscription(
                "id", "https://x.com", "short",
                java.util.Set.of(), SubscriptionStatus.ACTIVE, 5,
                java.time.Instant.now(), java.time.Instant.now());

        SubscriptionResponse response = SubscriptionResponse.from(sub);

        assertThat(response.secret()).isEqualTo("****");
    }

}