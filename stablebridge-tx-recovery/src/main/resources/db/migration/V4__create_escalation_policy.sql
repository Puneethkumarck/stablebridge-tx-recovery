CREATE TABLE escalation_policy (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chain              VARCHAR NOT NULL,
    level              INT NOT NULL CHECK (level >= 1),
    stuck_threshold_ms BIGINT NOT NULL CHECK (stuck_threshold_ms > 0),
    gas_multiplier     NUMERIC NOT NULL CHECK (gas_multiplier > 0),
    requires_human     BOOLEAN NOT NULL DEFAULT FALSE,
    description        VARCHAR,
    UNIQUE (chain, level)
);

INSERT INTO escalation_policy (chain, level, stuck_threshold_ms, gas_multiplier, requires_human, description) VALUES
    ('*', 1, 300000,  1.0,  FALSE, 'Initial detection'),
    ('*', 2, 600000,  1.25, FALSE, 'First speed-up'),
    ('*', 3, 1200000, 1.5,  FALSE, 'Second speed-up'),
    ('*', 4, 1800000, 2.0,  TRUE,  'Requires approval'),
    ('*', 5, 3600000, 3.0,  TRUE,  'Emergency escalation'),
    ('*', 6, 600000,  1.0,  TRUE,  'High-value initial review'),
    ('*', 7, 1200000, 1.5,  TRUE,  'High-value escalation'),
    ('*', 8, 1800000, 2.0,  TRUE,  'High-value emergency');
