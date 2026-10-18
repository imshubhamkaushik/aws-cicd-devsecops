-- cart-svc schema

CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    added_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
ALTER TABLE cart_items ADD CONSTRAINT cart_items_cart_id_product_id_key UNIQUE (cart_id, product_id);
CREATE INDEX idx_cart_items_cart_id ON cart_items (cart_id);

CREATE TABLE carts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

ALTER TABLE cart_items ADD CONSTRAINT cart_items_cart_id_fkey FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE;
