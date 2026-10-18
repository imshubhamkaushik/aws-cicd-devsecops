-- catalog-svc schema

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(10,2) NOT NULL,
    category VARCHAR(100) DEFAULT 'GENERAL'::character varying NOT NULL,
    stock_quantity INTEGER DEFAULT 0 NOT NULL,
    owner_id BIGINT DEFAULT 0 NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    image_url VARCHAR(500),
    moderation_status VARCHAR(30) DEFAULT 'PUBLISHED'::character varying NOT NULL
);
CREATE INDEX idx_products_category ON products (category);
CREATE INDEX idx_products_lower_category_price ON products (lower((category)::TEXT), price);
CREATE INDEX idx_products_moderation_status ON products (moderation_status);
CREATE INDEX idx_products_owner_id ON products (owner_id);
CREATE INDEX idx_products_price ON products (price);
