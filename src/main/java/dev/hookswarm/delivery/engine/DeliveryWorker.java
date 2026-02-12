package dev.hookswarm.delivery.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hookswarm.common.IdGenerator;
import dev.hookswarm.delivery.signing.WebhookSigner;
import dev.hookswarm.delivery.model.DeliveryAttempt;
import dev.hookswarm.delivery.model.DeliveryResult;
import dev.hookswarm.delivery.model.DeliveryTask;
import dev.hookswarm.delivery.repository.DeliveryAttemptRepository;
import dev.hookswarm.event.model.Event;
import dev.hookswarm.event.repository.EventRepository;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@Component
public class DeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);
    private static final int MAX_RESPONSE_BODY_LENGTH = 1024;

    private final HttpClient httpClient;
    private final WebhookSigner signer;
    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final Duration requestTimeout;

    private final ObjectMapper objectMapper;

    public DeliveryWorker(HttpClient webhookHttpClient,
                          WebhookSigner signer,
                          EventRepository eventRepository,
                          SubscriptionRepository subscriptionRepository,
                          DeliveryAttemptRepository attemptRepository,
                          ObjectMapper objectMapper,
                          @Value("${hookswarm.delivery.timeout-request-seconds:10}") long requestTimeoutSeconds) {
        this.httpClient = webhookHttpClient;
        this.signer = signer;
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.attemptRepository = attemptRepository;
        this.objectMapper = objectMapper;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
    }

    private String buildPayload(Event event) {
        try {
            var envelope = new java.util.LinkedHashMap<String, Object>();
            envelope.put("id", event.id());
            envelope.put("type", event.eventType());
            envelope.put("timestamp", event.createdAt().toString());
            envelope.put("data", objectMapper.readTree(event.payload()));
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build webhook payload", e);
        }
    }

    // Delivers a single webhook. Always records a DeliveryAttempt, failures are captured in the DeliveryResult
    public DeliveryResult deliver(DeliveryTask task) {
        int attemptNumber = task.attemptCount() + 1;
        Instant start = Instant.now();

        // Resolve event and subscription
        Event event = eventRepository.findById(task.eventId()).orElse(null);
        if (event == null) {
            Duration latency = Duration.between(start, Instant.now());
            recordAttempt(task.id(), attemptNumber, 0, null, latency,
                    "Event not found: " + task.eventId());
            return DeliveryResult.error(latency, "Event not found: " + task.eventId());
        }

        Subscription sub = subscriptionRepository.findById(task.subscriptionId()).orElse(null);
        if (sub == null) {
            Duration latency = Duration.between(start, Instant.now());
            recordAttempt(task.id(), attemptNumber, 0, null, latency,
                    "Subscription not found: " + task.subscriptionId());
            return DeliveryResult.error(latency,
                    "Subscription not found: " + task.subscriptionId());
        }

        //
        // Build and send payload
        //

        String body = buildPayload(event);
        String signature = signer.sign(body, sub.secret());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sub.url()))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("X-HookSwarm-Signature", signature)
                .header("X-HookSwarm-Event-Type", event.eventType())
                .header("X-HookSwarm-Delivery-Id", task.id())
                .header("X-HookSwarm-Timestamp", Instant.now().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            Duration latency = Duration.between(start, Instant.now());
            boolean success = response.statusCode() >= 200
                    && response.statusCode() < 300;

            recordAttempt(task.id(), attemptNumber, response.statusCode(),
                    truncate(response.body()), latency,
                    success ? null : "HTTP " + response.statusCode());

            log.debug("Delivery {} attempt #{}: {} {}ms",
                    task.id(), attemptNumber,
                    success ? "SUCCESS" : "FAILED(" + response.statusCode() + ")",
                    latency.toMillis());

            return success
                    ? DeliveryResult.success(response.statusCode(), latency)
                    : DeliveryResult.failure(response.statusCode(), latency,
                    "HTTP " + response.statusCode());

        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());

            recordAttempt(task.id(), attemptNumber, 0, null, latency,
                    e.getClass().getSimpleName() + ": " + e.getMessage());

            log.debug("Delivery {} attempt #{}: ERROR {}ms — {}",
                    task.id(), attemptNumber, latency.toMillis(), e.getMessage());

            return DeliveryResult.error(latency,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void recordAttempt(String taskId, int attemptNumber,
                               int statusCode, String responseBody,
                               Duration latency, String errorMessage) {
        attemptRepository.insert(new DeliveryAttempt(
                IdGenerator.newId(),
                taskId,
                attemptNumber,
                statusCode,
                responseBody,
                latency,
                errorMessage,
                Instant.now()
        ));
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > MAX_RESPONSE_BODY_LENGTH
                ? s.substring(0, MAX_RESPONSE_BODY_LENGTH) + "...[truncated]"
                : s;
    }

}