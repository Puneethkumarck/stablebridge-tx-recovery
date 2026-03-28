ALTER TABLE address_pool ALTER COLUMN address TYPE VARCHAR(66);
ALTER TABLE address_pool ALTER COLUMN chain TYPE VARCHAR(50);
ALTER TABLE address_pool ALTER COLUMN chain_family TYPE VARCHAR(20);
ALTER TABLE address_pool ALTER COLUMN tier TYPE VARCHAR(30);
ALTER TABLE address_pool ALTER COLUMN status TYPE VARCHAR(30);
ALTER TABLE address_pool ALTER COLUMN signer_endpoint TYPE VARCHAR(256);

ALTER TABLE transaction_projection ALTER COLUMN chain TYPE VARCHAR(50);
ALTER TABLE transaction_projection ALTER COLUMN chain_family TYPE VARCHAR(20);
ALTER TABLE transaction_projection ALTER COLUMN from_address TYPE VARCHAR(66);
ALTER TABLE transaction_projection ALTER COLUMN to_address TYPE VARCHAR(66);
ALTER TABLE transaction_projection ALTER COLUMN asset TYPE VARCHAR(100);
ALTER TABLE transaction_projection ALTER COLUMN status TYPE VARCHAR(30);
ALTER TABLE transaction_projection ALTER COLUMN transaction_hash TYPE VARCHAR(128);
ALTER TABLE transaction_projection ALTER COLUMN submission_strategy TYPE VARCHAR(30);
ALTER TABLE transaction_projection ALTER COLUMN current_recovery_action TYPE VARCHAR(30);

ALTER TABLE nonce_account_pool ALTER COLUMN nonce_account TYPE VARCHAR(44);
ALTER TABLE nonce_account_pool ALTER COLUMN authority_address TYPE VARCHAR(44);
ALTER TABLE nonce_account_pool ALTER COLUMN chain TYPE VARCHAR(50);
ALTER TABLE nonce_account_pool ALTER COLUMN status TYPE VARCHAR(30);
ALTER TABLE nonce_account_pool ALTER COLUMN current_nonce_value TYPE VARCHAR(128);

ALTER TABLE escalation_policy ALTER COLUMN chain TYPE VARCHAR(50);
ALTER TABLE escalation_policy ALTER COLUMN description TYPE VARCHAR(256);

ALTER TABLE gas_budget_config ALTER COLUMN chain TYPE VARCHAR(50);
