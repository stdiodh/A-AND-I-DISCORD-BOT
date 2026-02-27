CREATE TABLE assignment_tasks (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    verify_url TEXT NOT NULL,
    remind_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notified_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_assignment_tasks_guild_config
        FOREIGN KEY (guild_id) REFERENCES guild_config (guild_id),
    CONSTRAINT ck_assignment_tasks_status
        CHECK (status IN ('PENDING', 'DONE', 'CANCELED')),
    CONSTRAINT ck_assignment_tasks_title_not_blank
        CHECK (BTRIM(title) <> ''),
    CONSTRAINT ck_assignment_tasks_verify_url_not_blank
        CHECK (BTRIM(verify_url) <> '')
);

CREATE INDEX idx_assignment_tasks_status_remind_notified
    ON assignment_tasks (status, remind_at, notified_at);

CREATE INDEX idx_assignment_tasks_guild_status_remind
    ON assignment_tasks (guild_id, status, remind_at);

ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS admin_role_id BIGINT;

ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul';
