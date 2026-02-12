package dev.hookswarm.delivery.engine;


import dev.hookswarm.TestFixtures;
import dev.hookswarm.delivery.model.DeadLetterEntry;
import dev.hookswarm.delivery.model.DeliveryResult;
import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.delivery.model.DeliveryTask;
import dev.hookswarm.delivery.repository.DeadLetterRepository;
import dev.hookswarm.delivery.repository.DeliveryTaskRepository;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryEngineTest {

    @Mock
    DeliveryTaskRepository taskRepository;
    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    DeadLetterRepository deadLetterRepository;
    @Mock
    DeliveryWorker worker;
    @Mock
    RetryPolicy retryPolicy;
    @Mock
    CircuitBreakerManager circuitBreakers;
    @Mock
    ExecutorService deliveryExecutor;

    private DeliveryEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DeliveryEngine(
                taskRepository,
                subscriptionRepository,
                deadLetterRepository,
                worker,
                retryPolicy,
                circuitBreakers,
                deliveryExecutor,
                500
        );

        // Make executor run synchronously so assertions work
        when(deliveryExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        });
    }

    @Test
    void poll_doesNothingWhenNoDueTasks() {
        when(taskRepository.findDueAndMarkInFlight(anyInt(), any()))
                .thenReturn(List.of());

        engine.poll();

        verifyNoInteractions(worker);
        verifyNoInteractions(deliveryExecutor);
    }

    @Test
    void poll_dispatchesDeliverableTasks() {
        DeliveryTask task = TestFixtures.pendingTask();
        when(taskRepository.findDueAndMarkInFlight(anyInt(), any()))
                .thenReturn(List.of(task));
        when(circuitBreakers.isOpen("sub_01")).thenReturn(false);
        when(worker.deliver(task))
                .thenReturn(DeliveryResult.success(200, Duration.ofMillis(50)));

        engine.poll();

        verify(worker).deliver(task);
        verify(taskRepository).markDelivered(eq("task_01"), any());
    }

    @Test
    void poll_revertsCircuitBrokenTasks_zerAttempts_resetsToPending() {
        DeliveryTask task = TestFixtures.pendingTask(); // attemptCount = 0
        when(taskRepository.findDueAndMarkInFlight(anyInt(), any()))
                .thenReturn(List.of(task));
        when(circuitBreakers.isOpen("sub_01")).thenReturn(true);

        engine.poll();

        verifyNoInteractions(worker);
        verify(taskRepository).resetToPending(
                eq("task_01"), any(Instant.class), any(Instant.class));
    }

    // ADD — new test for the other branch
    @Test
    void poll_revertsCircuitBrokenTasks_withAttempts_marksFaild() {
        DeliveryTask task = TestFixtures.failedTask(3); // attemptCount = 3
        when(taskRepository.findDueAndMarkInFlight(anyInt(), any()))
                .thenReturn(List.of(task));
        when(circuitBreakers.isOpen("sub_01")).thenReturn(true);

        engine.poll();

        verifyNoInteractions(worker);
        verify(taskRepository).markFailed(
                eq("task_01"), eq(3), any(Instant.class), any(Instant.class));
    }

    @Test
    void poll_mixOfDeliverableAndBlocked() {
        DeliveryTask deliverable = TestFixtures.pendingTask();
        DeliveryTask blocked = new DeliveryTask(
                "task_02", "evt_02", "sub_02",
                DeliveryStatus.PENDING, 0,
                Instant.now(), Instant.now(), Instant.now());

        when(taskRepository.findDueAndMarkInFlight(anyInt(), any()))
                .thenReturn(List.of(deliverable, blocked));
        when(circuitBreakers.isOpen("sub_01")).thenReturn(false);
        when(circuitBreakers.isOpen("sub_02")).thenReturn(true);
        when(worker.deliver(deliverable))
                .thenReturn(DeliveryResult.success(200, Duration.ofMillis(50)));

        engine.poll();

        verify(worker).deliver(deliverable);
        verify(worker, never()).deliver(blocked);
    }

    @Test
    void poll_onSuccess_marksDeliveredAndResetsCircuit() {
        DeliveryTask task = TestFixtures.pendingTask();
        when(taskRepository.findDueAndMarkInFlight(anyInt(), any()))
                .thenReturn(List.of(task));
        when(circuitBreakers.isOpen("sub_01")).thenReturn(false);
        when(worker.deliver(task))
                .thenReturn(DeliveryResult.success(200, Duration.ofMillis(50)));

        engine.poll();

        verify(taskRepository).markDelivered(eq("task_01"), any());
        verify(circuitBreakers).recordSuccess("sub_01");
    }

    @Test
    void poll_onFailure_schedulesRetry() {
        DeliveryTask task = TestFixtures.pendingTask(); // attemptCount = 0
        Subscription sub = TestFixtures.subscription(); // maxRetries = 5
        Instant nextRetry = Instant.now().plusSeconds(30);

        when(taskRepository.findDueAndMarkInFlight(anyInt(), any()))
                .thenReturn(List.of(task));
        when(circuitBreakers.isOpen("sub_01")).thenReturn(false);
        when(worker.deliver(task))
                .thenReturn(DeliveryResult.failure(500, Duration.ofMillis(100), "HTTP 500"));
        when(subscriptionRepository.findById("sub_01"))
                .thenReturn(Optional.of(sub));
        when(retryPolicy.nextAttemptTime(1)).thenReturn(nextRetry);

        engine.poll();

        verify(taskRepository).markFailed("task_01", 1, nextRetry, any());
        verify(circuitBreakers).recordFailure("sub_01");
        verifyNoInteractions(deadLetterRepository);
    }

    @Test
    void poll_onMaxRetriesExhausted_movesToDLQ() {
        // Task already failed 4 times, subscription maxRetries = 5
        DeliveryTask task = TestFixtures.failedTask(4);
        Subscription sub = TestFixtures.subscription(); // maxRetries = 5

        when(taskRepository.findDueAndMarkInFlight(anyInt(), any()))
                .thenReturn(List.of(task));
        when(circuitBreakers.isOpen("sub_01")).thenReturn(false);
        when(worker.deliver(task))
                .thenReturn(DeliveryResult.failure(503, Duration.ofMillis(100), "HTTP 503"));
        when(subscriptionRepository.findById("sub_01"))
                .thenReturn(Optional.of(sub));

        engine.poll();

        // Task marked dead
        verify(taskRepository).markDead(eq("task_01"), eq(5), any());

        // DLQ entry created
        ArgumentCaptor<DeadLetterEntry> captor =
                ArgumentCaptor.forClass(DeadLetterEntry.class);
        verify(deadLetterRepository).insert(captor.capture());

        DeadLetterEntry dle = captor.getValue();
        assertThat(dle.deliveryTaskId()).isEqualTo("task_01");
        assertThat(dle.eventId()).isEqualTo("evt_01");
        assertThat(dle.subscriptionId()).isEqualTo("sub_01");
        assertThat(dle.totalAttempts()).isEqualTo(5);
        assertThat(dle.lastError()).isEqualTo("HTTP 503");
    }

    @Test
    void poll_onMaxRetriesExhausted_whenSubscriptionDeleted_usesDefault() {
        DeliveryTask task = TestFixtures.failedTask(4);

        when(taskRepository.findDueAndMarkInFlight(anyInt(), any()))
                .thenReturn(List.of(task));
        when(circuitBreakers.isOpen("sub_01")).thenReturn(false);
        when(worker.deliver(task))
                .thenReturn(DeliveryResult.error(Duration.ofMillis(10), "Connection refused"));
        when(subscriptionRepository.findById("sub_01"))
                .thenReturn(Optional.empty()); // subscription deleted

        engine.poll();

        // Default maxRetries is 5, attempt 5 >= 5 -> DLQ
        verify(taskRepository).markDead(eq("task_01"), eq(5), any());
        verify(deadLetterRepository).insert(any());
    }

    @Test
    void poll_handlesUnexpectedWorkerException() {
        DeliveryTask task = TestFixtures.pendingTask();
        Subscription sub = TestFixtures.subscription();

        when(taskRepository.findDueAndMarkInFlight(anyInt(), any()))
                .thenReturn(List.of(task));
        when(circuitBreakers.isOpen("sub_01")).thenReturn(false);
        when(worker.deliver(task)).thenThrow(new RuntimeException("boom"));
        when(subscriptionRepository.findById("sub_01"))
                .thenReturn(Optional.of(sub));
        when(retryPolicy.nextAttemptTime(1)).thenReturn(Instant.now().plusSeconds(10));

        engine.poll(); // should not throw

        verify(taskRepository).markFailed(eq("task_01"), eq(1), any(), any());
    }

}