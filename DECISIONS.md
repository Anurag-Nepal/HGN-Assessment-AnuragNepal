# HGN SOS Alert Service - Decisions & Tradeoffs

## Redis vs. DB Deduplication
We opted for Redis deduplication (using atomic `SETNX` semantics) rather than a strict database unique constraint. 
- **Why:** During an emergency, identical retry signals might bombard the intake service (due to device auto-retry every 20-60s on lack of ack or QoS 1 redelivery). A Redis cache acts as a fast, first-line defense that rejects duplicates without burning costly database connections or hitting the heavier PostgreSQL transaction logs. 
- **Tradeoff:** A transient Redis outage could technically let duplicates through, but the system favors availability and throughput. A database unique constraint can be added back later as a deferred hardening item.

## Dedup TTL Value (60s)
The `sos.dedup-ttl-seconds` is set to 60 seconds by default.
- **Why:** Physical SOS devices typically auto-retry transmissions within a 20s to 60s window if they don't receive an acknowledgment. A 60-second window is long enough to safely squash identical retry storms from the exact same device location, but short enough that if a trekker presses the SOS button *again* 2 minutes later from a new location, it correctly registers as a fresh ping/update.

## Resolution Grace Window (1 day)
The `sos.resolution-grace-days` is set to 1 day.
- **Why:** Trekkers often start their journeys slightly earlier than the exact timestamp, or return slightly later than scheduled. Failing to resolve an SOS just because a trekker was 4 hours late returning would be a critical failure. Expanding the search to +/- 1 day safely covers this.

## Escalation Window (300s / 5 min)
The `sos.escalation-window-seconds` is configured for 300 seconds (5 minutes).
- **Why:** SOS signals are extremely time-sensitive. If an operator has not actively claimed an OPEN alert within 5 minutes, it is automatically escalated via a background `@Scheduled` job. This ensures that no SOS falls through the cracks due to operator shift changes or dashboard inattention.