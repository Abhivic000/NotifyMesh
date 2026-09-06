CREATE TABLE events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id       VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    source         VARCHAR(100) NOT NULL,
    version        VARCHAR(10)  NOT NULL,
    correlation_id VARCHAR(100),
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ,

    CONSTRAINT uq_events_event_id UNIQUE (event_id)
);

-- uq_events_event_id above already gives us an index on event_id for free
-- (unique constraints are backed by an index) - PRD Layer 1 idempotency.
CREATE INDEX idx_events_event_type ON events (event_type);
CREATE INDEX idx_events_source     ON events (source);
CREATE INDEX idx_events_created_at ON events (created_at);
CREATE INDEX idx_events_status     ON events (status);
