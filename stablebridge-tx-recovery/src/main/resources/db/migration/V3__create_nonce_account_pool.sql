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
