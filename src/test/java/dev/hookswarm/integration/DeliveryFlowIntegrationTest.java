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

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired JdbcClient jdbc;
    @Autowired
    SubscriptionRepository subscriptionRepository;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    OutboxRepository outboxRepository;
    @Autowired
    DeliveryTaskRepository deliveryTaskRepository;
    @Autowired
    OutboxPoller outboxPoller;
    @Autowired
    DeliveryEngine deliveryEngine;

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
        jdbc.sql("DELETE FROM dead_letter_queue").update();
        jdbc.sql("DELETE FROM delivery_attempts").update();
        jdbc.sql("DELETE FROM delivery_tasks").update();
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM events").update();
        jdbc.sql("DELETE FROM subscriptions").update();
    }

    //
    // Full Happy Path
    //

    @Test
    void fullFlow_eventDeliveredSuccessfully() throws Exception {

        // 1. Register subscription pointing at mock server
        String webhookUrl = mockWebServer.url("/webhook").toString();
        Subscription sub = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("order.created"), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(sub);

        // 2. Insert event
        Event event = new Event(
                IdGenerator.newId(), "order.created",
                "{\"orderId\":\"ORD-999\"}", "idem_it_01", Instant.now());
        eventRepository.insert(event);

        // 3. Insert outbox entry (normally done by EventService in same tx)
        OutboxEntry outbox = new OutboxEntry(
                IdGenerator.newId(), event.id(), "order.created",
                false, Instant.now(), null);
        outboxRepository.insert(outbox);

        // 4. Mock server will return 200
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        // 5. Trigger outbox poller → creates delivery tasks
        outboxPoller.poll();

        // 6. Trigger delivery engine → delivers webhook
        deliveryEngine.poll();

        // 7. Verify mock server received the webhook
        RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(request.getHeader("X-HookSwarm-Signature")).startsWith("sha256=");
        assertThat(request.getHeader("X-HookSwarm-Event-Type")).isEqualTo("order.created");

        String body = request.getBody().readUtf8();
        assertThat(body).contains("ORD-999");

        // 8. Verify delivery task status
        String taskStatus = jdbc.sql("""
                SELECT status FROM delivery_tasks
                WHERE event_id = :eventId AND subscription_id = :subId
                """)
                .param("eventId", event.id())
                .param("subId", sub.id())
                .query(String.class)
                .single();
        assertThat(taskStatus).isEqualTo("DELIVERED");

        // 9. Verify attempt recorded
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

        // Sub 1: listens to order.created
        Subscription orderSub = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("order.created"), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(orderSub);

        // Sub 2: listens to user.signed_up (should NOT match)
        Subscription userSub = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("user.signed_up"), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(userSub);

        // Publish order.created event
        Event event = new Event(
                IdGenerator.newId(), "order.created",
                "{\"test\":true}", "idem_filter_01", Instant.now());
        eventRepository.insert(event);

        OutboxEntry outbox = new OutboxEntry(
                IdGenerator.newId(), event.id(), "order.created",
                false, Instant.now(), null);
        outboxRepository.insert(outbox);

        outboxPoller.poll();

        // Only 1 delivery task created (for orderSub, not userSub)
        Long taskCount = jdbc.sql("SELECT COUNT(*) FROM delivery_tasks WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(Long.class)
                .single();
        assertThat(taskCount).isEqualTo(1);

        // Verify it's for the correct subscription
        String subId = jdbc.sql("SELECT subscription_id FROM delivery_tasks WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(String.class)
                .single();
        assertThat(subId).isEqualTo(orderSub.id());
    }

    @Test
    void wildcardSubscription_receivesAllEventTypes() {
        String webhookUrl = mockWebServer.url("/webhook").toString();

        // Wildcard subscription (empty eventTypes)
        Subscription wildcard = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of(), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(wildcard);

        // Publish any event type
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

        // Specific subscription
        Subscription specific = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("order.created"), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(specific);

        // Wildcard subscription
        Subscription wildcard = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of(), SubscriptionStatus.ACTIVE,
                5, Instant.now(), Instant.now());
        subscriptionRepository.insert(wildcard);

        // Non-matching subscription
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

        // 2 tasks: specific + wildcard. Not nonMatch.
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

        // Publish 3 events
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

        // Verify outbox is fully processed
        Long unprocessed = jdbc.sql("SELECT COUNT(*) FROM outbox WHERE processed = false")
                .query(Long.class).single();
        assertThat(unprocessed).isEqualTo(0);
    }

    //
    // Retry + DLQ (Core Pipeline, Not Hardening API)
    //

    @Test
    void failedDelivery_retriesAndEventuallyDLQs() {
        String webhookUrl = mockWebServer.url("/webhook").toString();

        Subscription sub = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("fail.test"), SubscriptionStatus.ACTIVE,
                2, // only 2 retries
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

        // Enqueue failures
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("fail"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("fail"));

        // Poll 1: outbox -> delivery task
        outboxPoller.poll();

        // Poll 2: first delivery attempt -> fails
        deliveryEngine.poll();

        // Override next_attempt_at so the retry is immediately due
        jdbc.sql("UPDATE delivery_tasks SET next_attempt_at = NOW() - INTERVAL '1 minute' WHERE status = 'FAILED'")
                .update();

        // Poll 3: second attempt -> fails -> DLQ
        deliveryEngine.poll();

        // Verify task is DEAD
        String status = jdbc.sql("SELECT status FROM delivery_tasks WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(String.class)
                .single();
        assertThat(status).isEqualTo("DEAD");

        // Verify DLQ entry exists
        Long dlqCount = jdbc.sql("SELECT COUNT(*) FROM dead_letter_queue WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(Long.class)
                .single();
        assertThat(dlqCount).isEqualTo(1);

        // Verify 2 attempts recorded
        Long attemptCount = jdbc.sql("""
                SELECT COUNT(*) FROM delivery_attempts da
                JOIN delivery_tasks dt ON da.delivery_task_id = dt.id
                WHERE dt.event_id = :eventId
                """)
                .param("eventId", event.id())
                .query(Long.class)
                .single();
        assertThat(attemptCount).isEqualTo(2);
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

        // Verify signature matches, use the same signer
        dev.hookswarm.delivery.signing.WebhookSigner signer = new dev.hookswarm.delivery.signing.WebhookSigner();
        String expectedSig = signer.sign(body, secret);
        assertThat(signature).isEqualTo(expectedSig);
    }

    //
    // Idempotent Outbox Processing
    //

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

        // Poll twice
        outboxPoller.poll();
        outboxPoller.poll();

        // Should only have 1 delivery task, not 2
        Long taskCount = jdbc.sql("SELECT COUNT(*) FROM delivery_tasks WHERE event_id = :eventId")
                .param("eventId", event.id())
                .query(Long.class)
                .single();
        assertThat(taskCount).isEqualTo(1);
    }

}