CREATE TABLE outbox (
    id           VARCHAR(26)  PRIMARY KEY,
    event_id     VARCHAR(26)  NOT NULL REFERENCES events(id),
    processed    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unprocessed ON outbox(created_at)
    WHERE processed = FALSE;