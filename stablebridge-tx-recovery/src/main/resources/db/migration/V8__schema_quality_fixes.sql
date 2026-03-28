-- Q2: Auto-update trigger for transaction_projection.updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transaction_projection_updated_at
    BEFORE UPDATE ON transaction_projection
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Q4: Add timestamp columns to nonce_account_pool
ALTER TABLE nonce_account_pool
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TRIGGER trg_nonce_account_pool_updated_at
    BEFORE UPDATE ON nonce_account_pool
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Q5: Composite index on nonce_account_pool (chain, status)
CREATE INDEX idx_nonce_account_pool_chain_status
    ON nonce_account_pool (chain, status);

-- Q19: CHECK constraint ensuring in_flight_count is non-negative
ALTER TABLE address_pool
    ADD CONSTRAINT chk_address_pool_in_flight_count_non_negative
    CHECK (in_flight_count >= 0);
