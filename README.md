# **HookSwarm**

HookSwarm is a Java 21 + Spring Boot webhook delivery engine designed for reliable, scalable fan-out with strong persistence guarantees.

**TO put it simply:** HookSwarm is the "postman" for your software ecosystem. When something important happens in your system (an *Event*), HookSwarm ensures that message is delivered to everyone who needs to know (the *Subscribers*), even if their systems are slow, crashing, or temporarily offline.

It is a robust, "batteries-included" solution for mid-sized SaaS companies that need dependable webhook delivery without the operational complexity of managing Kafka or massive streaming clusters.

---

## **Why do we need this? (The "Product" View)**

Building a webhook system sounds easy: *"Just make an HTTP POST when the user clicks buy."*

But in the real world, this breaks quickly:

- **What if the receiver is down?** You need to retry, but not immediately (or you'll DDoS them). You need *Exponential Backoff*.
- **What if 5,000 events happen at once?** Your system might crash trying to send them all. You need *Queueing* and *Throttling*.
- **What if the receiver is slow?** Your main API will hang waiting for them. You need *Async Processing*.
- **How do we know if they got it?** You need *Delivery Tracking* and *Logs*.

HookSwarm solves these infrastructure problems so your team can focus on building features.

---

## **Real World Scenario: The Payment Gateway**

Imagine you are building a payment platform like Stripe or PayPal.

1. **The Trigger**: A shopper buys a coffee for $5.00. Your backend records the payment.
2. **The Problem**: You need to notify the coffee shop's inventory system instantly so they can start brewing.
3. **The HookSwarm Solution**:
   - Your backend sends an **Event** to HookSwarm: `payment.received` for $5.00.
   - HookSwarm checks its **Subscriptions**: "Who cares about payments?" -> *The Coffee Shop API*.
   - HookSwarm attempts **Delivery**.
   - **Scenario A (Success)**: The Coffee Shop API says "200 OK". HookSwarm marks it `DELIVERED`.
   - **Scenario B (Failure)**: The Coffee Shop API is down (500 Error).
     - HookSwarm waits 10 seconds. Retries.
     - Still down? Waits 30 seconds. Retries.
     - Still down? Waits 5 minutes. Retries.
     - After 5 hours of failure, it moves the task to the **Dead Letter Queue (DLQ)** and alerts your support team.

Result: No orders are lost, and your system never crashes just because a coffee shop's Wi-Fi is down.

---

## **Performance Profile**

For engineering teams evaluating fit:

- **Throughput**: 1–5K requests/second (Vertical scaling friendly)
- **Latency**: P50 ~200ms, P99 ~1500ms
- **Reliability**: Strong consistency via PostgreSQL-backed queue
- **Stack**: Java 21 (Virtual Threads), Spring Boot 3, PostgreSQL

It is not designed for "Twitter-scale" firehose streaming, but it is fast, durable, and operationally simple for 99% of B2B SaaS workloads.

---

## **APIs & Resources**

### **1. Events**

*The "What Happened". Immutable records of system activity.*

- `POST /api/v1/events` - **Ingest Event**: "Something happened!" (e.g., `user.created`, `invoice.paid`). Idempotent keys prevent duplicates.
- `GET /api/v1/events/{id}` - **Verify**: Did we receive the event?

### **2. Subscriptions**

*The "Who Cares". Rules determining who gets notified.*

- `POST /api/v1/subscriptions` - **Subscribe**: "Tell `https://api.acme.com` whenever `invoice.paid` happens."
- `PATCH /api/v1/subscriptions/{id}` - **Modify**: Pause notifications or rotate secrets.

### **3. Deliveries**

*The "Tracking Number". The individual attempt to send one event to one subscriber.*

- `GET /api/v1/deliveries/{id}` - **Track**: What is the status of this specific delivery? (PENDING, IN_FLIGHT, DELIVERED, FAILED)
- `GET /api/v1/deliveries/{id}/attempts` - **Audit**: See the exact HTTP response body and status code for every retry attempt. Useful for debugging customer issues.
- `POST /api/v1/deliveries/{id}/retry` - **Manual Retry**: Force a retry immediately (e.g., after a customer fixes their server).

### **4. Dead Letter Queue (DLQ)**

*The "Undeliverable Mail Office". Where messages go after max retries.*

- `GET /api/v1/dlq` - **Review**: See messages that completely failed to deliver.
- `POST /api/v1/dlq/{id}/replay` - **Redeliver**: "The customer fixed their bug, try sending this old event again."

---

## **Architecture**

HookSwarm uses the **Transactional Outbox Pattern**:

1. **Ingest**: Event + Outbox Entry saved atomically to Postgres.
2. **Poll**: Background pollers batch unprocessed events.
3. **Fan-out**: Events are matched to Subscriptions and converted to Delivery Tasks.
4. **Deliver**: Virtual threads handle the I/O-heavy HTTP requests concurrently.
5. **Resilience**: Circuit breakers pause delivery to failing endpoints; Stale Job Recovery fixes "stuck" tasks.

---

## **Deployment**

### **Prerequisites**

- Java 21
- PostgreSQL 16+
- Docker (for local DB)

### **Quick Start**

1. **Start Database**:z

   ```bash
   docker compose up -dxxx
   ```

2. **Run Application**:

   ```bash
   ./mvnw spring-boot:run
   ```

3. **Access API**: `http://localhost:8080`

### **Configuration**
Key knobs in `application.yml`:
- `hookswarm.delivery.poll-interval-ms`: How often to check for work.
- `hookswarm.threads.virtual.enabled`: High-concurrency mode.
- `hookswarm.retry.max-delay-seconds`: Max wait between retries.
