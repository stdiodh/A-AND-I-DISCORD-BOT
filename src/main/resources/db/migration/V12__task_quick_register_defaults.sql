ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS default_task_channel_id BIGINT;

ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS default_notify_role_id BIGINT;

CREATE TABLE IF NOT EXISTS guild_user_task_preferences (
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_task_channel_id BIGINT,
    last_notify_role_id BIGINT,
    last_mention_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (guild_id, user_id),
    CONSTRAINT fk_user_task_prefs_guild
        FOREIGN KEY (guild_id) REFERENCES guild_config (guild_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_task_prefs_guild_updated
    ON guild_user_task_preferences (guild_id, updated_at DESC);
