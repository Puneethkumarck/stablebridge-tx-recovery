CREATE TABLE transaction_intent (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_id                VARCHAR(36)    NOT NULL,
    chain                    VARCHAR(50)    NOT NULL,
    to_address               VARCHAR(66)    NOT NULL,
    amount                   NUMERIC(36, 18) NOT NULL,
    token                    VARCHAR(100)   NOT NULL,
    token_decimals           INT            NOT NULL DEFAULT 0,
    raw_amount               NUMERIC(78, 0),
    token_contract_address   VARCHAR(66),
    strategy                 VARCHAR(30),
    metadata                 JSONB,
    batch_id                 VARCHAR(36),
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT uq_transaction_intent_intent_id UNIQUE (intent_id)
);

CREATE INDEX idx_transaction_intent_batch_id ON transaction_intent (batch_id) WHERE batch_id IS NOT NULL;
