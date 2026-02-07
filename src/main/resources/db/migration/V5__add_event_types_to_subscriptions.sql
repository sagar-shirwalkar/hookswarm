ALTER TABLE subscriptions
    ADD COLUMN event_types TEXT[] NOT NULL DEFAULT '{}';

COMMENT ON COLUMN subscriptions.event_types IS
    'Event types this subscription listens to. Empty array = all events (wildcard).';