package dev.hookswarm.delivery.engine;

import dev.hookswarm.common.IdGenerator;
import dev.hookswarm.delivery.model.DeadLetterEntry;
import dev.hookswarm.delivery.model.DeliveryResult;
import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.delivery.model.DeliveryTask;
import dev.hookswarm.delivery.repository.DeadLetterRepository;
import dev.hookswarm.delivery.repository.DeliveryTaskRepository;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

// Don't delete. Scheduled.
@Component
public class DeliveryEngine {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEngine.class);

    private final DeliveryTaskRepository taskRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final DeliveryWorker worker;
    private final RetryPolicy retryPolicy;
    private final CircuitBreakerManager circuitBreakers;
    private final ExecutorService deliveryExecutor;

    private final int batchSize;

    public DeliveryEngine(DeliveryTaskRepository taskRepository,
                          SubscriptionRepository subscriptionRepository,
                          DeadLetterRepository deadLetterRepository,
                          DeliveryWorker worker,
                          RetryPolicy retryPolicy,
                          CircuitBreakerManager circuitBreakers,
                          ExecutorService deliveryExecutor,
                          @Value("${hookswarm.delivery.batch-size:500}") int batchSize) {
        this.taskRepository = taskRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.worker = worker;
        this.retryPolicy = retryPolicy;
        this.circuitBreakers = circuitBreakers;
        this.deliveryExecutor = deliveryExecutor;
        this.batchSize = batchSize;
    }

    //
    // Polling loop
    //

    // - Select due tasks + mark IN_FLIGHT (atomic, locked)
    // - Filter out tasks where circuit breaker is open (revert those to PENDING)
    // - Dispatch remaining to virtual threads (Each virtual thread: deliver -> handle result -> update DB)
    @Scheduled(fixedDelayString = "${hookswarm.delivery.poll-interval-ms:1000}")
    public void poll() {
        Instant now = Instant.now();
        List<DeliveryTask> inFlight = taskRepository.findDueAndMarkInFlight(batchSize, now);
        if (inFlight.isEmpty()) return;

        // Partition by circuit breaker state
        Map<Boolean, List<DeliveryTask>> partitioned = inFlight.stream()
                .collect(Collectors.partitioningBy(
                        task -> !circuitBreakers.isOpen(task.subscriptionId()))
                );

        List<DeliveryTask> deliverable = partitioned.get(true);
        List<DeliveryTask> blocked = partitioned.get(false);

        // Revert blocked tasks back to their previous status, will be picked up when the circuit closes
        if (!blocked.isEmpty()) {
            revertBlockedTasks(blocked, now);
            log.debug("Reverted {} tasks — circuit breaker open", blocked.size());
        }

        if (deliverable.isEmpty()) return;

        // Dispatch to virtual threads, fire and forget
        for (DeliveryTask task : deliverable) {
            deliveryExecutor.submit(() -> executeDelivery(task));
        }

        log.info("Dispatched {} deliveries ({} blocked by circuit breaker)",
                deliverable.size(), blocked.size());
    }

    // Deliver one webhook and handles the result.
    private void executeDelivery(DeliveryTask task) {
        try {
            DeliveryResult result = worker.deliver(task);
            handleResult(task, result);
        } catch (Exception e) {
            // Worker should never throw — but just in case
            log.error("Unexpected error delivering task {}", task.id(), e);
            handleFailure(task, "Unexpected: " + e.getMessage());
        }
    }

    private void handleResult(DeliveryTask task, DeliveryResult result) {
        Instant now = Instant.now();

        if (result.success()) {
            taskRepository.markDelivered(task.id(), now);
            circuitBreakers.recordSuccess(task.subscriptionId());
            log.debug("Task {} delivered successfully", task.id());
        } else {
            circuitBreakers.recordFailure(task.subscriptionId());
            handleFailure(task, result.errorMessage());
        }
    }

    private void handleFailure(DeliveryTask task, String errorMessage) {
        int newAttemptCount = task.attemptCount() + 1;
        Instant now = Instant.now();

        // Look up max retries from the subscription
        int maxRetries = subscriptionRepository.findById(task.subscriptionId())
                .map(Subscription::maxRetries)
                .orElse(5); // safe default if subscription was deleted

        if (newAttemptCount >= maxRetries) {
            // Exhausted retries -> dead letter queue
            taskRepository.markDead(task.id(), newAttemptCount, now);
            deadLetterRepository.insert(new DeadLetterEntry(
                    IdGenerator.newId(),
                    task.id(),
                    task.eventId(),
                    task.subscriptionId(),
                    newAttemptCount,
                    errorMessage,
                    now
            ));
            log.warn("Task {} moved to DLQ after {} attempts: {}",
                    task.id(), newAttemptCount, errorMessage);
        } else {
            // Schedule retry
            Instant nextAttempt = retryPolicy.nextAttemptTime(newAttemptCount);
            taskRepository.markFailed(task.id(), newAttemptCount, nextAttempt, now);
            log.debug("Task {} attempt {} failed, next retry at {}",
                    task.id(), newAttemptCount, nextAttempt);
        }
    }

    // Tasks blocked by circuit breaker were already marked IN_FLIGHT.
    // Revert them to FAILED to preserving attempt count so the next poll after the circuit closes picks them up
    private void revertBlockedTasks(List<DeliveryTask> blocked, Instant now) {
        for (DeliveryTask task : blocked) {
            if (task.attemptCount() == 0) {
                taskRepository.resetToPending(task.id(), task.nextAttemptAt(), now);
            } else {
                taskRepository.markFailed(task.id(), task.attemptCount(),
                        task.nextAttemptAt(), now);
            }
        }
    }

}