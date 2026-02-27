ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS dashboard_channel_id BIGINT;

ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS dashboard_message_id BIGINT;
