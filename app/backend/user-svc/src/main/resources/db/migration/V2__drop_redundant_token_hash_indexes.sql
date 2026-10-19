-- Each token_hash column already has a UNIQUE constraint, which creates a unique index.
-- These standalone indexes duplicate those unique indexes without adding a new access path.
DROP INDEX IF EXISTS idx_evt_token_hash;
DROP INDEX IF EXISTS idx_prt_token_hash;
DROP INDEX IF EXISTS idx_refresh_tokens_token_hash;
