package dev.hookswarm.subscription.service;


import dev.hookswarm.TestFixtures;
import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.common.exception.ResourceNotFoundException;
import dev.hookswarm.subscription.dto.CreateSubscriptionRequest;
import dev.hookswarm.subscription.dto.SubscriptionResponse;
import dev.hookswarm.subscription.dto.UpdateSubscriptionRequest;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import dev.hookswarm.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    SubscriptionRepository repository;

    @InjectMocks
    SubscriptionService service;

    @Test
    void create_insertsSubscriptionWithGeneratedIdAndSecret() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                "https://example.com/hook", Set.of("order.created"), 3);

        Subscription result = service.create(request);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).insert(captor.capture());

        Subscription saved = captor.getValue();
        assertThat(saved.id()).isNotBlank().hasSize(26);
        assertThat(saved.secret()).startsWith("hsw_");
        assertThat(saved.url()).isEqualTo("https://example.com/hook");
        assertThat(saved.eventTypes()).containsExactly("order.created");
        assertThat(saved.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.maxRetries()).isEqualTo(3);
        assertThat(result).isEqualTo(saved);
    }

    @Test
    void create_usesDefaultMaxRetries() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                "https://example.com/hook", null, null);

        service.create(request);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().maxRetries()).isEqualTo(5);
    }

    @Test
    void create_emptyEventTypesDefaultsToWildcard() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                "https://example.com/hook", null, null);

        service.create(request);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().eventTypes()).isEmpty();
    }

    @Test
    void getById_returnsSubscription() {
        Subscription sub = TestFixtures.subscription();
        when(repository.findById("sub_01")).thenReturn(Optional.of(sub));

        Subscription result = service.getById("sub_01");

        assertThat(result).isEqualTo(sub);
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void list_returnsPaginatedResponse() {
        Subscription sub = TestFixtures.subscription();
        when(repository.findAll(20, 0)).thenReturn(List.of(sub));
        when(repository.count()).thenReturn(1L);

        PagedResponse<SubscriptionResponse> response = service.list(0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.page()).isEqualTo(0);
    }

    @Test
    void list_calculatesOffsetFromPage() {
        when(repository.findAll(10, 20)).thenReturn(List.of());
        when(repository.count()).thenReturn(0L);

        service.list(2, 10); // page 2 * size 10 = offset 20

        verify(repository).findAll(10, 20);
    }

    @Test
    void update_appliesPartialChanges() {
        Subscription existing = TestFixtures.subscription();
        when(repository.findById("sub_01")).thenReturn(Optional.of(existing));

        UpdateSubscriptionRequest request = new UpdateSubscriptionRequest(
                null, null, SubscriptionStatus.PAUSED, null);

        Subscription result = service.update("sub_01", request);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).update(captor.capture());

        Subscription updated = captor.getValue();
        assertThat(updated.status()).isEqualTo(SubscriptionStatus.PAUSED);
        assertThat(updated.url()).isEqualTo(existing.url());        // unchanged
        assertThat(updated.maxRetries()).isEqualTo(existing.maxRetries()); // unchanged
    }

    @Test
    void update_appliesNewUrl() {
        Subscription existing = TestFixtures.subscription();
        when(repository.findById("sub_01")).thenReturn(Optional.of(existing));

        UpdateSubscriptionRequest request = new UpdateSubscriptionRequest(
                "https://new.example.com/hook", null, null, null);

        service.update("sub_01", request);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).update(captor.capture());
        assertThat(captor.getValue().url()).isEqualTo("https://new.example.com/hook");
    }

    @Test
    void update_appliesNewEventTypes() {
        Subscription existing = TestFixtures.subscription();
        when(repository.findById("sub_01")).thenReturn(Optional.of(existing));

        UpdateSubscriptionRequest request = new UpdateSubscriptionRequest(
                null, Set.of("order.cancelled"), null, null);

        service.update("sub_01", request);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).update(captor.capture());
        assertThat(captor.getValue().eventTypes()).containsExactly("order.cancelled");
    }

    @Test
    void update_rejectsEmptyUpdate() {
        UpdateSubscriptionRequest request = new UpdateSubscriptionRequest(
                null, null, null, null);

        assertThatThrownBy(() -> service.update("sub_01", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No fields to update");

        verify(repository, never()).findById(any());
    }

    @Test
    void update_throwsWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        UpdateSubscriptionRequest request = new UpdateSubscriptionRequest(
                "https://x.com", null, null, null);

        assertThatThrownBy(() -> service.update("missing", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_succeeds() {
        when(repository.deleteById("sub_01")).thenReturn(true);

        service.delete("sub_01");

        verify(repository).deleteById("sub_01");
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(repository.deleteById("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.delete("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

}