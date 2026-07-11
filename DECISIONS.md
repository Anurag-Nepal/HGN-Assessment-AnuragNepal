# HGN SOS Alert Service — Decisions & Tradeoffs

## 1. Overall Architecture

**Context:** A satellite SOS device sends emergency signals via MQTT. The system validates, deduplicates, resolves booking ownership, persists the alert, and pushes it in real-time to operator dashboards via WebSocket.

**Container Diagram:**

```
┌─────────────────────────────────────────────────────────────────────┐
│                        OPERATOR (Browser/Postman)                    │
│                                                                      │
│   ┌──────────────┐         ┌───────────────────────────────────────┐ │
│   │  REST Client  │  HTTP   │  WebSocket Client                   │ │
│   │  (Auth, List, │◄──────► │  (Receives real-time alert JSON)    │ │
│   │   Claim APIs) │  JWT    │  ws://host/ws/alerts                │ │
│   └──────────────┘         └──────────┬────────────────────────────┘ │
└──────────────────────────────────────┼────────────────────────────────┘
                                       │ ws upgrade + Bearer token
                                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    HGN SOS BACKEND (Spring Boot 3.3)                  │
│                                                                      │
│  ┌──────────────┐  ┌───────────┐  ┌──────────────┐  ┌────────────┐  │
│  │ MQTT Listener │  │  REST     │  │ WebSocket    │  │  Scheduler │  │
│  │ (SosMqttList.)│  │Controllers│  │ Handler      │  │ (Escalation│  │
│  │   mosquitto   │  │(Auth,     │  │ (AlertWebSkt │  │  + Expiry) │  │
│  │   topic:sos/+ │  │ Query,    │  │  Handler)    │  │            │  │
│  └──────┬───────┘  │ Claim)    │  └──────▲────────┘  └─────▲──────┘  │
│         │          └─────┬─────┘         │                  │         │
│         ▼                ▼               │                  │         │
│  ┌──────────────────────────────────────────────────────────┐│         │
│  │                  SosIntakeService                         ││         │
│  │   ┌──────────┐   ┌──────────────┐   ┌────────────────┐   ││         │
│  │   │ Dedup    │   │  Resolution  │   │  AlertPush     │   ││         │
│  │   │ Service  │──►│  Service     │──►│  Service       │───┘│         │
│  │   │ (Redis)  │   │  (Order      │   │  (WebSocket    │    │         │
│  │   └──────────┘   │   lookup)    │   │   broadcast)   │    │         │
│  │                  └──────────────┘   └────────────────┘    │         │
│  └──────────────────────────────────────────────────────────┘         │
│         │                                                             │
│         ▼                                                             │
│  ┌──────────────────────────────────────────────┐                     │
│  │          AlertRepository / OrderRepository    │                     │
│  │          (Spring Data JPA)                    │                     │
│  └───────────────────┬──────────────────────────┘                     │
└──────────────────────┼────────────────────────────────────────────────┘
                       │
            ┌──────────┼──────────┐
            ▼          ▼          ▼
       ┌────────┐ ┌────────┐ ┌──────────┐
       │Postgres│ │ Redis  │ │Mosquitto │
       │   :16  │ │   :7   │ │   :2     │
       └────────┘ └────────┘ └──────────┘
```

**Intake Processing Pipeline (detailed):**

```
Device SOS ──MQTT──> SosMqttListener
                         │
                    deserialize JSON
                         │
                         ▼
                    SosIntakeService.handleIncomingSos()  (@Transactional)
                         │
                    ┌────┴────┐
                    │         │
                    ▼         ▼
              DedupService  (new)
              (Redis SETNX)   │
                    │         │
              ┌─────┘         │
              │ (duplicate)   │ (first-time)
              │               │
              ▼               ▼
         return         ResolutionService.resolve()
         existing        (Order lookup)
                              │
                         ┌────┴────┐
                         │         │
                         ▼         ▼
                    1 match    0 or 2+ matches
                         │         │
                         ▼         ▼
                   set orderId   set urgent=true
                   & groupId
                         │
                         ▼
                    AlertRepository.save()
                         │
                         ▼
                    AlertPushService
                    (serialize AlertDto → JSON)
                         │
                         ▼
                    AlertWebSocketHandler.broadcast()
                         │
                         ▼
                    All connected operator sessions
```

**Why:** MQTT is the natural protocol for IoT satellite devices (low bandwidth, persistent connection, QoS 1 delivery). Redis provides a fast, atomic dedup layer without burdening PostgreSQL. Raw WebSocket eliminates STOMP protocol overhead since the only need is a single broadcast topic.

**Key architectural properties:**
- **Layered**: Controller → Service → Repository — each layer has a single responsibility.
- **Transaction-per-request**: The entire intake pipeline runs inside one `@Transactional` boundary. WebSocket push failures are caught and logged (not propagated), so a failed push never rolls back the alert creation.
- **Single-instance safe with ShedLock**: Scheduled jobs use database-backed distributed locks so multiple app instances don't duplicate work.
- **Fail-open for Redis**: If Redis is unreachable, dedup is skipped and the SOS is allowed through rather than dropped.

---

## 2. SOS Ingestion — MQTT Only

**Decision:** SOS intake is exclusively via MQTT on topic `sos/+` (one sub-topic per device). No REST intake endpoint.

**Why:** Satellite SOS devices communicate over MQTT. A REST endpoint would duplicate this channel and create ambiguity. The MQTT listener runs at QoS 1, so the broker guarantees at-least-once delivery. `spring-integration-mqtt` handles reconnection and back-pressure automatically.

**Tradeoff:** Testing requires an MQTT client (Python script provided in `scripts/publish_sos.py`). There is no HTTP fallback for manual SOS creation.

---

## 3. Redis vs. DB Deduplication

**Decision:** Redis `SETNX` with a composite key `sos:dedup:{deviceId}:{epochSec}:{lat:4f}:{lon:4f}` and 60-second TTL.

**Why:** During an emergency, identical retry signals might bombard the intake service (device auto-retry every 20–60s on lack of ack or QoS 1 redelivery). Redis offers:
- Atomic `SETNX` — no race window
- Sub-millisecond check, no DB connection consumed
- Automatic TTL expiry — no cleanup needed

**Tradeoff:** A transient Redis outage now falls open (allows the SOS through) rather than failing closed. See §7.

---

## 4. Dedup TTL (60s)

**Decision:** `sos.dedup-ttl-seconds = 60`.

**Why:** SOS devices retry within a 20–60 s window. A 60 s TTL squashes identical retries from the same device/position, but is short enough that a genuinely new SOS 2 minutes later from a different location registers as fresh. The key includes lat/lon to 4 decimal places (~11 m precision) so GPS drift does not cause spurious misses—only identical coordinates within the same second are deduplicated.

---

## 5. Booking Resolution

**Decision:** Two-phase lookup: exact match first, then grace-window fallback.

### Exact match
```
WHERE device_id = :d AND status = 'ACTIVE' AND :ts BETWEEN start_date AND end_date
```

**Why:** The SOS timestamp must fall within an active booking's date range. An exact match is unambiguous.

### Grace window (±1 day)
```
WHERE device_id = :d AND status = 'ACTIVE'
  AND (:ts - 1d) <= end_date AND (:ts + 1d) >= start_date
```

**Why:** Trekkers often start early or return late. A 1-day grace window prevents critical failures where a genuine SOS is rejected because the trekker was 4 hours late returning.

### Ambiguity handling

| Case | Result |
|---|---|
| 1 exact match | Resolved to that order |
| 0 exact, 1 grace match | Resolved via grace (`resolvedViaGraceWindow = true`) |
| 2+ matches (any phase) | Ambiguous — alert created with `urgent = true`, no order/group |

**Why:** Multiple overlapping active bookings are a data integrity issue. Rather than guess, the system flags the alert as urgent for manual operator review.

---

## 6. Concurrency-Safe Claiming

**Decision:** Atomic native `UPDATE` with `WHERE status = 'OPEN'` condition. No `SELECT ... FOR UPDATE`, no application-level locks.

```sql
UPDATE alert SET status = 'CLAIMED', claimed_by = :claimedBy, claimed_at = now()
WHERE id = :alertId AND status = 'OPEN'
```

**Why:** PostgreSQL serialises row-level writes. Two concurrent `UPDATE` statements both check the `WHERE` clause against the same snapshot; the second finds `status ≠ 'OPEN'` and affects zero rows. This is simpler and faster than pessimistic locking.

**Why NOT `@Version`:** The `@Version` annotation was originally present but the native query manually incremented `version`, creating a collision risk if JPA ever loaded and saved the same entity. Since the `WHERE status = 'OPEN'` provides the same protection, the manual increment was removed and `@Version` is left for JPA's own use only.

**Proven by** `ClaimConcurrencyIT` (10 threads, exactly 1 succeeds).

---

## 7. Incident Entity Merged Into Alert

**Decision:** The `incident` table was dropped and its fields (`claimed_by`, `claimed_at`, `resolved_at`, `notes`) were added to the `alert` table.

**Why:** The original two-phase claim flow (`UPDATE alert` → `UPDATE incident`) created a small race window and required a join on every read. An alert is always a potential incident; the split was artificial. Merging simplifies the claim to a single atomic `UPDATE`, reduces DB round-trips, and eliminates `IncidentRepository` entirely.

**Migration:** V4 migrates existing incident data into the alert table and drops the incident table with its FK.

---

## 8. Event-Driven WebSocket Push

**Decision:** Push notifications use `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` rather than direct service calls inside `@Transactional` methods.

**Why:** If a WebSocket broadcast throws (e.g., serialization error, closed session), the DB transaction should not roll back. Decoupling ensures the alert is persisted regardless of push success. The listener runs only after the transaction commits successfully.

**Flow:**
```
@Transactional method → publishEvent(AlertPushEvent)
                                 │
                    (transaction commits)
                                 │
                                 ▼
              @TransactionalEventListener(after commit)
                                 │
                                 ▼
              AlertPushService.pushNewAlert / pushEscalation / pushClaimUpdate
```

---

## 9. Raw WebSocket vs. STOMP

**Decision:** Raw WebSocket (`@EnableWebSocket` + `TextWebSocketHandler`) instead of STOMP-over-WebSocket (`@EnableWebSocketMessageBroker`).

**Why:** The only requirement is a single broadcast topic (`/topic/alerts`). STOMP adds protocol overhead (CONNECT, SUBSCRIBE, heartbeat frames, null-byte terminators). Raw WebSocket connects with just an HTTP upgrade + JWT Bearer token, and the server pushes JSON messages directly. Testing is simpler (any WebSocket client works) and the code is 50% less.

---

## 10. ShedLock for Distributed Scheduling

**Decision:** `net.javacrumbs.shedlock` with a JDBC `LockProvider` for all `@Scheduled` jobs.

**Why:** The `EscalationJob` and `OrderExpiryJob` must run exactly once even when multiple application instances are deployed. Without a distributed lock, every instance would escalate the same alerts and push duplicate notifications. ShedLock uses a database-backed lock table (`shedlock`) that is transactionally safe.

---

## 11. Order Status as Enum

**Decision:** `Order.status` is `@Enumerated(EnumType.STRING) OrderStatus` instead of a plain `String`.

**Why:** Stringly-typed statuses invite bugs (typos in `WHERE o.status = 'ACTIVE'` are caught at compile time only with an enum). The DB has a matching `CHECK` constraint (`'ACTIVE', 'COMPLETED', 'CANCELLED'`).

---

## 12. Order Expiry Job

**Decision:** `OrderExpiryJob` runs every hour via `@Scheduled(fixedRate = 3600000)` and flips `ACTIVE → COMPLETED` when `endDate < now()`.

**Why:** Without lifecycle management, expired orders remain `ACTIVE` forever and pollute booking-resolution queries. The job is idempotent (only affects rows matching the WHERE clause) and protected by ShedLock.

---

## 13. Global Exception Handler

**Decision:** Single `@RestControllerAdvice` class handling all exceptions.

| Exception | HTTP Status |
|---|---|
| `AlertNotFoundException` | 404 |
| `MissingServletRequestParameterException` | 400 |
| `MethodArgumentNotValidException` | 400 |
| Generic `Exception` | 500 (with logged stack trace) |

**Why:** Without this, unhandled exceptions produce a 500 with a stack trace in the response body. Operators get consistent JSON error objects instead.

---

## 14. DTO-Separated, Paginated API

**Decision:** All GET endpoints return `AlertDto` (not the JPA `Alert` entity) and accept `Pageable`.

**Why:** Returning JPA entities directly leaks internal column names, lazy-loading proxies, and serialization cycles. Pagination prevents unbounded result sets from overwhelming memory or the network. Default page size is 50.

---

## 15. Redis Fallback (Fail-Open)

**Decision:** `DedupService.registerIfNew()` wraps the Redis call in try/catch. If Redis is unreachable, it logs a warning and returns `true` (allow the SOS through).

**Why:** An SOS must never be silently dropped. The system favours availability over strict deduplication. If Redis is down, duplicates may briefly pass through, but the alert is still persisted and visible to operators.

---

## 16. Composite Indexes

**Decision:** Added two composite indexes in V4:
- `ix_trek_order_device_status_dates` on `(device_id, status, start_date, end_date)`
- `ix_alert_device_created` on `(device_id, created_at DESC)`

**Why:** The booking-resolution queries filter on `device_id`, `status`, and date range — without a composite index they would sequential-scan the entire `trek_order` table. The alert query `findMostRecentByDeviceId` sorts by `created_at DESC` per device.

---

## 17. Package Structure

```
com.hgn.sos/
├── config/         — Spring config, security, WebSocket, MQTT
├── controller/     — REST endpoints (auth, alert query, alert claim)
├── dto/            — Data transfer objects (records)
├── model/          — JPA entities + enums
├── repository/     — Spring Data JPA repositories
├── service/        — Business logic + scheduled jobs
└── utils/          — Exception classes
```

**Why:** Separation by layer (not by feature) keeps dependencies flowing in one direction: controller → service → repository. Cross-cutting concerns (config, DTOs) are shared naturally.

---

## 18. Request Validation

**Decision:** Jakarta `@NotBlank` on all mandatory request body fields + `@Valid` on controller `@RequestBody` parameters.

**Why:** A claim request with a blank `coordinatorId` or a login request with missing credentials should be rejected early with a clear 400 error, not processed halfway and fail with a cryptic exception.

---

## 19. Eliminated Duplicate DTO

**Decision:** `MqttSosDto` was deleted; `SosPayload` uses `@JsonProperty("timestamp")` to deserialize the incoming MQTT JSON directly.

**Why:** Two records with identical fields (`deviceId`, `latitude`, `longitude`, `timestamp`/`deviceTimestamp`) served only as a pointless copy. The `@JsonProperty` alias on `SosPayload.deviceTimestamp` handles the field-name mismatch at the Jackson level.

---

## 20. No REST SOS Intake

**Decision:** The original `SosIntakeController` (REST POST /api/sos) was removed.

**Why:** SOS devices communicate exclusively over MQTT. The REST endpoint duplicated the pipeline without a real use case and expanded the attack surface. If a REST intake is needed later, adding it is trivial.

---

## 21. Technology Choices

| Component | Choice | Why |
|---|---|---|
| Framework | Spring Boot 3.3.2 + Java 17 | LTS, virtual threads ready, modern JPA |
| Database | PostgreSQL 16 | JSON support, `RETURNING *`, robust locking |
| Cache | Redis 7 | Atomic `SETNX`, sub-ms latency, built-in TTL |
| MQTT Broker | Eclipse Mosquitto 2 | Lightweight, Eclipse `paho` client, Docker image |
| JWT | jjwt 0.12.6 | Actively maintained, supports HMAC-SHA256 |
| Migration | Flyway | Versioned, checksummed, repeatable |
| API docs | springdoc-openapi 2.6.0 | OpenAPI 3, Swagger UI, bearer auth annotations |
| Distributed lock | ShedLock 5.16.0 | Spring-native, minimal config, no Redis dependency |
| Testing | JUnit 5 + Testcontainers | Integration tests with real Postgres/Redis |
