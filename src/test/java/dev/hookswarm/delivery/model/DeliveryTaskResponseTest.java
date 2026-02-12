package dev.hookswarm.delivery.model;

import dev.hookswarm.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTaskResponseTest {

    @Test
    void from_mapsAllFields() {
        DeliveryTask task = TestFixtures.pendingTask();

        DeliveryTaskResponse response = DeliveryTaskResponse.from(task);

        assertThat(response.id()).isEqualTo(task.id());
        assertThat(response.eventId()).isEqualTo(task.eventId());
        assertThat(response.subscriptionId()).isEqualTo(task.subscriptionId());
        assertThat(response.status()).isEqualTo(task.status());
        assertThat(response.attemptCount()).isEqualTo(task.attemptCount());
        assertThat(response.nextAttemptAt()).isEqualTo(task.nextAttemptAt());
        assertThat(response.createdAt()).isEqualTo(task.createdAt());
        assertThat(response.updatedAt()).isEqualTo(task.updatedAt());
    }

    @Test
    void from_preservesFailedStatus() {
        DeliveryTask task = TestFixtures.failedTask(3);

        DeliveryTaskResponse response = DeliveryTaskResponse.from(task);

        assertThat(response.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(response.attemptCount()).isEqualTo(3);
    }

}