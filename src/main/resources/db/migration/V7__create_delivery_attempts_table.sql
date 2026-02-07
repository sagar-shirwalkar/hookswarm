CREATE TABLE delivery_attempts (
    id               VARCHAR(26)  PRIMARY KEY,
    delivery_task_id VARCHAR(26)  NOT NULL REFERENCES delivery_tasks(id),
    attempt_number   INT          NOT NULL,
    http_status_code INT,
    response_body    TEXT,
    latency_ms       BIGINT       NOT NULL,
    error_message    TEXT,
    attempted_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delivery_attempts_task ON delivery_attempts(delivery_task_id);