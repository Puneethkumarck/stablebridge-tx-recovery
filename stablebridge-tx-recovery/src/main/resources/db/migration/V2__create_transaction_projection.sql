CREATE TABLE transaction_projection (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_id               UUID NOT NULL,
    chain                   VARCHAR NOT NULL,
    chain_family            VARCHAR NOT NULL,
    from_address            VARCHAR NOT NULL,
    to_address              VARCHAR NOT NULL,
    amount                  NUMERIC(36, 18) NOT NULL,
    asset                   VARCHAR NOT NULL,
    status                  VARCHAR NOT NULL,
    transaction_hash        VARCHAR,
    nonce                   BIGINT,
    gas_price               NUMERIC(36, 18),
    gas_limit               BIGINT,
    gas_used                BIGINT,
    submission_strategy     VARCHAR,
    recovery_count          INT NOT NULL DEFAULT 0,
    current_recovery_action VARCHAR,
    last_recovery_at        TIMESTAMPTZ,
    confirmed_at            TIMESTAMPTZ,
    metadata                JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_transaction_projection_chain_status ON transaction_projection (chain, status);
CREATE INDEX idx_transaction_projection_from_address ON transaction_projection (from_address);
CREATE INDEX idx_transaction_projection_to_address ON transaction_projection (to_address);
CREATE INDEX idx_transaction_projection_intent_id ON transaction_projection (intent_id);
CREATE INDEX idx_transaction_projection_stuck ON transaction_projection (status) WHERE status = 'STUCK';
