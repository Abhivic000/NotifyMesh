-- Not one of the PRD's explicitly-listed tables (section 22 doesn't define a schema
-- for this) - PRD section 29 requires the *behavior* ("evaluate preferences before
-- creating channel jobs") without dictating a schema, so this is designed here.
-- Default-permissive: a missing row for (user_id, channel) means "enabled" - see
-- NotificationPreferenceRepository.
CREATE TABLE notification_preferences (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    VARCHAR(100) NOT NULL,
    channel    VARCHAR(20)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_preferences_user_channel UNIQUE (user_id, channel)
);
