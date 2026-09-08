-- A durable, queryable record of DLQ'd notifications - the Kafka DLQ topic alone
-- (PRD section 34) isn't something an admin API can list/inspect/retry against, so
-- this consumes that topic into a real table.
CREATE TABLE dlq_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id   VARCHAR(100) NOT NULL,
    event_id          VARCHAR(100) NOT NULL,
    channel           VARCHAR(20)  NOT NULL,
    original_message  JSONB        NOT NULL,
    attempt_count     INTEGER      NOT NULL,
    last_error        TEXT,
    error_code        VARCHAR(50),
    first_failure_at  TIMESTAMPTZ  NOT NULL,
    last_failure_at   TIMESTAMPTZ  NOT NULL,
    correlation_id    VARCHAR(100),
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ,

    CONSTRAINT chk_dlq_items_status CHECK (status IN ('PENDING_REVIEW', 'RETRIED', 'DISCARDED'))
);

CREATE INDEX idx_dlq_items_status ON dlq_items (status);
CREATE INDEX idx_dlq_items_notification_id ON dlq_items (notification_id);
