package dev.hookswarm.delivery.repository;

import dev.hookswarm.delivery.model.DeliveryAttempt;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ReactiveDeliveryAttemptRepository extends ReactiveCrudRepository<DeliveryAttempt, String> {

    Flux<DeliveryAttempt> findByDeliveryTaskIdOrderByAttemptNumberAsc(String deliveryTaskId);

}