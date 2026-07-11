# HGN SOS Alert Service

A Spring Boot backend for ingesting satellite SOS signals, resolving trekking booking ownership, deduplicating retry storms, and pushing real-time alerts to operator dashboards via WebSocket.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Quick Start (Docker)](#quick-start-docker)
- [Running Locally (Without Docker)](#running-locally-without-docker)
- [Configuration](#configuration)
- [Database Schema](#database-schema)
- [API Reference](#api-reference)
- [WebSocket](#websocket)
- [Testing](#testing)
- [Simulating an SOS](#simulating-an-sos)
- [End-to-End Flow](#end-to-end-flow)
- [Project Structure](#project-structure)
- [Architectural Decisions](#architectural-decisions)
- [Troubleshooting](#troubleshooting)

---

## Architecture Overview

```text
Satellite SOS Device ──MQTT──→ Mosquitto Broker
                                      │
                                      ▼
                              HGN SOS Backend
                         ┌─────────────────────┐
                         │  SosMqttListener     │
                         │   (topic: sos/+)     │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         SosIntakeService (@Transactional)
                         ┌─────────────────────┐
                         │ 1. Redis Dedup      │
                         │ 2. Order Resolution │
                         │ 3. Persist Alert    │
                         │ 4. WebSocket Push   │
                         └─────────────────────┘
                                    │
                          ┌─────────┴─────────┐
                          ▼                   ▼
                       Redis              PostgreSQL
                    (dedup cache)      (alerts, orders)
```

The pipeline: **MQTT** → **Dedup** (Redis) → **Booking Resolution** (PostgreSQL) → **Alert Persistence** → **WebSocket Push** (real-time to operators).

---

## Tech Stack

| Component | Technology |
| --- | --- |
| Framework | Spring Boot 3.3.2, Java 17 |
| Database | PostgreSQL 16 |
| Cache | Redis 7 (deduplication) |
| MQTT Broker | Eclipse Mosquitto 2 |
| Messaging | Spring Integration MQTT |
| WebSocket | Raw WebSocket (no STOMP) |
| Auth | JWT (jjwt 0.12.6) + Spring Security |
| Migrations | Flyway |
| API Docs | springdoc-openapi (Swagger UI) |
| Distributed Lock | ShedLock 5.16 |
| Build | Maven + Docker |
| Testing | JUnit 5, Mockito, Testcontainers |

---

## Prerequisites

- **Docker Desktop** (recommended) — runs all dependencies in containers
- OR standalone: Java 17+, PostgreSQL 16, Redis 7, Mosquitto 2
- **Postman** or any WebSocket client (for testing push notifications)
- **Python 3** (for the MQTT simulation script)

---

## Quick Start (Docker)

### 1. Clone and build

```bash
git clone <repo-url> hgn-sos
cd hgn-sos
```

### 2. Build the Docker image

```bash
docker compose build
```

### 3. Start all services

```bash
docker compose up -d
```

This starts 4 containers:

| Container | Port | Purpose |
| --- | --- | --- |
| `app` | 8080 | Spring Boot backend |
| `postgres` | 5432 | Database |
| `redis` | 6379 | Dedup cache |
| `mosquitto` | 1883 | MQTT broker |

### 4. Verify the app is running

```bash
curl http://localhost:8080/api/alerts
```

If you get a `401 Unauthorized`, the app is running (auth is required).

### 5. Check logs

```bash
docker compose logs -f app
```

---

## Running Locally (Without Docker)

### 1. Start dependencies

Make sure PostgreSQL 16, Redis 7, and Mosquitto 2 are running on localhost with default ports.

### 2. Configure database

```sql
CREATE DATABASE hgn_sos;
```

### 3. Build and run

```bash
./mvnw clean package -DskipTests
java -jar target/hgn-sos-0.0.1-SNAPSHOT.jar
```

---

## Configuration

All configuration is in `src/main/resources/application.yml`. Values can be overridden via environment variables:

| Variable | Default | Description |
| --- | --- | --- |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_USER` | `postgres` | DB username |
| `DB_PASSWORD` | `postgres` | DB password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `MQTT_BROKER_URL` | `tcp://localhost:1883` | Mosquitto URL |
| `JWT_SECRET` | *(embedded default)* | 256-bit Base64 secret for signing JWTs |
| `JWT_EXPIRATION_MS` | `86400000` | Token validity (24h) |
| `DEDUP_TTL_SECONDS` | `60` | Redis dedup window |
| `ESCALATION_WINDOW_SECONDS` | `300` | Auto-escalate after N seconds |
| `ESCALATION_POLL_MS` | `30000` | Escalation job interval |
| `RESOLUTION_GRACE_DAYS` | `1` | ±N days grace for booking overlap |

---

## Database Schema

**6 tables** (PostgreSQL):

| Table | Purpose |
| --- | --- |
| `device` | Satellite hardware devices (unique by `external_device_id`) |
| `trekker` | Individual trekkers/persons |
| `group_table` | Trekking groups |
| `trek_order` | Bookings linking device + group + date range |
| `group_member` | Many-to-many: which trekkers belong to which group for which order |
| `alert` | SOS alerts (core table — includes claim/incident fields) |
| `app_user` | Login accounts for operator dashboard |
| `shedlock` | Distributed lock table for schedulers |

**Key indexes:**

- `trek_order(device_id, status, start_date, end_date)` — fast booking lookup
- `alert(device_id, created_at DESC)` — latest alert per device
- `alert(status, created_at)` — escalation and status queries
- Partial index on `alert(order_id) WHERE order_id IS NULL` — unresolved ownership

---

## API Reference

Full interactive docs at: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Authentication

All endpoints except `/api/auth/login` require a JWT Bearer token.

#### `POST /api/auth/login`

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "password"}'
```

Response:

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9..." }
```

**Test users** (seeded by Flyway V3):

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `password` | `OPERATOR` (full access) |
| `user` | `password` | `USER` (limited access) |

---

### Alerts

All alert endpoints require the `Authorization: Bearer <token>` header and the `OPERATOR` role.

#### `GET /api/alerts`

List alerts with pagination, always sorted by device timestamp (newest first).

```bash
curl http://localhost:8080/api/alerts?page=0&size=50 \
  -H "Authorization: Bearer <token>"
```

Parameters:

| Param | Default | Description |
| --- | --- | --- |
| `status` | *(all)* | Filter: `OPEN`, `CLAIMED`, `ESCALATED`, `RESOLVED` |
| `page` | `0` | Zero-based page number |
| `size` | `50` | Items per page |

Returns:

```json
[
  {
    "id": "uuid",
    "deviceId": "uuid",
    "orderId": "uuid",
    "groupId": "uuid",
    "latitude": 27.7172,
    "longitude": 85.324,
    "status": "OPEN",
    "urgent": false,
    "receivedAt": "2026-07-11T12:00:00Z",
    "claimedBy": null,
    "claimedAt": null,
    "resolvedAt": null,
    "notes": null
  }
]
```

#### `GET /api/alerts/unresolved-ownership`

Alerts where no booking could be resolved (no matching order or ambiguous). These have `urgent: true` and `orderId: null`.

```bash
curl http://localhost:8080/api/alerts/unresolved-ownership \
  -H "Authorization: Bearer <token>"
```

#### `POST /api/alerts/{id}/claim`

Claim an alert. Only works if the alert is in `OPEN` status.

```bash
curl -X POST http://localhost:8080/api/alerts/{alertId}/claim \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"coordinatorId": "operator-1"}'
```

Success (200):

```json
{ "success": true, "status": "CLAIMED", "claimedBy": "operator-1" }
```

Conflict (409 — already claimed):

```json
{ "success": false, "status": "CLAIMED", "claimedBy": "other-operator" }
```

---

## WebSocket

**Endpoint:** `ws://localhost:8080/ws/alerts`

**Authentication:** Connect with the JWT token in the `Authorization` header:

```text
Authorization: Bearer <token>
```

The user must have the `OPERATOR` role.

**What you receive:** JSON messages (same format as the REST `AlertDto`) whenever:

- A new SOS alert is created (`CREATED`)
- An alert is escalated (`ESCALATED`)
- An alert is claimed (`CLAIMED`)

**Testing with Postman:**

1. `POST /api/auth/login` to get a JWT token
2. Create a new WebSocket request to `ws://localhost:8080/ws/alerts`
3. Set header `Authorization: Bearer <token>`
4. Click **Connect**
5. Send an SOS (see [Simulating an SOS](#simulating-an-sos)) — you'll receive the alert as a JSON message

**Testing with Python:**

```python
import asyncio
import websockets

async def listen():
    async with websockets.connect(
        "ws://localhost:8080/ws/alerts",
        extra_headers={"Authorization": "Bearer <token>"}
    ) as ws:
        async for msg in ws:
            print("Received:", msg)

asyncio.run(listen())
```

---

## Testing

### Run unit tests

```bash
./mvnw test
```

Tests (6 total):

| Test | Type | What it verifies |
| --- | --- | --- |
| `DedupServiceTest` | Unit (mocked Redis) | `registerIfNew` returns true first call, false second call within TTL |
| `ResolutionServiceTest` | Unit (mocked OrderRepo) | Exact match, ambiguous, grace window, no match |
| `PasswordGenTest` | Unit | BCrypt hash generation |
| `ClaimConcurrencyIT` | Integration (Testcontainers) | 10 threads claim the same alert — exactly 1 succeeds |

> **Note:** `ClaimConcurrencyIT` uses the `*IT` suffix (Failsafe convention) and only runs during a full `mvn verify` if the failsafe plugin is configured. By default it is skipped in `mvn test`.

---

## Simulating an SOS

### Using the Python script

```bash
pip install paho-mqtt
python scripts/publish_sos.py
```

Optional arguments:

```bash
python scripts/publish_sos.py <device-uuid> <mqtt-broker-host>
```

Default device UUID: `123e4567-e89b-12d3-a456-426614174000` (seeded by V2 migration).

### Using the Mosquitto CLI

```bash
docker compose exec mosquitto mosquitto_pub \
  -t "sos/123e4567-e89b-12d3-a456-426614174000" \
  -m '{"deviceId":"123e4567-e89b-12d3-a456-426614174000","latitude":27.7172,"longitude":85.324,"timestamp":"2026-07-11T12:00:00Z"}' \
  -q 1
```

### What happens

1. **MQTT** → `SosMqttListener` receives the message
2. **Dedup** → Redis checks if this exact device+coordinates+second has been seen in the last 60s
3. **Resolution** → `OrderRepository` looks for an active booking covering this timestamp (±1 day grace)
4. **Persist** → A new `alert` row is created with status `OPEN`
5. **Push** → The alert JSON is broadcast to all connected WebSocket operator sessions

---

## End-to-End Flow

```text
Step 1: Start all services
  $ docker compose up -d

Step 2: Get a JWT token
  $ curl -X POST http://localhost:8080/api/auth/login \
      -H "Content-Type: application/json" \
      -d '{"username":"admin","password":"password"}'
  → {"token":"eyJ..."}

Step 3: Connect WebSocket (Postman)
  URL: ws://localhost:8080/ws/alerts
  Header: Authorization: Bearer eyJ...

Step 4: Send an SOS
  $ python scripts/publish_sos.py

Step 5: Verify alert via REST
  $ curl http://localhost:8080/api/alerts -H "Authorization: Bearer eyJ..."
  → [{ "status": "OPEN", ... }]

Step 6: Claim the alert
  $ curl -X POST http://localhost:8080/api/alerts/<alert-id>/claim \
      -H "Authorization: Bearer eyJ..." \
      -H "Content-Type: application/json" \
      -d '{"coordinatorId":"operator-1"}'
  → {"success":true,"status":"CLAIMED","claimedBy":"operator-1"}

Step 7: Wait 5 minutes (escalation)
  → The alert auto-escalates if not claimed within 5 minutes
  → WebSocket receives an ESCALATED push
```

---

## Project Structure

```text
src/main/java/com/hgn/sos/
├── HgnSosApplication.java          # Entry point
├── config/
│   ├── AlertWebSocketHandler.java  # Raw WebSocket handler (session mgmt + broadcast)
│   ├── AppConfig.java              # Spring Security beans (auth provider, password encoder)
│   ├── GlobalExceptionHandler.java # @RestControllerAdvice (404, 400, 500)
│   ├── JwtAuthenticationFilter.java# OncePerRequestFilter for JWT validation
│   ├── MqttConfig.java             # Spring Integration MQTT inbound adapter
│   ├── OpenApiConfig.java          # Swagger/OpenAPI config with bearer auth
│   ├── SchedulerConfig.java        # ShedLock distributed scheduler config
│   ├── SecurityConfig.java         # HTTP security, role-based access, CORS
│   └── WebSocketConfig.java        # @EnableWebSocket + handler registration
├── controller/
│   ├── AlertClaimController.java   # POST /api/alerts/{id}/claim
│   ├── AlertQueryController.java   # GET /api/alerts, GET /unresolved-ownership
│   └── AuthController.java         # POST /api/auth/login
├── dto/                            # Records
│   ├── AlertDto.java               # Response DTO for alerts
│   ├── AuthRequest.java            # Login request
│   ├── AuthResponse.java           # Login response (JWT token)
│   ├── ClaimOutcome.java           # Claim result
│   ├── ClaimRequest.java           # Claim request body
│   ├── IntakeResult.java           # SOS intake result
│   ├── ResolutionResult.java       # Booking resolution result
│   └── SosPayload.java             # Internal SOS payload
├── model/                          # JPA entities + enums
│   ├── Alert.java                  # Core entity (with claim/incident fields)
│   ├── AlertStatus.java            # OPEN, CLAIMED, ESCALATED, RESOLVED
│   ├── AppUser.java                # User entity (implements UserDetails)
│   ├── Device.java                 # Satellite device
│   ├── Group.java                  # Trekking group
│   ├── GroupMember.java            # Join table (group ↔ trekker ↔ order)
│   ├── Order.java                  # Trek booking
│   ├── OrderStatus.java            # ACTIVE, COMPLETED, CANCELLED
│   └── Trekker.java                # Individual trekker
├── repository/                     # Spring Data JPA
│   ├── AlertRepository.java        # Native queries for claim, escalation, pagination
│   ├── OrderRepository.java        # Booking resolution + expiry queries
│   └── UserRepository.java         # User lookup
├── service/
│   ├── AlertClaimService.java      # Claim logic (atomic UPDATE + push)
│   ├── AlertPushService.java       # JSON serialization + WebSocket broadcast
│   ├── DedupService.java           # Redis SETNX dedup with fail-open
│   ├── EscalationJob.java          # @Scheduled escalation (ShedLock)
│   ├── JwtService.java             # JWT generation + validation
│   ├── OrderExpiryJob.java         # @Scheduled order expiry (ShedLock)
│   ├── ResolutionService.java      # Booking resolution (exact match → grace window)
│   ├── SosIntakeService.java       # Orchestrates the entire intake pipeline
│   └── SosMqttListener.java        # MQTT inbound message handler
└── utils/
    └── AlertNotFoundException.java # 404 exception

src/main/resources/
├── application.yml                 # All configuration
└── db/migration/                   # Flyway migrations
    ├── V1__init.sql                        # Core schema (device, trekker, group, order, alert)
    ├── V2__seed_test_data.sql              # Seed data for testing
    ├── V3__security_schema.sql             # app_user table + test accounts
    └── V4__merge_incident_add_indexes.sql  # Merge incident into alert, add indexes
```

---

## Architectural Decisions

Full details in [`DECISIONS.md`](DECISIONS.md). Key highlights:

| Decision | Rationale |
| --- | --- |
| **Raw WebSocket** over STOMP | Single broadcast topic — no need for STOMP frame overhead |
| **Redis dedup** over DB constraints | Atomic SETNX, no DB connection consumed, auto-TTL expiry |
| **60s dedup TTL** | Matches device retry window (20–60s) |
| **±1 day grace window** | Covers early starts / late returns |
| **Incident merged into Alert** | Eliminates two-phase claim and an unnecessary join |
| **Direct push inside `@Transactional`** with try/catch | Simpler than event system; push failure doesn't roll back the transaction |
| **ShedLock** on schedulers | Prevents duplicate work in multi-instance deployments |
| **Fail-open for Redis** | Redis outage doesn't block SOS intake |
| **Order `status` as enum** | Compile-time safety for status values |

---

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| App fails to start, Flyway checksum error | Migration file changed after first run | `docker compose down -v && docker compose up -d` (wipes volumes) |
| WebSocket connection rejected (403) | Invalid/missing JWT or wrong role | Use `admin`/`password`, include `Bearer` prefix in header |
| SOS published but no alert appears | Mosquitto not running or wrong topic | Check `docker compose logs mosquitto`; use topic `sos/{deviceId}` |
| No WebSocket push received | Operator not connected or Redis down | Verify WebSocket connection in Postman; check `docker compose logs app` |
| Alert created but `orderId` is null | No matching booking found | Check `GET /api/alerts/unresolved-ownership`; the alert has `urgent: true` |
| Duplicate alerts created for same SOS | Redis unavailable (fail-open) | Check Redis container; adjust `dedup-ttl-seconds` |
| Escalation not happening | Wrong timezone or clock skew | Escalation uses `Instant.now()` (UTC); all timestamps are UTC |