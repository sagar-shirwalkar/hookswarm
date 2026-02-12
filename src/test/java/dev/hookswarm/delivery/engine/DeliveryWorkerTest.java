package dev.hookswarm.delivery.engine;


import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hookswarm.TestFixtures;
import dev.hookswarm.delivery.model.DeliveryAttempt;
import dev.hookswarm.delivery.model.DeliveryResult;
import dev.hookswarm.delivery.model.DeliveryTask;
import dev.hookswarm.delivery.repository.DeliveryAttemptRepository;
import dev.hookswarm.delivery.signing.WebhookSigner;
import dev.hookswarm.event.model.Event;
import dev.hookswarm.event.repository.EventRepository;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryWorkerTest {

    @Mock
    HttpClient httpClient;
    @Mock
    HttpResponse<String> httpResponse;
    @Mock
    EventRepository eventRepository;
    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    DeliveryAttemptRepository attemptRepository;

    private DeliveryWorker worker;
    private final WebhookSigner signer = new WebhookSigner();

    @Mock
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        worker = new DeliveryWorker(
                httpClient, signer, eventRepository,
                subscriptionRepository, attemptRepository,
                objectMapper, 10);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deliver_successOn2xx() throws Exception {
        stubObjectMapper();
        DeliveryTask task = TestFixtures.pendingTask();
        Event event = TestFixtures.event();
        Subscription sub = TestFixtures.subscription();

        when(eventRepository.findById("evt_01")).thenReturn(Optional.of(event));
        when(subscriptionRepository.findById("sub_01")).thenReturn(Optional.of(sub));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"ok\":true}");

        DeliveryResult result = worker.deliver(task);

        assertThat(result.success()).isTrue();
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.latency()).isNotNull();
        assertThat(result.errorMessage()).isNull();

        // Verify attempt was recorded
        ArgumentCaptor<DeliveryAttempt> captor =
                ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptRepository).insert(captor.capture());

        DeliveryAttempt attempt = captor.getValue();
        assertThat(attempt.deliveryTaskId()).isEqualTo("task_01");
        assertThat(attempt.attemptNumber()).isEqualTo(1);
        assertThat(attempt.httpStatusCode()).isEqualTo(200);
        assertThat(attempt.errorMessage()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deliver_failureOnServerError() throws Exception {
        stubObjectMapper();
        DeliveryTask task = TestFixtures.pendingTask();
        Event event = TestFixtures.event();
        Subscription sub = TestFixtures.subscription();

        when(eventRepository.findById("evt_01")).thenReturn(Optional.of(event));
        when(subscriptionRepository.findById("sub_01")).thenReturn(Optional.of(sub));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("Internal Server Error");

        DeliveryResult result = worker.deliver(task);

        assertThat(result.success()).isFalse();
        assertThat(result.httpStatusCode()).isEqualTo(500);
        assertThat(result.errorMessage()).isEqualTo("HTTP 500");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deliver_failureOn4xx() throws Exception {
        stubObjectMapper();
        DeliveryTask task = TestFixtures.pendingTask();

        when(eventRepository.findById("evt_01")).thenReturn(Optional.of(TestFixtures.event()));
        when(subscriptionRepository.findById("sub_01")).thenReturn(Optional.of(TestFixtures.subscription()));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(404);
        when(httpResponse.body()).thenReturn("Not Found");

        DeliveryResult result = worker.deliver(task);

        assertThat(result.success()).isFalse();
        assertThat(result.httpStatusCode()).isEqualTo(404);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deliver_handlesConnectionError() throws Exception {
        stubObjectMapper();
        DeliveryTask task = TestFixtures.pendingTask();

        when(eventRepository.findById("evt_01")).thenReturn(Optional.of(TestFixtures.event()));
        when(subscriptionRepository.findById("sub_01")).thenReturn(Optional.of(TestFixtures.subscription()));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        DeliveryResult result = worker.deliver(task);

        assertThat(result.success()).isFalse();
        assertThat(result.httpStatusCode()).isEqualTo(0);
        assertThat(result.errorMessage()).contains("IOException", "Connection refused");
    }

    @Test
    void deliver_eventNotFound() {
        DeliveryTask task = TestFixtures.pendingTask();
        when(eventRepository.findById("evt_01")).thenReturn(Optional.empty());

        DeliveryResult result = worker.deliver(task);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Event not found");
        verify(httpClient, never()).sendAsync(any(), any());
    }

    @Test
    void deliver_subscriptionNotFound() {
        DeliveryTask task = TestFixtures.pendingTask();
        when(eventRepository.findById("evt_01")).thenReturn(Optional.of(TestFixtures.event()));
        when(subscriptionRepository.findById("sub_01")).thenReturn(Optional.empty());

        DeliveryResult result = worker.deliver(task);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Subscription not found");
    }

    @Test
    void deliver_recordsAttemptOnConnectionError() throws Exception {
        stubObjectMapper();
        DeliveryTask task = TestFixtures.failedTask(2); // already failed twice

        when(eventRepository.findById("evt_01")).thenReturn(Optional.of(TestFixtures.event()));
        when(subscriptionRepository.findById("sub_01")).thenReturn(Optional.of(TestFixtures.subscription()));
        when(httpClient.send(any(), any())).thenThrow(new IOException("timeout"));

        worker.deliver(task);

        ArgumentCaptor<DeliveryAttempt> captor =
                ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptRepository).insert(captor.capture());

        DeliveryAttempt attempt = captor.getValue();
        assertThat(attempt.attemptNumber()).isEqualTo(3); // attemptCount(2) + 1
        assertThat(attempt.httpStatusCode()).isEqualTo(0);
        assertThat(attempt.errorMessage()).contains("timeout");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deliver_sendsHmacSignatureHeader() throws Exception {
        stubObjectMapper();
        DeliveryTask task = TestFixtures.pendingTask();

        when(eventRepository.findById("evt_01")).thenReturn(Optional.of(TestFixtures.event()));
        when(subscriptionRepository.findById("sub_01")).thenReturn(Optional.of(TestFixtures.subscription()));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("ok");

        worker.deliver(task);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        HttpRequest sent = captor.getValue();
        assertThat(sent.headers().firstValue("X-HookSwarm-Signature"))
                .isPresent()
                .get().asString().startsWith("sha256=");
        assertThat(sent.headers().firstValue("X-HookSwarm-Event-Type"))
                .isPresent()
                .get().asString().isEqualTo("order.created");
        assertThat(sent.headers().firstValue("X-HookSwarm-Delivery-Id"))
                .isPresent()
                .get().asString().isEqualTo("task_01");
    }

    private void stubObjectMapper() throws Exception {
        // When readTree is called with any string, return a valid JsonNode
        com.fasterxml.jackson.databind.JsonNode mockNode =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree("{\"orderId\":\"ORD-123\",\"amount\":99.99}");
        when(objectMapper.readTree(anyString())).thenReturn(mockNode);

        // When writeValueAsString is called, return a valid JSON string
        when(objectMapper.writeValueAsString(any())).thenReturn(
                "{\"id\":\"evt_01\",\"type\":\"order.created\",\"timestamp\":\"2025-01-15T10:00:00Z\",\"data\":{\"orderId\":\"ORD-123\"}}");
    }

}