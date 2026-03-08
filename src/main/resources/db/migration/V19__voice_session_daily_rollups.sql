CREATE TABLE IF NOT EXISTS voice_session_daily_rollups (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    date_local DATE NOT NULL,
    total_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_voice_session_daily_rollups_guild_user_date
        UNIQUE (guild_id, user_id, date_local),
    CONSTRAINT ck_voice_session_daily_rollups_non_negative
        CHECK (total_seconds >= 0)
);

CREATE INDEX IF NOT EXISTS idx_voice_session_daily_rollups_guild_date
    ON voice_session_daily_rollups (guild_id, date_local);

INSERT INTO voice_session_daily_rollups (
    guild_id,
    user_id,
    date_local,
    total_seconds,
    created_at,
    updated_at
)
SELECT
    sliced.guild_id,
    sliced.user_id,
    sliced.date_local,
    SUM(sliced.seconds)::BIGINT AS total_seconds,
    NOW(),
    NOW()
FROM (
    SELECT
        v.guild_id,
        v.user_id,
        day_point::DATE AS date_local,
        EXTRACT(
            EPOCH FROM (
                LEAST(
                    COALESCE(v.left_at, v.joined_at),
                    (day_point + INTERVAL '1 day') AT TIME ZONE 'Asia/Seoul'
                ) - GREATEST(
                    v.joined_at,
                    day_point AT TIME ZONE 'Asia/Seoul'
                )
            )
        )::BIGINT AS seconds
    FROM voice_sessions v
    JOIN LATERAL generate_series(
        date_trunc('day', v.joined_at AT TIME ZONE 'Asia/Seoul'),
        date_trunc('day', COALESCE(v.left_at, v.joined_at) AT TIME ZONE 'Asia/Seoul'),
        INTERVAL '1 day'
    ) day_point ON TRUE
    WHERE COALESCE(v.left_at, v.joined_at) > v.joined_at
) sliced
WHERE sliced.seconds > 0
GROUP BY sliced.guild_id, sliced.user_id, sliced.date_local
ON CONFLICT (guild_id, user_id, date_local)
DO UPDATE
   SET total_seconds = EXCLUDED.total_seconds,
       updated_at = NOW();
