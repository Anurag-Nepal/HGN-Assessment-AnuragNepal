CREATE TABLE device (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_device_id VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL DEFAULT 'active'
);

CREATE TABLE trekker (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL,
    emergency_contact VARCHAR(128)
);

CREATE TABLE group_table (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL
);

CREATE TABLE trek_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES group_table(id),
    device_id UUID NOT NULL REFERENCES device(id),
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL
);

CREATE TABLE group_member (
    group_id UUID NOT NULL REFERENCES group_table(id),
    trekker_id UUID NOT NULL REFERENCES trekker(id),
    order_id UUID NOT NULL REFERENCES trek_order(id),
    PRIMARY KEY (group_id, trekker_id, order_id)
);

CREATE TABLE alert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES device(id),
    order_id UUID REFERENCES trek_order(id),
    group_id UUID REFERENCES group_table(id),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    device_timestamp TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    urgent BOOLEAN NOT NULL DEFAULT false,
    resolved_via_grace_window BOOLEAN NOT NULL DEFAULT false,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_alert_status_created ON alert (status, created_at);
CREATE INDEX ix_alert_unresolved ON alert (order_id) WHERE order_id IS NULL;

CREATE TABLE incident (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL UNIQUE REFERENCES alert(id),
    claimed_by VARCHAR(64),
    claimed_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    notes TEXT
);