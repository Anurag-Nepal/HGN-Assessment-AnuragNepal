## System Design

## Basic HLD

![image-20260710103841970](C:\Users\V I C T U S\AppData\Roaming\Typora\typora-user-images\image-20260710103841970.png)



This diagram illustrates the high-level SOS ingestion flow. A simulation script acts as a satellite SOS device by publishing an SOS message containing the device ID, coordinates, and timestamp to an MQTT topic (`sos/{deviceId}`). The backend subscribes to this topic and receives the message in real time without polling. After receiving the SOS, the backend processes it according to the business rules—such as deduplication, booking resolution, alert creation, and persistence in PostgreSQL—before making it available to operators. This event-driven approach closely resembles how real-world satellite communication providers deliver SOS events to downstream systems.

## FInal HLD

![image-20260710103823590](C:\Users\V I C T U S\AppData\Roaming\Typora\typora-user-images\image-20260710103823590.png)This diagram illustrates the internal processing pipeline after an SOS message is received from the MQTT broker. The **MQTT Subscriber** listens to the `sos/{deviceId}` topic and forwards each incoming SOS to the processing pipeline. The pipeline first performs **deduplication** using Redis to ignore retransmitted messages, then resolves the associated **active booking, group, and trekkers** using the device ID and SOS timestamp. A new alert is then created and persisted in the database before being published in real time to connected operator dashboards. Operators can claim the alert, where **optimistic concurrency control** ensures only the first successful claim updates the alert while concurrent attempts are rejected. Separately, an **Escalation Service** periodically scans for unclaimed alerts, escalates those exceeding the configured response window, updates their status in the database, and broadcasts the escalation to all connected operators.

The architecture is designed to scale horizontally without changing the core business flow.

* **Stateless processing services:** Multiple backend instances can subscribe to the MQTT broker simultaneously, allowing incoming SOS events to be processed in parallel.
* **MQTT layer:** A clustered MQTT broker can be introduced to increase throughput and eliminate a single point of failure.
* **Redis:** Redis can be deployed in a clustered or highly available configuration so deduplication remains fast and consistent across all processing instances.
* **Database:** Connection pooling, read replicas, and table partitioning can be introduced as alert volume grows, while indexes ensure efficient booking resolution and alert queries.
* **Real-time notifications:** In a multi-instance deployment, a shared messaging mechanism (such as Redis Pub/Sub or Kafka) can synchronize alert broadcasts so operators connected to different backend instances receive identical updates.
* **Concurrency control:** Alert claiming relies on optimistic conditional updates in the database, ensuring only one operator can successfully claim an alert regardless of how many application instances are running.
* **Scheduled tasks:** The escalation process should use distributed locking so that only one instance performs periodic escalation checks, preventing duplicate processing.

This event-driven architecture keeps ingestion, processing, persistence, and notification loosely coupled, enabling each component to scale independently as the number of devices, operators, and SOS events increases.
HGN SOS Alert Service: Decisions & Tradeoffs
============================================

## SOS Ingestion : MQTT

**Decision:** SOS intake is exclusively via MQTT on topic `sos/+`.

**Why:** Satellite SOS devices communicate over MQTT. A REST endpoint would duplicate this channel and create ambiguity. The MQTT listener runs at QoS 1, so the broker guarantees at-least-once delivery. `spring-integration-mqtt` handles reconnection and back-pressure automatically. 

## Redis vs. DB Deduplication

**Decision:** Redis `SETNX` with a composite key `sos:dedup:{deviceId}:{epochSec}:{lat:4f}:{lon:4f}` and 60-second TTL.

**Why:** During an emergency, identical retry signals might bombard the intake service (device auto-retry every 20–60s on lack of ack or QoS 1 redelivery). Redis offers:

* Atomic `SETNX` — no race window

* Sub-millisecond check, no DB connection consumed

* Automatic TTL expiry — no cleanup needed 

## Dedup TTL (60s)

**Decision:** `sos.dedup-ttl-seconds = 60`.

**Why:** SOS devices retry within a 20–60s window. A 60s TTL squashes identical retries from the same device/position, but is short enough that a genuinely new SOS 2 minutes later from a different location registers as fresh. The key includes lat/lon to 4 decimal places (~11m precision) so GPS drift does not cause spurious misses — only identical coordinates within the same second are deduplicated. 

## Booking Resolution

**Decision:** Two-phase lookup: exact match first, then grace-window fallback.

### Exact match

    WHERE device_id = :d AND status = 'ACTIVE' AND :ts BETWEEN start_date AND end_date

**Why:** The SOS timestamp must fall within an active booking's date range. An exact match is unambiguous.

### Grace window (±1 day)

    WHERE device_id = :d AND status = 'ACTIVE'
      AND (:ts - 1d) <= end_date AND (:ts + 1d) >= start_date

**Why:** Trekkers often start early or return late. A 1-day grace window prevents critical failures where a genuine SOS is rejected because the trekker was 4 hours late returning.

### Ambiguity handling

| Case                   | Result                                                        |
| ---------------------- | ------------------------------------------------------------- |
| 1 exact match          | Resolved to that order                                        |
| 0 exact, 1 grace match | Resolved via grace (`resolvedViaGraceWindow = true`)          |
| 2+ matches (any phase) | Ambiguous: alert created with `urgent = true`, no order/group |

**Why:** Multiple overlapping active bookings are a data integrity issue. Rather than guess, the system flags the alert as urgent for manual operator review. 

## Concurrency-Safe Claiming

**Decision:** Atomic native `UPDATE` with `WHERE status = 'OPEN'` condition. No `SELECT ... FOR UPDATE`, no application-level locks.
    UPDATE alert SET status = 'CLAIMED', claimed_by = :claimedBy, claimed_at = now()
    WHERE id = :alertId AND status = 'OPEN'

**Why:** PostgreSQL serialises row-level writes. Two concurrent `UPDATE` statements both check the `WHERE` clause against the same snapshot; the second finds `status ≠ 'OPEN'` and affects zero rows. This is simpler and faster than pessimistic locking.

**Why NOT `@Version`:** The `@Version` annotation was originally present but the native query manually incremented `version`, creating a collision risk if JPA ever loaded and saved the same entity. Since the `WHERE status = 'OPEN'` provides the same protection, the manual increment was removed and `@Version` is left for JPA's own use only.

**Proven by** `ClaimConcurrencyIT` (10 threads, exactly 1 succeeds). 

## Event-Driven WebSocket Push

**Decision:** Push notifications use `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` rather than direct service calls inside `@Transactional` methods.

**Why:** If a WebSocket broadcast throws (e.g., serialization error, closed session), the DB transaction should not roll back. Decoupling ensures the alert is persisted regardless of push success. The listener runs only after the transaction commits successfully.

**Flow:**
    @Transactional method → publishEvent(AlertPushEvent)
                                     │
                        (transaction commits)
                                     │
                                     ▼
                  @TransactionalEventListener(after commit)
                                     │
                                     ▼
                  AlertPushService.pushNewAlert / pushEscalation / pushClaimUpdate 

## WebSocket

**Decision:** Raw WebSocket (`@EnableWebSocket` + `TextWebSocketHandler`) for realtime push to operator's dashboard.

**Why:** The only requirement is a single broadcast topic (`/topic/alerts`). WebSocket connects with just an HTTP upgrade + JWT Bearer token, and the server pushes JSON messages directly. Testing is simpler (any WebSocket client works). 

## ShedLock for Distributed Scheduling

**Decision:** `net.javacrumbs.shedlock` with a JDBC `LockProvider` for all `@Scheduled` jobs.

**Why:** The `EscalationJob` and `OrderExpiryJob` must run exactly once even when multiple application instances are deployed. Without a distributed lock, every instance would escalate the same alerts and push duplicate notifications. ShedLock uses a database-backed lock table (`shedlock`) that is transactionally safe. 

## Global Exception Handler

**Decision:** Single `@RestControllerAdvice` class handling all exceptions.

| Exception                                 | HTTP Status                   |
| ----------------------------------------- | ----------------------------- |
| `AlertNotFoundException`                  | 404                           |
| `MissingServletRequestParameterException` | 400                           |
| `MethodArgumentNotValidException`         | 400                           |
| Generic `Exception`                       | 500 (with logged stack trace) |

**Why:** Without this, unhandled exceptions produce a 500 with a stack trace in the response body. Operators get consistent JSON error objects instead. 

## Package Structure

    com.hgn.sos/
    ├── config/         — Spring config, security, WebSocket, MQTT
    ├── controller/     — REST endpoints (auth, alert query, alert claim)
    ├── dto/            — Data transfer objects (records)
    ├── model/          — JPA entities + enums
    ├── repository/     — Spring Data JPA repositories
    ├── service/        — Business logic + scheduled jobs
    └── utils/          — Exception classes

**Why:** Separation by layer (not by feature) keeps dependencies flowing in one direction: controller → service → repository. Cross-cutting concerns (config, DTOs) are shared naturally. 

## Technology Choices

| Component        | Choice                      | Why                                                |
| ---------------- | --------------------------- | -------------------------------------------------- |
| Framework        | Spring Boot 3.3.2 + Java 17 | LTS, virtual threads ready, modern JPA             |
| Database         | PostgreSQL 16               | JSON support, `RETURNING *`, robust locking        |
| Cache            | Redis 7                     | Atomic `SETNX`, sub-ms latency, built-in TTL       |
| MQTT Broker      | Eclipse Mosquitto 2         | Lightweight, Eclipse `paho` client, Docker image   |
| JWT              | jjwt 0.12.6                 | Actively maintained, supports HMAC-SHA256          |
| Migration        | Flyway                      | Versioned, checksummed, repeatable                 |
| API docs         | springdoc-openapi 2.6.0     | OpenAPI 3, Swagger UI, bearer auth annotations     |
| Distributed lock | ShedLock 5.16.0             | Spring-native, minimal config, no Redis dependency |
| Testing          | JUnit 5 + Testcontainers    | Integration tests with real Postgres/Redis         |




