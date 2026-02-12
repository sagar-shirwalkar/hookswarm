package dev.hookswarm.outbox;

import dev.hookswarm.common.IdGenerator;
import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.delivery.model.DeliveryTask;
import dev.hookswarm.delivery.repository.DeliveryTaskRepository;
import dev.hookswarm.outbox.repository.OutboxRepository;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Don't delete. Scheduled.
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DeliveryTaskRepository deliveryTaskRepository;
    private final int batchSize;

    public OutboxPoller(OutboxRepository outboxRepository,
                        SubscriptionRepository subscriptionRepository,
                        DeliveryTaskRepository deliveryTaskRepository,
                        @Value("${hookswarm.outbox.batch-size:100}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryTaskRepository = deliveryTaskRepository;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${hookswarm.outbox.poll-interval-ms:500}")
    @Transactional
    public void poll() {
        List<OutboxEntry> entries = outboxRepository.findUnprocessedForUpdate(batchSize);
        if (entries.isEmpty()) return;

        Instant now = Instant.now();

        // Group by event type so we query subscriptions once per unique type, not once per outbox entry
        // e.g. 100 entries with 3 unique types = 3 queries.
        Map<String, List<OutboxEntry>> byEventType = entries.stream()
                .collect(Collectors.groupingBy(OutboxEntry::eventType));

        List<DeliveryTask> tasks = byEventType.entrySet().stream()
                .flatMap(group -> {
                    String eventType = group.getKey();
                    List<OutboxEntry> groupEntries = group.getValue();

                    List<Subscription> matching =
                            subscriptionRepository.findActiveByEventType(eventType);

                    if (matching.isEmpty()) {
                        log.debug("No matching subscriptions for event type '{}'", eventType);
                        return java.util.stream.Stream.empty();
                    }

                    return groupEntries.stream()
                            .flatMap(entry -> matching.stream()
                                    .map(sub -> new DeliveryTask(
                                            IdGenerator.newId(),
                                            entry.eventId(),
                                            sub.id(),
                                            DeliveryStatus.PENDING,
                                            0,
                                            now,
                                            now,
                                            now
                                    )));
                })
                .toList();

        if (!tasks.isEmpty()) {
            deliveryTaskRepository.insertBatch(tasks);
        }

        markProcessed(entries);

        log.info("Processed {} outbox entries ({} event types) -> {} delivery tasks",
                entries.size(), byEventType.size(), tasks.size()
        );
    }

    private void markProcessed(List<OutboxEntry> entries) {
        List<String> ids = entries.stream().map(OutboxEntry::id).toList();
        outboxRepository.markProcessed(ids, Instant.now());
    }

}