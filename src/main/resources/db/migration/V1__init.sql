CREATE TABLE guild_config (
    guild_id BIGINT PRIMARY KEY,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul',
    admin_role_id BIGINT,
    mogakco_active_minutes INTEGER NOT NULL DEFAULT 30
);

CREATE TABLE agenda_links (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    date_local DATE NOT NULL,
    title VARCHAR(255),
    url TEXT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_agenda_links_guild_date UNIQUE (guild_id, date_local),
    CONSTRAINT fk_agenda_links_guild_config
        FOREIGN KEY (guild_id) REFERENCES guild_config (guild_id)
);

CREATE INDEX idx_agenda_links_guild_date_local
    ON agenda_links (guild_id, date_local);

CREATE TABLE mogakco_channels (
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    PRIMARY KEY (guild_id, channel_id),
    CONSTRAINT fk_mogakco_channels_guild_config
        FOREIGN KEY (guild_id) REFERENCES guild_config (guild_id)
);

CREATE TABLE voice_sessions (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    left_at TIMESTAMPTZ,
    duration_sec INTEGER,
    CONSTRAINT fk_voice_sessions_guild_config
        FOREIGN KEY (guild_id) REFERENCES guild_config (guild_id)
);

CREATE INDEX idx_voice_sessions_guild_joined_at
    ON voice_sessions (guild_id, joined_at);

CREATE INDEX idx_voice_sessions_guild_user_joined_at
    ON voice_sessions (guild_id, user_id, joined_at);
