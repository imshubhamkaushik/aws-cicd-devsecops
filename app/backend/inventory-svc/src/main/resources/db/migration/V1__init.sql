-- inventory-svc schema

CREATE TABLE inventory_items (
    product_id BIGINT NOT NULL PRIMARY KEY,
    quantity INTEGER NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT inventory_items_quantity_check CHECK ((quantity >= 0))
);

CREATE TABLE inventory_operations (
    operation_id VARCHAR(120) NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    delta INTEGER NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX idx_inventory_operations_created_at ON inventory_operations (created_at);
