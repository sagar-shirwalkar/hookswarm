package dev.hookswarm.delivery.service;

import dev.hookswarm.delivery.model.*;
import dev.hookswarm.delivery.repository.ReactiveDeadLetterRepository;
import dev.hookswarm.delivery.repository.ReactiveDeliveryAttemptRepository;
import dev.hookswarm.delivery.repository.ReactiveDeliveryTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(MockitoExtension.class)
class ReactiveDeliveryServiceTest {

    @Mock
    private ReactiveDeliveryTaskRepository taskRepository;

    @Mock
    private ReactiveDeliveryAttemptRepository attemptRepository;

    @Mock
    private ReactiveDeadLetterRepository deadLetterRepository;

    @InjectMocks
    private ReactiveDeliveryService deliveryService;

    private final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    @Test
    void getTask_ShouldReturnTask() {
        String id = "task1";
        DeliveryTask task = new DeliveryTask(id, "evt1", "sub1", "url", "secret", "{}", DeliveryStatus.PENDING, 0, now, now, now);
        when(taskRepository.findById(id)).thenReturn(Mono.just(task));

        StepVerifier.create(deliveryService.getTask(id))
                .expectNext(task)
                .verifyComplete();

        verify(taskRepository).findById(id);
    }

    @Test
    void getTasksByEventId_ShouldReturnList() {
        String eventId = "evt1";
        DeliveryTask task1 = new DeliveryTask("task1", eventId, "sub1", "url", "secret", "{}", DeliveryStatus.PENDING, 0, now, now, now);
        DeliveryTask task2 = new DeliveryTask("task2", eventId, "sub2", "url", "secret", "{}", DeliveryStatus.DELIVERED, 1, now, now, now);
        when(taskRepository.findByEventId(eventId)).thenReturn(Flux.just(task1, task2));

        StepVerifier.create(deliveryService.getTasksByEventId(eventId))
                .expectNext(task1, task2)
                .verifyComplete();
    }

    @Test
    void getTasksBySubscriptionId_WithPagination_ShouldReturnPagedResponse() {
        String subId = "sub1";
        int page = 0, size = 2;
        DeliveryTask task1 = new DeliveryTask("task1", "evt1", subId, "url", "secret", "{}", DeliveryStatus.PENDING, 0, now, now, now);
        DeliveryTask task2 = new DeliveryTask("task2", "evt1", subId, "url", "secret", "{}", DeliveryStatus.DELIVERED, 1, now, now, now);

        when(taskRepository.findBySubscriptionId(subId)).thenReturn(Flux.just(task1, task2));
        when(taskRepository.count()).thenReturn(Mono.just(2L));

        StepVerifier.create(deliveryService.getTasksBySubscriptionId(subId, page, size))
                .assertNext(response -> {
                    assertThat(response.content()).containsExactly(task1, task2);
                    assertThat(response.page()).isEqualTo(page);
                    assertThat(response.size()).isEqualTo(size);
                    assertThat(response.totalElements()).isEqualTo(2);
                    assertThat(response.totalPages()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void getAttempts_ShouldReturnAttempts() {
        String taskId = "task1";
        DeliveryAttempt attempt1 = new DeliveryAttempt("att1", taskId, 1, 200, "OK", 100L, null, now);
        DeliveryAttempt attempt2 = new DeliveryAttempt("att2", taskId, 2, 500, "error", 50L, "Server error", now);
        when(attemptRepository.findByDeliveryTaskIdOrderByAttemptNumberAsc(taskId)).thenReturn(Flux.just(attempt1, attempt2));

        StepVerifier.create(deliveryService.getAttempts(taskId))
                .expectNext(attempt1, attempt2)
                .verifyComplete();
    }

    @Test
    void retryTask_ShouldCreateNewPendingTask() {
        String oldTaskId = "oldTask";
        DeliveryTask oldTask = new DeliveryTask(oldTaskId, "evt1", "sub1", "url", "secret", "{}", DeliveryStatus.FAILED, 3, now, now, now);
        when(taskRepository.findById(oldTaskId)).thenReturn(Mono.just(oldTask));
        when(taskRepository.save(any(DeliveryTask.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(deliveryService.retryTask(oldTaskId))
                .assertNext(newTask -> {
                    assertThat(newTask.id()).isNotEqualTo(oldTaskId);
                    assertThat(newTask.eventId()).isEqualTo("evt1");
                    assertThat(newTask.subscriptionId()).isEqualTo("sub1");
                    assertThat(newTask.status()).isEqualTo(DeliveryStatus.PENDING);
                    assertThat(newTask.attemptCount()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void listDeadLetters_ShouldReturnPagedResponse() {
        int page = 0, size = 2;
        DeadLetterEntry dead1 = new DeadLetterEntry("dead1", "task1", "evt1", "sub1", 5, "error", now);
        DeadLetterEntry dead2 = new DeadLetterEntry("dead2", "task2", "evt1", "sub1", 3, "timeout", now);
        when(deadLetterRepository.findAllWithPagination(0, size)).thenReturn(Flux.just(dead1, dead2));
        when(deadLetterRepository.count()).thenReturn(Mono.just(2L));

        StepVerifier.create(deliveryService.listDeadLetters(page, size))
                .assertNext(response -> {
                    assertThat(response.content()).containsExactly(dead1, dead2);
                    assertThat(response.totalElements()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void replayDeadLetter_ShouldCreateNewTaskAndDeleteDead() {
        String deadId = "dead1";
        DeadLetterEntry dead = new DeadLetterEntry(deadId, "oldTask", "evt1", "sub1", 5, "error", now);
        when(deadLetterRepository.findById(deadId)).thenReturn(Mono.just(dead));
        when(taskRepository.save(any(DeliveryTask.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(deadLetterRepository.delete(dead)).thenReturn(Mono.empty());

        StepVerifier.create(deliveryService.replayDeadLetter(deadId))
                .assertNext(newTask -> {
                    assertThat(newTask.eventId()).isEqualTo("evt1");
                    assertThat(newTask.subscriptionId()).isEqualTo("sub1");
                    assertThat(newTask.status()).isEqualTo(DeliveryStatus.PENDING);
                })
                .verifyComplete();

        verify(deadLetterRepository).delete(dead);
    }
}