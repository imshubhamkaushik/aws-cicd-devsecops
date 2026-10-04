-- payment-svc schema

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    method VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reference VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    idempotency_key VARCHAR(64)
);
CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE UNIQUE INDEX uq_payments_user_idempotency ON payments (requested_by_user_id, idempotency_key) WHERE (idempotency_key IS NOT NULL);

CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    original_payment_id BIGINT NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    reference VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    idempotency_key VARCHAR(100)
);
CREATE INDEX idx_refunds_order_id ON refunds (order_id);
CREATE UNIQUE INDEX uq_refunds_order_idempotency_key ON refunds (order_id, idempotency_key) WHERE (idempotency_key IS NOT NULL);

ALTER TABLE refunds ADD CONSTRAINT refunds_original_payment_id_fkey FOREIGN KEY (original_payment_id) REFERENCES payments(id);
