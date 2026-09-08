-- Holds a job awaiting its next retry attempt (PRD section 32). A scheduled poller
-- (RetryPoller) reads due rows and re-attempts delivery directly, in-process - see
-- the Phase 6 design write-up for why this is a DB-scheduled queue rather than
-- blocking in-process sleeps or per-attempt Kafka retry topics.
CREATE TABLE retry_queue (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id   VARCHAR(100) NOT NULL,
    job_payload        JSONB        NOT NULL,
    attempt_number     INTEGER      NOT NULL,
    next_attempt_at    TIMESTAMPTZ  NOT NULL,
    last_error         TEXT,
    first_failure_at   TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_retry_queue_notification UNIQUE (notification_id)
);

CREATE INDEX idx_retry_queue_next_attempt ON retry_queue (next_attempt_at);
