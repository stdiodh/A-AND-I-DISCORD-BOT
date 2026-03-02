ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS meeting_opener_role_id BIGINT;
