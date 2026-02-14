package dev.hookswarm.delivery.service;

import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.common.UlidGenerator;
import dev.hookswarm.delivery.model.*;
import dev.hookswarm.delivery.repository.ReactiveDeadLetterRepository;
import dev.hookswarm.delivery.repository.ReactiveDeliveryAttemptRepository;
import dev.hookswarm.delivery.repository.ReactiveDeliveryTaskRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class ReactiveDeliveryService {

    private final ReactiveDeliveryTaskRepository taskRepository;
    private final ReactiveDeliveryAttemptRepository attemptRepository;
    private final ReactiveDeadLetterRepository deadLetterRepository;

    public ReactiveDeliveryService(ReactiveDeliveryTaskRepository taskRepository,
                                   ReactiveDeliveryAttemptRepository attemptRepository,
                                   ReactiveDeadLetterRepository deadLetterRepository) {
        this.taskRepository = taskRepository;
        this.attemptRepository = attemptRepository;
        this.deadLetterRepository = deadLetterRepository;
    }

    // ---------- Delivery Tasks ----------
    public Mono<DeliveryTask> getTask(String id) {
        return taskRepository.findById(id);
    }

    public Flux<DeliveryTask> getTasksByEventId(String eventId) {
        return taskRepository.findByEventId(eventId);
    }

    public Mono<PagedResponse<DeliveryTask>> getTasksBySubscriptionId(String subscriptionId, int page, int size) {
        return taskRepository.findBySubscriptionId(subscriptionId)
                .skip((long) page * size)
                .take(size)
                .collectList()
                .zipWith(taskRepository.count())
                .map(tuple -> new PagedResponse<>(
                        tuple.getT1(),
                        page,
                        size,
                        tuple.getT2(),
                        (int) Math.ceil((double) tuple.getT2() / size)
                ));
    }

    public Flux<DeliveryAttempt> getAttempts(String taskId) {
        return attemptRepository.findByDeliveryTaskIdOrderByAttemptNumberAsc(taskId);
    }

    public Mono<DeliveryTask> retryTask(String id) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return taskRepository.findById(id)
                .flatMap(task -> {
                    DeliveryTask retryTask = new DeliveryTask(
                            UlidGenerator.newUlid(),
                            task.eventId(),
                            task.subscriptionId(),
                            task.url(),
                            task.secret(),
                            task.payload(),
                            DeliveryStatus.PENDING,
                            0,
                            now.plusMinutes(1), // retry soon
                            now,
                            now
                    );
                    return taskRepository.save(retryTask);
                });
    }

    // ---------- Dead Letter Queue ----------
    public Mono<PagedResponse<DeadLetterEntry>> listDeadLetters(int page, int size) {
        return deadLetterRepository.findAllWithPagination((long) page * size, size)
                .collectList()
                .zipWith(deadLetterRepository.count())
                .map(tuple -> new PagedResponse<>(
                        tuple.getT1(),
                        page,
                        size,
                        tuple.getT2(),
                        (int) Math.ceil((double) tuple.getT2() / size)
                ));
    }

    public Mono<DeliveryTask> replayDeadLetter(String id) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return deadLetterRepository.findById(id)
                .flatMap(dead -> {
                    DeliveryTask newTask = new DeliveryTask(
                            UlidGenerator.newUlid(),
                            dead.eventId(),
                            dead.subscriptionId(),
                            "", // url? not stored in DLQ, need to fetch from subscription!
                            "", // secret
                            "", // payload
                            DeliveryStatus.PENDING,
                            0,
                            now.plusMinutes(1),
                            now,
                            now
                    );
                    return taskRepository.save(newTask)
                            .flatMap(savedTask -> deadLetterRepository.delete(dead).thenReturn(savedTask));
                });
    }

}