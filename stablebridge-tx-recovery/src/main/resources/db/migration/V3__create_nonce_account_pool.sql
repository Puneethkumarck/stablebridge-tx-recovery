CREATE TABLE nonce_account_pool (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nonce_account       VARCHAR NOT NULL,
    authority_address   VARCHAR NOT NULL,
    chain               VARCHAR NOT NULL,
    status              VARCHAR NOT NULL,
    current_nonce_value VARCHAR,
    allocated_to_tx     UUID,
    UNIQUE (nonce_account, chain)
);

CREATE INDEX idx_nonce_account_pool_allocated_to_tx ON nonce_account_pool (allocated_to_tx) WHERE allocated_to_tx IS NOT NULL;
