-- Merge Incident into Alert
ALTER TABLE alert ADD COLUMN claimed_by VARCHAR(64);
ALTER TABLE alert ADD COLUMN claimed_at TIMESTAMPTZ;
ALTER TABLE alert ADD COLUMN resolved_at TIMESTAMPTZ;
ALTER TABLE alert ADD COLUMN notes TEXT;

-- Migrate existing incident data into alert
UPDATE alert a
SET claimed_by = i.claimed_by,
    claimed_at = i.claimed_at,
    resolved_at = i.resolved_at,
    notes = i.notes
FROM incident i
WHERE i.alert_id = a.id;

-- Drop incident table (FK drops automatically)
DROP TABLE incident;

-- Composite indexes for booking resolution and latest-alert queries
CREATE INDEX ix_trek_order_device_status_dates
    ON trek_order (device_id, status, start_date, end_date);
CREATE INDEX ix_alert_device_created
    ON alert (device_id, created_at DESC);

-- Enforce valid order statuses as uppercase
UPDATE trek_order SET status = UPPER(status);
ALTER TABLE trek_order ADD CONSTRAINT chk_trek_order_status
    CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'));

-- ShedLock table for distributed scheduler locking
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
