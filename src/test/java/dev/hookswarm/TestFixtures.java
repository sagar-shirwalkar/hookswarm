package dev.hookswarm;

import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.delivery.model.DeliveryTask;
import dev.hookswarm.event.model.Event;
import dev.hookswarm.outbox.OutboxEntry;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;

import java.time.Instant;
import java.util.Set;

public final class TestFixtures {

    private TestFixtures() {}

    private static final Instant NOW = Instant.parse("2025-01-15T10:00:00Z");

    public static Subscription subscription() {
        return new Subscription(
                "sub_01",
                "https://example.com/webhook",
                "hsw_abc123secret",
                Set.of("order.created"),
                SubscriptionStatus.ACTIVE,
                5,
                NOW,
                NOW
        );
    }

    public static Subscription wildcardSubscription() {
        return new Subscription(
                "sub_02",
                "https://audit.example.com/hook",
                "hsw_audit999secret",
                Set.of(),
                SubscriptionStatus.ACTIVE,
                3,
                NOW,
                NOW
        );
    }

    public static Event event() {
        return new Event(
                "evt_01",
                "order.created",
                "{\"orderId\":\"ORD-123\",\"amount\":99.99}",
                "idem_001",
                NOW
        );
    }

    public static DeliveryTask pendingTask() {
        return new DeliveryTask(
                "task_01",
                "evt_01",
                "sub_01",
                DeliveryStatus.PENDING,
                0,
                NOW,
                NOW,
                NOW
        );
    }

    public static DeliveryTask failedTask(int attemptCount) {
        return new DeliveryTask(
                "task_01",
                "evt_01",
                "sub_01",
                DeliveryStatus.FAILED,
                attemptCount,
                NOW.plusSeconds(30),
                NOW,
                NOW
        );
    }

    public static OutboxEntry outboxEntry() {
        return new OutboxEntry(
                "outbox_01",
                "evt_01",
                "order.created",
                false,
                NOW,
                null
        );
    }

}