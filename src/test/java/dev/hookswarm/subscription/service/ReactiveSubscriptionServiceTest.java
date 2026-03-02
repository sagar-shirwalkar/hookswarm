package dev.hookswarm.subscription.service;

import dev.hookswarm.subscription.model.CreateSubscriptionRequest;
import dev.hookswarm.subscription.model.UpdateSubscriptionRequest;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import dev.hookswarm.subscription.repository.ReactiveSubscriptionRepository;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactiveSubscriptionServiceTest {

    @Mock
    private ReactiveSubscriptionRepository repository;

    @InjectMocks
    private ReactiveSubscriptionService subscriptionService;

    private final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    @Test
    void create_ShouldSaveSubscription() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                "http://test.com", "secret123", Set.of("user.created"), 5
        );
        when(repository.save(any(Subscription.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(subscriptionService.create(request))
                .assertNext(sub -> {
                    assertThat(sub.url()).isEqualTo("http://test.com");
                    assertThat(sub.secret()).isEqualTo("secret123");
                    assertThat(sub.eventTypes()).containsExactly("user.created");
                    assertThat(sub.status()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(sub.maxRetries()).isEqualTo(5);
                    assertThat(sub.id()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void getById_ShouldReturnSubscription() {
        String id = "sub1";
        Subscription sub = new Subscription(id, "http://test.com", "secret", Set.of("user.created"), SubscriptionStatus.ACTIVE, 5, now, now);
        when(repository.findById(id)).thenReturn(Mono.just(sub));

        StepVerifier.create(subscriptionService.getById(id))
                .expectNext(sub)
                .verifyComplete();
    }

    @Test
    void getAllActive_ShouldReturnActiveSubscriptions() {
        Subscription sub1 = new Subscription("sub1", "url1", "secret1", Set.of("user.created"), SubscriptionStatus.ACTIVE, 5, now, now);
        Subscription sub2 = new Subscription("sub2", "url2", "secret2", Set.of("invoice.paid"), SubscriptionStatus.ACTIVE, 3, now, now);
        when(repository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(Flux.just(sub1, sub2));

        StepVerifier.create(subscriptionService.getAllActive())
                .expectNext(sub1, sub2)
                .verifyComplete();
    }

    @Test
    void getActiveByEventType_ShouldReturnMatchingSubscriptions() {
        String eventType = "user.created";
        Subscription sub1 = new Subscription("sub1", "url1", "secret1", Set.of("user.created"), SubscriptionStatus.ACTIVE, 5, now, now);
        Subscription sub2 = new Subscription("sub2", "url2", "secret2", Set.of(), SubscriptionStatus.ACTIVE, 5, now, now); // wildcard
        when(repository.findActiveByEventType(eventType)).thenReturn(Flux.just(sub1, sub2));

        StepVerifier.create(subscriptionService.getActiveByEventType(eventType))
                .expectNext(sub1, sub2)
                .verifyComplete();
    }

    @Test
    void update_ShouldModifySubscription() {
        String id = "sub1";
        Subscription existing = new Subscription(id, "oldUrl", "oldSecret", Set.of("user.created"), SubscriptionStatus.ACTIVE, 5, now, now);
        UpdateSubscriptionRequest request = new UpdateSubscriptionRequest(
                "newUrl", "newSecret", Set.of("user.updated"), SubscriptionStatus.PAUSED, 10
        );
        when(repository.findById(id)).thenReturn(Mono.just(existing));
        when(repository.save(any(Subscription.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(subscriptionService.update(id, request))
                .assertNext(updated -> {
                    assertThat(updated.url()).isEqualTo("newUrl");
                    assertThat(updated.secret()).isEqualTo("newSecret");
                    assertThat(updated.eventTypes()).containsExactly("user.updated");
                    assertThat(updated.status()).isEqualTo(SubscriptionStatus.PAUSED);
                    assertThat(updated.maxRetries()).isEqualTo(10);
                })
                .verifyComplete();
    }

    @Test
    void delete_ShouldRemoveSubscription() {
        String id = "sub1";
        when(repository.deleteById(id)).thenReturn(Mono.empty());

        StepVerifier.create(subscriptionService.delete(id))
                .verifyComplete();

        verify(repository).deleteById(id);
    }
}