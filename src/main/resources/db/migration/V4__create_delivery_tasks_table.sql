CREATE TABLE delivery_tasks (
    id              VARCHAR(26)  PRIMARY KEY,
    event_id        VARCHAR(26)  NOT NULL REFERENCES events(id),
    subscription_id VARCHAR(26)  NOT NULL REFERENCES subscriptions(id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count   INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE delivery_tasks
    ADD COLUMN url     TEXT NOT NULL DEFAULT '',
    ADD COLUMN secret  TEXT NOT NULL DEFAULT '',
    ADD COLUMN payload TEXT NOT NULL DEFAULT '';

-- Remove default after adding (defaults are for migration only)
ALTER TABLE delivery_tasks
    ALTER COLUMN url DROP DEFAULT,
    ALTER COLUMN secret DROP DEFAULT,
    ALTER COLUMN payload DROP DEFAULT;

-- The delivery engine will poll on this
CREATE INDEX idx_delivery_tasks_due ON delivery_tasks(next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_delivery_tasks_event ON delivery_tasks(event_id);
CREATE INDEX idx_delivery_tasks_subscription ON delivery_tasks(subscription_id);