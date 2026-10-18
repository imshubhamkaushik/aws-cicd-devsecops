CREATE TABLE IF NOT EXISTS notification_log (
    id          BIGSERIAL PRIMARY KEY,
    recipient   VARCHAR(255) NOT NULL,
    subject     VARCHAR(255) NOT NULL,
    body        TEXT         NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    error       VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notification_log_recipient ON notification_log (recipient);
