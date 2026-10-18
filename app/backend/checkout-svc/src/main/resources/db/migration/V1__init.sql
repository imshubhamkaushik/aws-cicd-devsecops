-- checkout-svc schema

CREATE TABLE compensation_outbox (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    product_id BIGINT,
    delta INTEGER,
    reason VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts INTEGER DEFAULT 0 NOT NULL,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    operation_id VARCHAR(120),
    undo_of VARCHAR(120),
    CONSTRAINT chk_compensation_target CHECK ((((type)::TEXT = 'RELEASE_STOCK'::TEXT) AND (product_id IS NOT NULL) AND (delta IS NOT NULL)))
);
CREATE INDEX idx_compensation_outbox_status ON compensation_outbox (status);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    seller_id BIGINT
);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_seller_id ON order_items (seller_id);

CREATE TABLE order_status_events (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    note VARCHAR(200),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX idx_order_status_events_order_id ON order_status_events (order_id);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING_PAYMENT'::character varying NOT NULL,
    total_amount NUMERIC(12,2) DEFAULT 0 NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    idempotency_key VARCHAR(64),
    shipping_label VARCHAR(40),
    shipping_line1 VARCHAR(200),
    shipping_line2 VARCHAR(200),
    shipping_city VARCHAR(100),
    shipping_state VARCHAR(100),
    shipping_pincode VARCHAR(12),
    shipping_phone VARCHAR(20),
    payment_method VARCHAR(20),
    payment_reference VARCHAR(100),
    customer_email VARCHAR(255),
    payment_started_at TIMESTAMPTZ
);
CREATE INDEX idx_orders_payment_processing ON orders (payment_started_at) WHERE ((status)::TEXT = 'PAYMENT_PROCESSING'::TEXT);
CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE UNIQUE INDEX uq_orders_user_idempotency ON orders (user_id, idempotency_key);

ALTER TABLE order_items ADD CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;
ALTER TABLE order_status_events ADD CONSTRAINT order_status_events_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;
