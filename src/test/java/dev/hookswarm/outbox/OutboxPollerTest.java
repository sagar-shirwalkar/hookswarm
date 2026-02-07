package dev.hookswarm.outbox;


import dev.hookswarm.TestFixtures;
import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.delivery.model.DeliveryTask;
import dev.hookswarm.delivery.repository.DeliveryTaskRepository;
import dev.hookswarm.outbox.repository.OutboxRepository;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    OutboxRepository outboxRepository;
    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    DeliveryTaskRepository deliveryTaskRepository;

    private OutboxPoller poller;

    @BeforeEach
    void setUp() {
        poller = new OutboxPoller(outboxRepository, subscriptionRepository, deliveryTaskRepository, 100);
    }

    @Test
    void poll_noUnprocessedEntries_doesNothing() {
        when(outboxRepository.findUnprocessedForUpdate(anyInt())).thenReturn(List.of());

        poller.poll();

        verifyNoInteractions(subscriptionRepository);
        verifyNoInteractions(deliveryTaskRepository);
    }

    @Test
    void poll_fansOutToMatchingSubscriptions() {
        OutboxEntry entry = TestFixtures.outboxEntry(); // type: order.created
        Subscription sub = TestFixtures.subscription(); // listens: order.created

        when(outboxRepository.findUnprocessedForUpdate(anyInt()))
                .thenReturn(List.of(entry));
        when(subscriptionRepository.findActiveByEventType("order.created"))
                .thenReturn(List.of(sub));

        poller.poll();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DeliveryTask>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(deliveryTaskRepository).insertBatch(captor.capture());

        List<DeliveryTask> tasks = captor.getValue();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().eventId()).isEqualTo("evt_01");
        assertThat(tasks.getFirst().subscriptionId()).isEqualTo("sub_01");
        assertThat(tasks.getFirst().status()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void poll_fansOutToMultipleSubscriptions() {
        OutboxEntry entry = TestFixtures.outboxEntry();
        Subscription sub1 = TestFixtures.subscription();
        Subscription sub2 = TestFixtures.wildcardSubscription();

        when(outboxRepository.findUnprocessedForUpdate(anyInt()))
                .thenReturn(List.of(entry));
        when(subscriptionRepository.findActiveByEventType("order.created"))
                .thenReturn(List.of(sub1, sub2));

        poller.poll();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DeliveryTask>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(deliveryTaskRepository).insertBatch(captor.capture());

        List<DeliveryTask> tasks = captor.getValue();
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(DeliveryTask::subscriptionId)
                .containsExactlyInAnyOrder("sub_01", "sub_02");
    }

    @Test
    void poll_noMatchingSubscriptions_stillMarksProcessed() {
        OutboxEntry entry = new OutboxEntry(
                "outbox_01", "evt_01", "some.unknown.type",
                false, Instant.now(), null);

        when(outboxRepository.findUnprocessedForUpdate(anyInt()))
                .thenReturn(List.of(entry));
        when(subscriptionRepository.findActiveByEventType("some.unknown.type"))
                .thenReturn(List.of());

        poller.poll();

        verify(deliveryTaskRepository, never()).insertBatch(any());
        verify(outboxRepository).markProcessed(eq(List.of("outbox_01")), any());
    }

    @Test
    void poll_multipleEventTypes_queriesSubscriptionsPerType() {
        OutboxEntry orderEntry = TestFixtures.outboxEntry(); // order.created
        OutboxEntry userEntry = new OutboxEntry(
                "outbox_02", "evt_02", "user.signed_up",
                false, Instant.now(), null);

        when(outboxRepository.findUnprocessedForUpdate(anyInt()))
                .thenReturn(List.of(orderEntry, userEntry));
        when(subscriptionRepository.findActiveByEventType("order.created"))
                .thenReturn(List.of(TestFixtures.subscription()));
        when(subscriptionRepository.findActiveByEventType("user.signed_up"))
                .thenReturn(List.of(TestFixtures.wildcardSubscription()));

        poller.poll();

        // Two separate queries, one per event type
        verify(subscriptionRepository).findActiveByEventType("order.created");
        verify(subscriptionRepository).findActiveByEventType("user.signed_up");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DeliveryTask>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(deliveryTaskRepository).insertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void poll_marksAllEntriesProcessed() {
        OutboxEntry e1 = TestFixtures.outboxEntry();
        OutboxEntry e2 = new OutboxEntry(
                "outbox_02", "evt_02", "order.created",
                false, Instant.now(), null);

        when(outboxRepository.findUnprocessedForUpdate(anyInt()))
                .thenReturn(List.of(e1, e2));
        when(subscriptionRepository.findActiveByEventType("order.created"))
                .thenReturn(List.of(TestFixtures.subscription()));

        poller.poll();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> idsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).markProcessed(idsCaptor.capture(), any());
        assertThat(idsCaptor.getValue()).containsExactly("outbox_01", "outbox_02");
    }

}