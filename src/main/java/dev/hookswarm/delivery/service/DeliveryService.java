package dev.hookswarm.delivery.service;


import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.common.exception.ResourceNotFoundException;
import dev.hookswarm.delivery.model.*;
import dev.hookswarm.delivery.repository.DeadLetterRepository;
import dev.hookswarm.delivery.repository.DeliveryAttemptRepository;
import dev.hookswarm.delivery.repository.DeliveryTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private static final Set<DeliveryStatus> RETRYABLE_STATUSES =
            Set.of(DeliveryStatus.FAILED, DeliveryStatus.DEAD);

    private final DeliveryTaskRepository taskRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final DeadLetterRepository deadLetterRepository;

    public DeliveryService(DeliveryTaskRepository taskRepository,
                           DeliveryAttemptRepository attemptRepository,
                           DeadLetterRepository deadLetterRepository) {
        this.taskRepository = taskRepository;
        this.attemptRepository = attemptRepository;
        this.deadLetterRepository = deadLetterRepository;
    }

    // Reads

    @Transactional(readOnly = true)
    public DeliveryTask getTask(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryTask", taskId));
    }

    @Transactional(readOnly = true)
    public List<DeliveryTaskResponse> getTasksByEventId(String eventId) {
        return taskRepository.findByEventId(eventId).stream()
                .map(DeliveryTaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<DeliveryTaskResponse> getTasksBySubscriptionId(
            String subscriptionId, int page, int size) {
        int offset = page * size;
        List<DeliveryTaskResponse> content = taskRepository
                .findBySubscriptionId(subscriptionId, size, offset).stream()
                .map(DeliveryTaskResponse::from)
                .toList();
        long total = taskRepository.countBySubscriptionId(subscriptionId);
        return PagedResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public List<DeliveryAttemptResponse> getAttempts(String taskId) {
        // Verify task exists
        getTask(taskId);
        return attemptRepository.findByDeliveryTaskId(taskId).stream()
                .map(DeliveryAttemptResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<DeadLetterResponse> listDeadLetters(int page, int size) {
        int offset = page * size;
        List<DeadLetterResponse> content = deadLetterRepository
                .findAll(size, offset).stream()
                .map(DeadLetterResponse::from)
                .toList();
        long total = deadLetterRepository.count();
        return PagedResponse.of(content, page, size, total);
    }

    // Manual Retry for FAILED / DEAD delivery task.

    @Transactional
    public DeliveryTaskResponse retryTask(String taskId) {
        DeliveryTask task = getTask(taskId);

        if (!RETRYABLE_STATUSES.contains(task.status())) {
            throw new IllegalStateException(
                    "Cannot retry task in status %s. Must be FAILED or DEAD."
                            .formatted(task.status()));
        }

        Instant now = Instant.now();

        if (task.status() == DeliveryStatus.DEAD) {
            // DEAD: resets attempt count to 0 + removes DLQ entry. Effectively a DLQ replay by taskId.
            taskRepository.resetForReplay(taskId, now);
            deadLetterRepository.deleteByDeliveryTaskId(taskId);
            log.info("Replayed DEAD task {} — reset to PENDING with fresh attempts", taskId);
        } else {
            // FAILED: sets next_attempt_at = now so, engine picks it immediately. Counts tow. max attempts
            taskRepository.resetToPending(taskId, now, now);
            log.info("Retried FAILED task {} — set to PENDING for immediate pickup", taskId);
        }

        return DeliveryTaskResponse.from(getTask(taskId));
    }

    // DLQ Replay:
    // - Replays a dead letter entry by its DLQ ID.
    // - Resets the associated delivery task to PENDING with attempt count 0.
    // - Removes the DLQ entry.
    @Transactional
    public DeliveryTaskResponse replayDeadLetter(String dlqId) {
        DeadLetterEntry entry = deadLetterRepository.findById(dlqId)
                .orElseThrow(() -> new ResourceNotFoundException("DeadLetterEntry", dlqId));

        Instant now = Instant.now();

        taskRepository.resetForReplay(entry.deliveryTaskId(), now);
        deadLetterRepository.deleteById(dlqId);

        log.info("Replayed DLQ entry {} → task {} reset to PENDING",
                dlqId, entry.deliveryTaskId());

        return DeliveryTaskResponse.from(getTask(entry.deliveryTaskId()));
    }

}