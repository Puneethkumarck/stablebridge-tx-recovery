ALTER TABLE transaction_projection ADD CONSTRAINT uq_transaction_projection_intent_id UNIQUE (intent_id);

ALTER TABLE escalation_policy ALTER COLUMN gas_multiplier TYPE NUMERIC(10, 4);
