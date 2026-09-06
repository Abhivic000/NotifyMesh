CREATE TABLE templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key VARCHAR(100) NOT NULL,
    channel     VARCHAR(20)  NOT NULL,
    version     INTEGER      NOT NULL,
    subject     VARCHAR(255),
    body        TEXT         NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_templates_key_channel_version UNIQUE (template_key, channel, version)
);
