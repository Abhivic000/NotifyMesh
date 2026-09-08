CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id      VARCHAR(100) NOT NULL,
    action        VARCHAR(50)  NOT NULL,
    resource_type VARCHAR(50)  NOT NULL,
    resource_id   VARCHAR(100) NOT NULL,
    metadata      JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
