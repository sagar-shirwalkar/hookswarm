ALTER TABLE outbox
    ADD COLUMN event_type VARCHAR(255);

-- Backfill from events table if any rows exist
UPDATE outbox o
SET event_type = e.event_type
FROM events e
WHERE o.event_id = e.id
  AND o.event_type IS NULL;

-- Now enforce NOT NULL
ALTER TABLE outbox
    ALTER COLUMN event_type SET NOT NULL;

CREATE INDEX idx_outbox_event_type ON outbox(event_type)
    WHERE processed = FALSE;