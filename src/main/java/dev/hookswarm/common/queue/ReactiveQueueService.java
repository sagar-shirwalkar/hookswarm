package dev.hookswarm.common.queue;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

public interface ReactiveQueueService {

    Mono<String> publish(String stream, Map<String, String> body);

    Mono<Void> createGroup(String stream, String group);

    Flux<QueueMessage> read(String group, String consumer, String stream, int count, Duration block);

    Mono<Void> ack(String stream, String group, String messageId);

    Mono<Void> deadLetter(String fromStream, String messageId, Map<String, String> body, Throwable error);

    Mono<Long> streamLength(String stream);

    Mono<Map<String, Object>> pendingSummary(String stream, String group);
}