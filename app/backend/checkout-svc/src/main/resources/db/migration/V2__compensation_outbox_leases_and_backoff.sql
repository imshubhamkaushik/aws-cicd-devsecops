-- Keep remote inventory calls outside DB transactions while preventing competing replicas
-- from processing the same row. Stale PROCESSING rows are reclaimed after a bounded lease.
ALTER TABLE compensation_outbox
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX idx_compensation_outbox_due
    ON compensation_outbox (status, next_attempt_at, created_at);
