CREATE TABLE events (
    id              VARCHAR(26)   PRIMARY KEY,
    event_type      VARCHAR(255)  NOT NULL,
    payload         TEXT          NOT NULL,
    idempotency_key VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_events_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_events_type ON events(event_type);
CREATE INDEX idx_events_created ON events(created_at DESC);