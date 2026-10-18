-- Order notifications are recorded atomically with order state changes and relayed with retry/backoff.
CREATE TABLE order_event_outbox (
    id BIGSERIAL PRIMARY KEY,
    routing_key VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_event_outbox_due
    ON order_event_outbox (status, next_attempt_at, created_at);
