CREATE TABLE dead_letter_queue (
    id               VARCHAR(26) PRIMARY KEY,
    delivery_task_id VARCHAR(26) NOT NULL REFERENCES delivery_tasks(id),
    event_id         VARCHAR(26) NOT NULL REFERENCES events(id),
    subscription_id  VARCHAR(26) NOT NULL REFERENCES subscriptions(id),
    total_attempts   INT         NOT NULL,
    last_error       TEXT,
    dead_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dlq_subscription ON dead_letter_queue(subscription_id);
CREATE INDEX idx_dlq_dead_at ON dead_letter_queue(dead_at);