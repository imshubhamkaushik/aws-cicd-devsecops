-- cart_items_cart_id_product_id_key already provides a B-tree index whose leading
-- column is cart_id. That unique index supports cart_id lookups and ON DELETE CASCADE,
-- so the standalone idx_cart_items_cart_id index only duplicates write/storage cost.
DROP INDEX IF EXISTS idx_cart_items_cart_id;
