package dev.hookswarm.delivery.service;


import dev.hookswarm.TestFixtures;
import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.common.exception.ResourceNotFoundException;
import dev.hookswarm.delivery.model.*;
import dev.hookswarm.delivery.repository.DeadLetterRepository;
import dev.hookswarm.delivery.repository.DeliveryAttemptRepository;
import dev.hookswarm.delivery.repository.DeliveryTaskRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    DeliveryTaskRepository taskRepository;
    @Mock
    DeliveryAttemptRepository attemptRepository;
    @Mock
    DeadLetterRepository deadLetterRepository;

    @InjectMocks DeliveryService service;

    //
    // Reads
    //

    @Nested
    class GetTask {

        @Test
        void returnsTask() {
            DeliveryTask task = TestFixtures.pendingTask();
            when(taskRepository.findById("task_01")).thenReturn(Optional.of(task));

            DeliveryTask result = service.getTask("task_01");

            assertThat(result).isEqualTo(task);
        }

        @Test
        void throwsWhenNotFound() {
            when(taskRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTask("missing"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("missing");
        }
    }

    @Nested
    class GetTasksByEventId {

        @Test
        void returnsMappedResponses() {
            DeliveryTask task = TestFixtures.pendingTask();
            when(taskRepository.findByEventId("evt_01")).thenReturn(List.of(task));

            List<DeliveryTaskResponse> results = service.getTasksByEventId("evt_01");

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().eventId()).isEqualTo("evt_01");
        }

        @Test
        void returnsEmptyListWhenNoTasks() {
            when(taskRepository.findByEventId("evt_99")).thenReturn(List.of());

            List<DeliveryTaskResponse> results = service.getTasksByEventId("evt_99");

            assertThat(results).isEmpty();
        }
    }

    @Nested
    class GetTasksBySubscriptionId {

        @Test
        void returnsPaginatedResults() {
            DeliveryTask task = TestFixtures.pendingTask();
            when(taskRepository.findBySubscriptionId("sub_01", 20, 0))
                    .thenReturn(List.of(task));
            when(taskRepository.countBySubscriptionId("sub_01")).thenReturn(1L);

            PagedResponse<DeliveryTaskResponse> result =
                    service.getTasksBySubscriptionId("sub_01", 0, 20);

            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.page()).isEqualTo(0);
        }

        @Test
        void calculatesOffsetCorrectly() {
            when(taskRepository.findBySubscriptionId("sub_01", 10, 30))
                    .thenReturn(List.of());
            when(taskRepository.countBySubscriptionId("sub_01")).thenReturn(0L);

            service.getTasksBySubscriptionId("sub_01", 3, 10);

            verify(taskRepository).findBySubscriptionId("sub_01", 10, 30);
        }
    }

    @Nested
    class GetAttempts {

        @Test
        void returnsSortedAttempts() {
            DeliveryTask task = TestFixtures.pendingTask();
            when(taskRepository.findById("task_01")).thenReturn(Optional.of(task));

            DeliveryAttempt att1 = TestFixtures.attempt("task_01", 1, 500);
            DeliveryAttempt att2 = TestFixtures.attempt("task_01", 2, 200);
            when(attemptRepository.findByDeliveryTaskId("task_01"))
                    .thenReturn(List.of(att1, att2));

            List<DeliveryAttemptResponse> results = service.getAttempts("task_01");

            assertThat(results).hasSize(2);
            assertThat(results.get(0).attemptNumber()).isEqualTo(1);
            assertThat(results.get(1).attemptNumber()).isEqualTo(2);
        }

        @Test
        void throwsWhenTaskNotFound() {
            when(taskRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAttempts("missing"))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(attemptRepository);
        }
    }

    @Nested
    class ListDeadLetters {

        @Test
        void returnsPaginatedDLQ() {
            DeadLetterEntry entry = TestFixtures.deadLetterEntry();
            when(deadLetterRepository.findAll(20, 0)).thenReturn(List.of(entry));
            when(deadLetterRepository.count()).thenReturn(1L);

            PagedResponse<DeadLetterResponse> result = service.listDeadLetters(0, 20);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().getFirst().deliveryTaskId()).isEqualTo("task_01");
            assertThat(result.totalElements()).isEqualTo(1);
        }

        @Test
        void returnsEmptyPage() {
            when(deadLetterRepository.findAll(20, 0)).thenReturn(List.of());
            when(deadLetterRepository.count()).thenReturn(0L);

            PagedResponse<DeadLetterResponse> result = service.listDeadLetters(0, 20);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0);
        }
    }

    //
    // Manual Retry
    //

    @Nested
    class RetryTask {

        @Test
        void retryFailedTask_resetsToPending() {
            DeliveryTask failedTask = TestFixtures.failedTask(3);
            DeliveryTask pendingAfter = TestFixtures.pendingTask(); // simulates state after reset

            when(taskRepository.findById("task_01"))
                    .thenReturn(Optional.of(failedTask))    // first call — check status
                    .thenReturn(Optional.of(pendingAfter)); // second call — return response

            DeliveryTaskResponse result = service.retryTask("task_01");

            verify(taskRepository).resetToPending(eq("task_01"), any(Instant.class), any(Instant.class));
            verify(deadLetterRepository, never()).deleteByDeliveryTaskId(any());
        }

        @Test
        void retryDeadTask_resetsForReplayAndRemovesDLQ() {
            DeliveryTask deadTask = TestFixtures.deadTask();
            DeliveryTask pendingAfter = TestFixtures.pendingTask();

            when(taskRepository.findById("task_01"))
                    .thenReturn(Optional.of(deadTask))
                    .thenReturn(Optional.of(pendingAfter));

            service.retryTask("task_01");

            verify(taskRepository).resetForReplay(eq("task_01"), any(Instant.class));
            verify(deadLetterRepository).deleteByDeliveryTaskId("task_01");
        }

        @Test
        void retryDeliveredTask_throwsIllegalState() {
            DeliveryTask deliveredTask = TestFixtures.deliveredTask();
            when(taskRepository.findById("task_01")).thenReturn(Optional.of(deliveredTask));

            assertThatThrownBy(() -> service.retryTask("task_01"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DELIVERED")
                    .hasMessageContaining("FAILED or DEAD");

            verify(taskRepository, never()).resetToPending(any(), any(), any());
            verify(taskRepository, never()).resetForReplay(any(), any());
        }

        @Test
        void retryPendingTask_throwsIllegalState() {
            DeliveryTask pendingTask = TestFixtures.pendingTask();
            when(taskRepository.findById("task_01")).thenReturn(Optional.of(pendingTask));

            assertThatThrownBy(() -> service.retryTask("task_01"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PENDING");
        }

        @Test
        void retryInFlightTask_throwsIllegalState() {
            DeliveryTask inFlightTask = TestFixtures.inFlightTask(1, Instant.now());
            when(taskRepository.findById("task_01")).thenReturn(Optional.of(inFlightTask));

            assertThatThrownBy(() -> service.retryTask("task_01"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("IN_FLIGHT");
        }

        @Test
        void retryNonexistentTask_throws404() {
            when(taskRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.retryTask("missing"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    //
    // DLQ Replay
    //

    @Nested
    class ReplayDeadLetter {

        @Test
        void replaysSuccessfully() {
            DeadLetterEntry entry = TestFixtures.deadLetterEntry();
            DeliveryTask pendingAfter = TestFixtures.pendingTask();

            when(deadLetterRepository.findById("dlq_01")).thenReturn(Optional.of(entry));
            when(taskRepository.findById("task_01")).thenReturn(Optional.of(pendingAfter));

            DeliveryTaskResponse result = service.replayDeadLetter("dlq_01");

            // Verify task was reset
            verify(taskRepository).resetForReplay(eq("task_01"), any(Instant.class));

            // Verify DLQ entry was removed
            verify(deadLetterRepository).deleteById("dlq_01");
        }

        @Test
        void throwsWhenDLQEntryNotFound() {
            when(deadLetterRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.replayDeadLetter("missing"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("missing");

            verify(taskRepository, never()).resetForReplay(any(), any());
        }

        @Test
        void resetsAttemptCountToZero() {
            DeadLetterEntry entry = TestFixtures.deadLetterEntry();
            DeliveryTask resetTask = new DeliveryTask(
                    "task_01", "evt_01", "sub_01",
                    DeliveryStatus.PENDING, 0,
                    Instant.now(), Instant.now(), Instant.now());

            when(deadLetterRepository.findById("dlq_01")).thenReturn(Optional.of(entry));
            when(taskRepository.findById("task_01")).thenReturn(Optional.of(resetTask));

            DeliveryTaskResponse result = service.replayDeadLetter("dlq_01");

            verify(taskRepository).resetForReplay(eq("task_01"), any());
            assertThat(result.attemptCount()).isEqualTo(0);
            assertThat(result.status()).isEqualTo(DeliveryStatus.PENDING);
        }
    }

}