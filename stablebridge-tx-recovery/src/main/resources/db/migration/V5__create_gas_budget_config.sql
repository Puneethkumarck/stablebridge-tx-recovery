CREATE TABLE gas_budget_config (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chain        VARCHAR NOT NULL UNIQUE,
    percentage   NUMERIC(10, 6) NOT NULL DEFAULT 0.01,
    absolute_min NUMERIC(18, 2) NOT NULL DEFAULT 5.0,
    absolute_max NUMERIC(18, 2) NOT NULL DEFAULT 500.0
);

INSERT INTO gas_budget_config (chain, percentage, absolute_min, absolute_max) VALUES
    ('*', 0.01, 5.0, 500.0);
