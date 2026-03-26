CREATE TABLE address_pool (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    address         VARCHAR NOT NULL,
    chain           VARCHAR NOT NULL,
    chain_family    VARCHAR NOT NULL,
    tier            VARCHAR NOT NULL,
    status          VARCHAR NOT NULL,
    signer_endpoint VARCHAR,
    registered_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    retired_at      TIMESTAMPTZ,
    UNIQUE (address, chain)
);

CREATE INDEX idx_address_pool_chain_status ON address_pool (chain, status);
