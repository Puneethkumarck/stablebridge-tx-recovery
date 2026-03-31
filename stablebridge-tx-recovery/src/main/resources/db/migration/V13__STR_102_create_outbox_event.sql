CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        VARCHAR(255)  NOT NULL,
    intent_id       VARCHAR(36)   NOT NULL,
    topic           VARCHAR(255)  NOT NULL,
    partition_key   VARCHAR(255)  NOT NULL,
    payload         TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    retry_count     INT           NOT NULL DEFAULT 0,

    CONSTRAINT uq_outbox_event_event_id UNIQUE (event_id)
);

CREATE INDEX idx_outbox_event_pending ON outbox_event (created_at) WHERE status = 'PENDING';
