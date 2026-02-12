package dev.hookswarm.integration;


import dev.hookswarm.common.IdGenerator;
import dev.hookswarm.delivery.engine.DeliveryEngine;
import dev.hookswarm.delivery.repository.DeliveryTaskRepository;
import dev.hookswarm.event.model.Event;
import dev.hookswarm.event.repository.EventRepository;
import dev.hookswarm.outbox.OutboxEntry;
import dev.hookswarm.outbox.OutboxPoller;
import dev.hookswarm.outbox.repository.OutboxRepository;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import dev.hookswarm.subscription.repository.SubscriptionRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired JdbcClient jdbc;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired EventRepository eventRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired DeliveryTaskRepository deliveryTaskRepository;
    @Autowired OutboxPoller outboxPoller;
    @Autowired DeliveryEngine deliveryEngine;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        cleanDatabase();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    private void cleanDatabase() {
        // Order matters
        jdbc.sql("TRUNCATE TABLE dead_letter_queue, delivery_attempts, delivery_tasks, outbox, events, subscriptions CASCADE").update();
    }

    //
    // Full Happy Path
    //

    @Test
    void fullFlow_eventDeliveredSuccessfully() throws Exception {
        String webhookUrl = mockWebServer.url("/webhook").toString();
        Subscription sub = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("order.created"), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(sub);

        Event event = new Event(
                IdGenerator.newId(), "order.created",
                "{\"orderId\":\"ORD-999\"}", "idem_it_01", Instant.now());
        eventRepository.insert(event);

        OutboxEntry outbox = new OutboxEntry(
                IdGenerator.newId(), event.id(), "order.created",
                false, Instant.now(), null);
        outboxRepository.insert(outbox);

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        outboxPoller.poll();
        deliveryEngine.poll();

        // 1. Verify HTTP call (Sync)
        RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("X-HookSwarm-Event-Type")).isEqualTo("order.created");

        // 2. Verify DB Update (Async - wait for it)
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            String taskStatus = jdbc.sql("""
                SELECT status FROM delivery_tasks
                WHERE event_id = :eventId AND subscription_id = :subId
                """)
                    .param("eventId", event.id())
                    .param("subId", sub.id())
                    .query(String.class)
                    .single();
            assertThat(taskStatus).isEqualTo("DELIVERED");
        });

        Long attemptCount = jdbc.sql("""
                SELECT COUNT(*) FROM delivery_attempts da
                JOIN delivery_tasks dt ON da.delivery_task_id = dt.id
                WHERE dt.event_id = :eventId
                """)
                .param("eventId", event.id())
                .query(Long.class)
                .single();
        assertThat(attemptCount).isEqualTo(1);
    }

    //
    // Event Type Filtering
    //

    @Test
    void eventTypeFiltering_onlyMatchingSubscriptionsReceive() {
        String webhookUrl = mockWebServer.url("/webhook").toString();

        Subscription orderSub = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("order.created"), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(orderSub);

        Subscription userSub = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("user.signed_up"), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(userSub);

        Event event = new Event(
                IdGenerator.newId(), "order.created",
                "{\"test\":true}", "idem_filter_01", Instant.now());
        eventRepository.insert(event);

        OutboxEntry outbox = new OutboxEntry(
                IdGenerator.newId(), event.id(), "order.created",
                false, Instant.now(), null);
        outboxRepository.insert(outbox);

        outboxPoller.poll();

        Long taskCount = jdbc.sql("SELECT COUNT(*) FROM delivery_tasks WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(Long.class)
                .single();
        assertThat(taskCount).isEqualTo(1);

        String subId = jdbc.sql("SELECT subscription_id FROM delivery_tasks WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(String.class)
                .single();
        assertThat(subId).isEqualTo(orderSub.id());
    }

    @Test
    void wildcardSubscription_receivesAllEventTypes() {
        String webhookUrl = mockWebServer.url("/webhook").toString();

        Subscription wildcard = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of(), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(wildcard);

        Event event = new Event(
                IdGenerator.newId(), "some.random.event",
                "{}", "idem_wc_01", Instant.now());
        eventRepository.insert(event);

        OutboxEntry outbox = new OutboxEntry(
                IdGenerator.newId(), event.id(), "some.random.event",
                false, Instant.now(), null);
        outboxRepository.insert(outbox);

        outboxPoller.poll();

        Long taskCount = jdbc.sql("SELECT COUNT(*) FROM delivery_tasks WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(Long.class)
                .single();
        assertThat(taskCount).isEqualTo(1);
    }

    @Test
    void mixedSubscriptions_wildcardAndSpecific_bothReceive() {
        String webhookUrl = mockWebServer.url("/webhook").toString();

        Subscription specific = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("order.created"), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(specific);

        Subscription wildcard = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of(), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(wildcard);

        Subscription nonMatch = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("user.deleted"), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(nonMatch);

        Event event = new Event(
                IdGenerator.newId(), "order.created",
                "{}", "idem_mix_01", Instant.now());
        eventRepository.insert(event);

        OutboxEntry outbox = new OutboxEntry(
                IdGenerator.newId(), event.id(), "order.created",
                false, Instant.now(), null);
        outboxRepository.insert(outbox);

        outboxPoller.poll();

        Long taskCount = jdbc.sql("SELECT COUNT(*) FROM delivery_tasks WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(Long.class)
                .single();
        assertThat(taskCount).isEqualTo(2);
    }

    @Test
    void pausedSubscription_doesNotReceive() {
        String webhookUrl = mockWebServer.url("/webhook").toString();

        Subscription paused = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("order.created"), SubscriptionStatus.PAUSED,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(paused);

        Event event = new Event(
                IdGenerator.newId(), "order.created",
                "{}", "idem_paused_01", Instant.now());
        eventRepository.insert(event);

        OutboxEntry outbox = new OutboxEntry(
                IdGenerator.newId(), event.id(), "order.created",
                false, Instant.now(), null);
        outboxRepository.insert(outbox);

        outboxPoller.poll();

        Long taskCount = jdbc.sql("SELECT COUNT(*) FROM delivery_tasks WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(Long.class)
                .single();
        assertThat(taskCount).isEqualTo(0);
    }

    //
    // Fan-Out: Multiple Events
    //

    @Test
    void multipleEvents_eachFannedOutIndependently() {
        String webhookUrl = mockWebServer.url("/webhook").toString();

        Subscription sub = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of(), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(sub);

        for (int i = 1; i <= 3; i++) {
            Event event = new Event(
                    IdGenerator.newId(), "batch.event",
                    "{\"seq\":" + i + "}", "idem_batch_" + i, Instant.now());
            eventRepository.insert(event);

            OutboxEntry outbox = new OutboxEntry(
                    IdGenerator.newId(), event.id(), "batch.event",
                    false, Instant.now(), null);
            outboxRepository.insert(outbox);
        }

        outboxPoller.poll();

        Long taskCount = jdbc.sql("SELECT COUNT(*) FROM delivery_tasks").query(Long.class).single();
        assertThat(taskCount).isEqualTo(3);

        Long unprocessed = jdbc.sql("SELECT COUNT(*) FROM outbox WHERE processed = false")
                .query(Long.class).single();
        assertThat(unprocessed).isEqualTo(0);
    }

    //
    // Retry + DLQ
    //

    @Test
    void failedDelivery_retriesAndEventuallyDLQs() {
        String webhookUrl = mockWebServer.url("/webhook").toString();

        Subscription sub = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("fail.test"), SubscriptionStatus.ACTIVE,
                3, // at leat 2 retries
                Instant.now(), Instant.now());
        subscriptionRepository.insert(sub);

        Event event = new Event(
                IdGenerator.newId(), "fail.test",
                "{}", "idem_fail_01", Instant.now());
        eventRepository.insert(event);

        OutboxEntry outbox = new OutboxEntry(
                IdGenerator.newId(), event.id(), "fail.test",
                false, Instant.now(), null);
        outboxRepository.insert(outbox);

        // Enqueue failures (1 initial + 2 retries = 3 failures total needed)
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("fail"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("fail"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("fail")); // Add one more just in case

        // 1. Initial Processing
        outboxPoller.poll();
        deliveryEngine.poll();

        // Wait for first failure
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            String status = jdbc.sql("SELECT status FROM delivery_tasks WHERE event_id = :eventId")
                    .param("eventId", event.id()).query(String.class).single();
            assertThat(status).isEqualTo("FAILED");
        });

        // 2. First Retry
        // Fast-forward time so it's due now
        jdbc.sql("UPDATE delivery_tasks SET next_attempt_at = NOW() - INTERVAL '1 minute' WHERE status = 'FAILED'").update();
        deliveryEngine.poll();

        // Wait for second failure
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Integer attempts = jdbc.sql("SELECT attempt_count FROM delivery_tasks WHERE event_id = :eventId")
                    .param("eventId", event.id()).query(Integer.class).single();
            assertThat(attempts).isEqualTo(2);
            String status = jdbc.sql("SELECT status FROM delivery_tasks WHERE event_id = :eventId")
                    .param("eventId", event.id()).query(String.class).single();
            assertThat(status).isEqualTo("FAILED");
        });

        // 3. Second (Final) Retry -> Should go to DLQ
        jdbc.sql("UPDATE delivery_tasks SET next_attempt_at = NOW() - INTERVAL '1 minute' WHERE status = 'FAILED'").update();
        deliveryEngine.poll();

        // Wait for final DEAD status
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            String status = jdbc.sql("SELECT status FROM delivery_tasks WHERE event_id = :eventId")
                    .param("eventId", event.id())
                    .query(String.class)
                    .single();
            assertThat(status).isEqualTo("DEAD");
        });

        Long dlqCount = jdbc.sql("SELECT COUNT(*) FROM dead_letter_queue WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(Long.class)
                .single();
        assertThat(dlqCount).isEqualTo(1);
    }

    //
    // Webhook Signature Verification
    //

    @Test
    void deliveredWebhook_hasValidSignature() throws Exception {
        String webhookUrl = mockWebServer.url("/webhook").toString();
        String secret = IdGenerator.newSecret();

        Subscription sub = new Subscription(
                IdGenerator.newId(), webhookUrl, secret,
                Set.of("sig.test"), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(sub);

        Event event = new Event(
                IdGenerator.newId(), "sig.test",
                "{\"verify\":\"me\"}", "idem_sig_01", Instant.now());
        eventRepository.insert(event);

        OutboxEntry outbox = new OutboxEntry(
                IdGenerator.newId(), event.id(), "sig.test",
                false, Instant.now(), null);
        outboxRepository.insert(outbox);

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        outboxPoller.poll();
        deliveryEngine.poll();

        RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();

        String signature = request.getHeader("X-HookSwarm-Signature");
        String body = request.getBody().readUtf8();

        assertThat(signature).isNotNull().startsWith("sha256=");

        dev.hookswarm.delivery.signing.WebhookSigner signer = new dev.hookswarm.delivery.signing.WebhookSigner();
        String expectedSig = signer.sign(body, secret);
        assertThat(signature).isEqualTo(expectedSig);
    }

    @Test
    void outboxPoller_processedEntriesNotPickedUpAgain() {
        String webhookUrl = mockWebServer.url("/webhook").toString();

        Subscription sub = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of(), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(sub);

        Event event = new Event(
                IdGenerator.newId(), "dedup.test",
                "{}", "idem_dedup_01", Instant.now());
        eventRepository.insert(event);

        OutboxEntry outbox = new OutboxEntry(
                IdGenerator.newId(), event.id(), "dedup.test",
                false, Instant.now(), null);
        outboxRepository.insert(outbox);

        outboxPoller.poll();
        outboxPoller.poll();

        Long taskCount = jdbc.sql("SELECT COUNT(*) FROM delivery_tasks WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(Long.class)
                .single();
        assertThat(taskCount).isEqualTo(1);
    }

}