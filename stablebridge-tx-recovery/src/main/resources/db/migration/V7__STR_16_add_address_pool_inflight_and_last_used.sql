ALTER TABLE address_pool ADD COLUMN current_nonce  BIGINT NOT NULL DEFAULT 0;
ALTER TABLE address_pool ADD COLUMN in_flight_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE address_pool ADD COLUMN last_used_at   TIMESTAMPTZ;

CREATE INDEX idx_address_pool_candidate
    ON address_pool (chain, tier, status, in_flight_count ASC, last_used_at ASC NULLS FIRST);
