package dev.hookswarm.integration;


import dev.hookswarm.common.IdGenerator;
import dev.hookswarm.delivery.*;
//import dev.hookswarm.event.Event;
//import dev.hookswarm.event.EventRepository;
//import dev.hookswarm.outbox.OutboxEntry;
//import dev.hookswarm.outbox.OutboxPoller;
//import dev.hookswarm.outbox.OutboxRepository;
//import dev.hookswarm.subscription.Subscription;
//import dev.hookswarm.subscription.SubscriptionRepository;
//import dev.hookswarm.subscription.SubscriptionStatus;
import dev.hookswarm.delivery.engine.DeliveryEngine;
import dev.hookswarm.delivery.engine.StaleTaskRecoveryJob;
import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.delivery.model.DeliveryTaskResponse;
import dev.hookswarm.delivery.service.DeliveryService;
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
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HardeningIntegrationTest extends BaseIntegrationTest {

    @Autowired JdbcClient jdbc;
    @Autowired
    SubscriptionRepository subscriptionRepository;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    OutboxRepository outboxRepository;
    @Autowired
    OutboxPoller outboxPoller;
    @Autowired
    DeliveryEngine deliveryEngine;
    @Autowired
    DeliveryService deliveryService;
    @Autowired
    StaleTaskRecoveryJob recoveryJob;

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
    // Helper: sets up a subscription + event + outbox entry
    // Runs through outbox poller to create a delivery task
    //

    private record TestSetup(Subscription subscription, Event event, String deliveryTaskId) {}

    private TestSetup setupFailedDelivery(int maxRetries, int failureCount) {
        String webhookUrl = mockWebServer.url("/webhook").toString();

        Subscription sub = new Subscription(
                IdGenerator.newId(), webhookUrl, IdGenerator.newSecret(),
                Set.of("test.event"), SubscriptionStatus.ACTIVE,
                maxRetries, Instant.now(), Instant.now());
        subscriptionRepository.insert(sub);

        Event event = new Event(
                IdGenerator.newId(), "test.event",
                "{\"test\":true}", IdGenerator.newId(), Instant.now());
        eventRepository.insert(event);

        OutboxEntry outbox = new OutboxEntry(
                IdGenerator.newId(), event.id(), "test.event",
                false, Instant.now(), null);
        outboxRepository.insert(outbox);

        // Enqueue enough failures
        for (int i = 0; i < failureCount; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("fail"));
        }

        // Create delivery task via outbox poller
        outboxPoller.poll();

        // Run delivery attempts
        for (int i = 0; i < failureCount; i++) {
            deliveryEngine.poll();
            // Make retry immediately eligible
            jdbc.sql("UPDATE delivery_tasks SET next_attempt_at = NOW() - INTERVAL '1 minute' WHERE status IN ('PENDING', 'FAILED')")
                    .update();
        }

        String taskId = jdbc.sql("""
                SELECT id FROM delivery_tasks
                WHERE event_id = :eventId AND subscription_id = :subId
                """)
                .param("eventId", event.id())
                .param("subId", sub.id())
                .query(String.class)
                .single();

        return new TestSetup(sub, event, taskId);
    }

    //
    // Manual Retry — FAILED task
    //

    @Test
    void manualRetry_failedTask_becomesDeliverableAgain() {
        // Setup: 3 max retries, fail 2 times -> task is FAILED (not yet DEAD)
        TestSetup setup = setupFailedDelivery(3, 2);

        // Verify task is FAILED
        String statusBefore = getTaskStatus(setup.deliveryTaskId);
        assertThat(statusBefore).isEqualTo("FAILED");

        // Enqueue a success for the retry
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        // Retry
        DeliveryTaskResponse retried = deliveryService.retryTask(setup.deliveryTaskId);
        assertThat(retried.status()).isEqualTo(DeliveryStatus.PENDING);

        // Deliver
        deliveryEngine.poll();

        // Verify delivered
        String statusAfter = getTaskStatus(setup.deliveryTaskId);
        assertThat(statusAfter).isEqualTo("DELIVERED");

        // Verify total attempts = 3 (2 failures + 1 success)
        Long totalAttempts = jdbc.sql("""
                SELECT COUNT(*) FROM delivery_attempts WHERE delivery_task_id = :taskId
                """)
                .param("taskId", setup.deliveryTaskId)
                .query(Long.class)
                .single();
        assertThat(totalAttempts).isEqualTo(3);
    }

    //
    // Manual Retry - DEAD task (from DLQ)
    //

    @Test
    void manualRetry_deadTask_resetsAndRemovesDLQEntry() {
        // Setup: 2 max retries, fail 2 times -> task is DEAD, in DLQ
        TestSetup setup = setupFailedDelivery(2, 2);

        // Verify task is DEAD
        String statusBefore = getTaskStatus(setup.deliveryTaskId);
        assertThat(statusBefore).isEqualTo("DEAD");

        // Verify DLQ entry exists
        Long dlqCountBefore = jdbc.sql("SELECT COUNT(*) FROM dead_letter_queue WHERE delivery_task_id = :taskId")
                .param("taskId", setup.deliveryTaskId)
                .query(Long.class)
                .single();
        assertThat(dlqCountBefore).isEqualTo(1);

        // Enqueue a success
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        // Retry the DEAD task
        DeliveryTaskResponse retried = deliveryService.retryTask(setup.deliveryTaskId);
        assertThat(retried.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(retried.attemptCount()).isEqualTo(0); // fresh start

        // Verify DLQ entry removed
        Long dlqCountAfter = jdbc.sql("SELECT COUNT(*) FROM dead_letter_queue WHERE delivery_task_id = :taskId")
                .param("taskId", setup.deliveryTaskId)
                .query(Long.class)
                .single();
        assertThat(dlqCountAfter).isEqualTo(0);

        // Deliver
        deliveryEngine.poll();

        // Verify delivered
        assertThat(getTaskStatus(setup.deliveryTaskId)).isEqualTo("DELIVERED");
    }

    //
    // DLQ Replay by DLQ ID
    //

    @Test
    void dlqReplay_resetsTaskAndRemovesEntry() {
        // Setup: exhaust retries -> DLQ
        TestSetup setup = setupFailedDelivery(2, 2);

        // Get DLQ entry ID
        String dlqId = jdbc.sql("SELECT id FROM dead_letter_queue WHERE delivery_task_id = :taskId")
                .param("taskId", setup.deliveryTaskId)
                .query(String.class)
                .single();

        // Enqueue success
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        // Replay
        DeliveryTaskResponse replayed = deliveryService.replayDeadLetter(dlqId);
        assertThat(replayed.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(replayed.attemptCount()).isEqualTo(0);

        // DLQ entry gone
        Long dlqCount = jdbc.sql("SELECT COUNT(*) FROM dead_letter_queue WHERE id = :dlqId")
                .param("dlqId", dlqId)
                .query(Long.class)
                .single();
        assertThat(dlqCount).isEqualTo(0);

        // Deliver
        deliveryEngine.poll();
        assertThat(getTaskStatus(setup.deliveryTaskId)).isEqualTo("DELIVERED");
    }

    //
    // Stale Task Recovery
    //

    @Test
    void staleRecovery_stuckInFlightTask_recoveredToPending() {
        // Setup: create a delivery task normally
        TestSetup setup = setupFailedDelivery(5, 0);

        // Simulate stuck IN_FLIGHT: manually set status + old updated_at
        jdbc.sql("""
                UPDATE delivery_tasks
                SET status = 'IN_FLIGHT', updated_at = NOW() - INTERVAL '10 minutes'
                WHERE id = :taskId
                """)
                .param("taskId", setup.deliveryTaskId)
                .update();

        // Verify it's stuck
        assertThat(getTaskStatus(setup.deliveryTaskId)).isEqualTo("IN_FLIGHT");

        // Run recovery
        recoveryJob.recover();

        // Should be back to PENDING (attemptCount was 0)
        assertThat(getTaskStatus(setup.deliveryTaskId)).isEqualTo("PENDING");

        // Enqueue success and deliver
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        deliveryEngine.poll();

        assertThat(getTaskStatus(setup.deliveryTaskId)).isEqualTo("DELIVERED");
    }

    @Test
    void staleRecovery_stuckAfterAttempts_recoveredToFailed() {
        // Setup: fail once, then simulate stuck IN_FLIGHT
        TestSetup setup = setupFailedDelivery(5, 1);

        // Simulate stuck IN_FLIGHT with existing attempts
        jdbc.sql("""
                UPDATE delivery_tasks
                SET status = 'IN_FLIGHT', updated_at = NOW() - INTERVAL '10 minutes'
                WHERE id = :taskId
                """)
                .param("taskId", setup.deliveryTaskId)
                .update();

        recoveryJob.recover();

        // Should be FAILED (attemptCount > 0) so it gets picked up by normal retry flow
        assertThat(getTaskStatus(setup.deliveryTaskId)).isEqualTo("FAILED");
    }

    @Test
    void staleRecovery_recentInFlightTask_notRecovered() {
        TestSetup setup = setupFailedDelivery(5, 0);

        // Set IN_FLIGHT with RECENT updated_at (not stale)
        jdbc.sql("""
                UPDATE delivery_tasks
                SET status = 'IN_FLIGHT', updated_at = NOW()
                WHERE id = :taskId
                """)
                .param("taskId", setup.deliveryTaskId)
                .update();

        recoveryJob.recover();

        // Should still be IN_FLIGHT — not stale enough
        assertThat(getTaskStatus(setup.deliveryTaskId)).isEqualTo("IN_FLIGHT");
    }

    //
    // Read API via Service
    //

    @Test
    void getAttempts_returnsAllAttemptsInOrder() {
        TestSetup setup = setupFailedDelivery(5, 3);

        var attempts = deliveryService.getAttempts(setup.deliveryTaskId);

        assertThat(attempts).hasSize(3);
        assertThat(attempts.get(0).attemptNumber()).isEqualTo(1);
        assertThat(attempts.get(1).attemptNumber()).isEqualTo(2);
        assertThat(attempts.get(2).attemptNumber()).isEqualTo(3);

        // All should be failures (HTTP 500)
        assertThat(attempts).allSatisfy(a -> {
            assertThat(a.httpStatusCode()).isEqualTo(500);
            assertThat(a.errorMessage()).isNotNull();
            assertThat(a.latencyMs()).isPositive();
        });
    }

    @Test
    void getTasksByEventId_returnsTasks() {
        TestSetup setup = setupFailedDelivery(5, 0);

        var tasks = deliveryService.getTasksByEventId(setup.event.id());

        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().eventId()).isEqualTo(setup.event.id());
        assertThat(tasks.getFirst().subscriptionId()).isEqualTo(setup.subscription.id());
    }

    @Test
    void listDeadLetters_returnsPagedResults() {
        // Create 3 dead tasks
        setupFailedDelivery(1, 1);
        setupFailedDelivery(1, 1);
        setupFailedDelivery(1, 1);

        var page0 = deliveryService.listDeadLetters(0, 2);
        assertThat(page0.content()).hasSize(2);
        assertThat(page0.totalElements()).isEqualTo(3);
        assertThat(page0.totalPages()).isEqualTo(2);

        var page1 = deliveryService.listDeadLetters(1, 2);
        assertThat(page1.content()).hasSize(1);
    }

    //
    // Helper
    //

    private String getTaskStatus(String taskId) {
        return jdbc.sql("SELECT status FROM delivery_tasks WHERE id = :id")
                .param("id", taskId)
                .query(String.class)
                .single();
    }

}