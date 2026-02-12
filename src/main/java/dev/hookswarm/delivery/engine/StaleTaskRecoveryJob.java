package dev.hookswarm.delivery.engine;


import dev.hookswarm.delivery.model.DeliveryTask;
import dev.hookswarm.delivery.repository.DeliveryTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

//
// Recovers delivery tasks stuck in IN_FLIGHT status.
//
// This happens when:
// - The application crashes mid-delivery
// - A virtual thread is interrupted or OOM-killed
// - The HTTP call hangs beyond the stale threshold
//
// Recovery strategy:
// - attemptCount == 0 -> reset to PENDING (never actually attempted)
// - attemptCount > 0 -> set to FAILED with immediate retry
// The delivery engine will pick them up on the next poll cycle.
@Component
public class StaleTaskRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(StaleTaskRecoveryJob.class);

    private final DeliveryTaskRepository taskRepository;
    private final Duration staleThreshold;
    private final int batchSize;

    public StaleTaskRecoveryJob(
            DeliveryTaskRepository taskRepository,
            @Value("${hookswarm.recovery.stale-threshold-minutes:5}") long staleMinutes,
            @Value("${hookswarm.recovery.batch-size:200}") int batchSize) {
        this.taskRepository = taskRepository;
        this.staleThreshold = Duration.ofMinutes(staleMinutes);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${hookswarm.recovery.interval-ms:60000}")
    @Transactional
    public void recover() {
        Instant threshold = Instant.now().minus(staleThreshold);
        List<DeliveryTask> staleTasks = taskRepository.findStaleInFlight(threshold, batchSize);

        if (staleTasks.isEmpty()) return;

        Instant now = Instant.now();
        int resetToPending = 0;
        int resetToFailed = 0;

        for (DeliveryTask task : staleTasks) {
            if (task.attemptCount() == 0) {
                taskRepository.resetToPending(task.id(), now, now);
                resetToPending++;
            } else {
                // Keep attempt count, make immediately eligible for retry
                taskRepository.markFailed(task.id(), task.attemptCount(), now, now);
                resetToFailed++;
            }
        }

        log.warn("Recovered {} stale IN_FLIGHT tasks ({} → PENDING, {} → FAILED)",
                staleTasks.size(), resetToPending, resetToFailed);
    }

}