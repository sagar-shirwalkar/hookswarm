package dev.hookswarm.delivery.engine;


import dev.hookswarm.TestFixtures;
import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.delivery.model.DeliveryTask;
import dev.hookswarm.delivery.repository.DeliveryTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaleTaskRecoveryJobTest {

    @Mock
    DeliveryTaskRepository taskRepository;

    private StaleTaskRecoveryJob createJob(long staleMinutes, int batchSize) {
        return new StaleTaskRecoveryJob(taskRepository, staleMinutes, batchSize);
    }

    @Test
    void noStaleTasks_doesNothing() {
        StaleTaskRecoveryJob job = createJob(5, 200);
        when(taskRepository.findStaleInFlight(any(), eq(200))).thenReturn(List.of());

        job.recover();

        verify(taskRepository, never()).resetToPending(any(), any(), any());
        verify(taskRepository, never()).markFailed(any(), anyInt(), any(), any());
    }

    @Test
    void staleTaskWithZeroAttempts_resetsToPending() {
        StaleTaskRecoveryJob job = createJob(5, 200);
        Instant staleTime = Instant.now().minusSeconds(600);
        DeliveryTask staleTask = TestFixtures.inFlightTask(0, staleTime);

        when(taskRepository.findStaleInFlight(any(), eq(200)))
                .thenReturn(List.of(staleTask));

        job.recover();

        verify(taskRepository).resetToPending(
                eq("task_01"), any(Instant.class), any(Instant.class));
        verify(taskRepository, never()).markFailed(any(), anyInt(), any(), any());
    }

    @Test
    void staleTaskWithPriorAttempts_marksFailedForRetry() {
        StaleTaskRecoveryJob job = createJob(5, 200);
        Instant staleTime = Instant.now().minusSeconds(600);
        DeliveryTask staleTask = TestFixtures.inFlightTask(3, staleTime);

        when(taskRepository.findStaleInFlight(any(), eq(200)))
                .thenReturn(List.of(staleTask));

        job.recover();

        verify(taskRepository).markFailed(
                eq("task_01"), eq(3), any(Instant.class), any(Instant.class));
        verify(taskRepository, never()).resetToPending(any(), any(), any());
    }

    @Test
    void mixOfStaleTaskTypes_handledCorrectly() {
        StaleTaskRecoveryJob job = createJob(5, 200);
        Instant staleTime = Instant.now().minusSeconds(600);

        DeliveryTask neverAttempted = new DeliveryTask(
                "task_01", "evt_01", "sub_01",
                DeliveryStatus.IN_FLIGHT, 0,
                staleTime, staleTime, staleTime);

        DeliveryTask partiallyAttempted = new DeliveryTask(
                "task_02", "evt_02", "sub_02",
                DeliveryStatus.IN_FLIGHT, 2,
                staleTime, staleTime, staleTime);

        DeliveryTask alsoNeverAttempted = new DeliveryTask(
                "task_03", "evt_03", "sub_03",
                DeliveryStatus.IN_FLIGHT, 0,
                staleTime, staleTime, staleTime);

        when(taskRepository.findStaleInFlight(any(), eq(200)))
                .thenReturn(List.of(neverAttempted, partiallyAttempted, alsoNeverAttempted));

        job.recover();

        // task_01 → PENDING
        verify(taskRepository).resetToPending(
                eq("task_01"), any(Instant.class), any(Instant.class));
        // task_02 → FAILED (keep attempt count)
        verify(taskRepository).markFailed(
                eq("task_02"), eq(2), any(Instant.class), any(Instant.class));
        // task_03 → PENDING
        verify(taskRepository).resetToPending(
                eq("task_03"), any(Instant.class), any(Instant.class));
    }

    @Test
    void respectsBatchSize() {
        StaleTaskRecoveryJob job = createJob(5, 50);

        when(taskRepository.findStaleInFlight(any(), eq(50)))
                .thenReturn(List.of());

        job.recover();

        verify(taskRepository).findStaleInFlight(any(), eq(50));
    }

    @Test
    void thresholdCalculatedCorrectly() {
        StaleTaskRecoveryJob job = createJob(10, 200);

        when(taskRepository.findStaleInFlight(any(), eq(200)))
                .thenReturn(List.of());

        Instant before = Instant.now().minusSeconds(10 * 60 + 1);
        job.recover();
        Instant after = Instant.now().minusSeconds(10 * 60 - 1);

        verify(taskRepository).findStaleInFlight(
                argThat(threshold -> threshold.isAfter(before) && threshold.isBefore(after)),
                eq(200));
    }

}