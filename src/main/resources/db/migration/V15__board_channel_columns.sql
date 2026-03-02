ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS meeting_board_channel_id BIGINT;

ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS mogakco_board_channel_id BIGINT;
