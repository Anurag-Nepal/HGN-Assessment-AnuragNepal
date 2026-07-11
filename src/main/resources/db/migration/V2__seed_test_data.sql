-- Seed a standard test device
INSERT INTO device (id, external_device_id, status)
VALUES ('123e4567-e89b-12d3-a456-426614174000', 'SAT-TRACKER-001', 'active');

-- Seed a trekker
INSERT INTO trekker (id, name, emergency_contact)
VALUES ('223e4567-e89b-12d3-a456-426614174000', 'Jane Doe', '+1234567890');

-- Seed a group
INSERT INTO group_table (id, name)
VALUES ('323e4567-e89b-12d3-a456-426614174000', 'Everest Base Camp Trekkers');

-- Seed an active order (covers a wide date range so it resolves cleanly during testing)
INSERT INTO trek_order (id, group_id, device_id, start_date, end_date, status)
VALUES ('423e4567-e89b-12d3-a456-426614174000', '323e4567-e89b-12d3-a456-426614174000', '123e4567-e89b-12d3-a456-426614174000', now() - interval '10 days', now() + interval '10 days', 'active');

-- Link them in group_member
INSERT INTO group_member (group_id, trekker_id, order_id)
VALUES ('323e4567-e89b-12d3-a456-426614174000', '223e4567-e89b-12d3-a456-426614174000', '423e4567-e89b-12d3-a456-426614174000');