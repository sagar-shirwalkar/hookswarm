# **HookSwarm**

HookSwarm is a fast Webhook Delivery engine which runs Event-Hooks en masse with Java 21's Virtual Threads, ensuring critical message delivery to subscribers at scale.

HookSwarm is built on a fully reactive stack: Spring WebFlux, R2DBC, and DragonflyDB (a high‑performance, Redis‑compatible in‑memory store). This architecture eliminates thread blocking and allows it to handle high concurrency with minimal resources, without the operational complexity of managing Kafka and massive streaming clusters.

---

## **Batteries-included features**

- Ingestion: HookSwarm publishes events to a Dragonfly "Ingest" stream, while simultaneously running Idempotent event-related persistence on PostgreSQL.

- Fan-out with reactive consumers reading the Ingest steam, while checking active subscriptions with R2DBC queries. Message published to a second Dragonfly "Delivery" stream.

- POST requests to subscribers generated with Spring's non-blocking WebClient, by yet another reactive consumer picking up messages from the Deliveries stream. All attempts are recorded, with automatic exponential-backoff retries with jitter and Dead Letter Queue for failures.

- Resilience: All system operations including event processing, delivery, circuit-breaking, retry queues and the DLQ are non-blocking. HookSwarm stays responsive even when downstream services are stressed or failing.

- Monitoring Dashboard: Work in Progress

- Metrics and Observability: Built-in with Spring Actuator, to be added as a dashboard

- Records and Audit support: Configurable retention policies, supporting audit trails and compliance needs.
