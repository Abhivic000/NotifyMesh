-- notification_id is a soft reference (no FK) - it points at a row in the
-- notification-service schema, which this service does not own and must not
-- constrain against directly. See the Phase 4 write-up for why.
CREATE TABLE delivery_attempts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id   VARCHAR(100) NOT NULL,
    attempt_number    INTEGER      NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    provider_response TEXT,
    error_code        VARCHAR(50),
    attempted_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_attempts_notification_id ON delivery_attempts (notification_id);
CREATE INDEX idx_delivery_attempts_attempted_at     ON delivery_attempts (attempted_at);
