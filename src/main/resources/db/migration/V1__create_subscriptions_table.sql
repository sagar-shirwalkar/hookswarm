CREATE TABLE subscriptions (
    id          VARCHAR(26)   PRIMARY KEY,
    url         VARCHAR(2048) NOT NULL,
    secret      VARCHAR(68)   NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    max_retries INT           NOT NULL DEFAULT 5,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_status ON subscriptions(status);