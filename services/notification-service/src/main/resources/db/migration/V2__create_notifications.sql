CREATE TABLE notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            VARCHAR(100) NOT NULL,
    user_id             VARCHAR(100) NOT NULL,
    channel             VARCHAR(20)  NOT NULL,
    template_id         UUID         NOT NULL REFERENCES templates (id),
    recipient           VARCHAR(255) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    retry_count         INTEGER      NOT NULL DEFAULT 0,
    provider_message_id VARCHAR(255),
    idempotency_key     VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at             TIMESTAMPTZ,
    failed_at           TIMESTAMPTZ,

    -- Idempotency Layer 3 (PRD section 20): one notification per (event, channel) -
    -- a redelivered Kafka message for an already-processed event must not create a
    -- second notification on the same channel.
    CONSTRAINT uq_notifications_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX idx_notifications_event_id   ON notifications (event_id);
CREATE INDEX idx_notifications_user_id    ON notifications (user_id);
CREATE INDEX idx_notifications_status     ON notifications (status);
CREATE INDEX idx_notifications_channel    ON notifications (channel);
CREATE INDEX idx_notifications_created_at ON notifications (created_at);
