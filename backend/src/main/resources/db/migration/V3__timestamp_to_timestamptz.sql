-- members
ALTER TABLE members
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

-- social_accounts
ALTER TABLE social_accounts
    ALTER COLUMN connected_at TYPE TIMESTAMPTZ;

-- daily_sentences (used_at DATE는 유지)
ALTER TABLE daily_sentences
    ALTER COLUMN created_at TYPE TIMESTAMPTZ;

-- game_sessions
ALTER TABLE game_sessions
    ALTER COLUMN cleared_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

-- guess_history
ALTER TABLE guess_history
    ALTER COLUMN created_at TYPE TIMESTAMPTZ;

-- sentence_uploads
ALTER TABLE sentence_uploads
    ALTER COLUMN created_at TYPE TIMESTAMPTZ;

-- refresh_tokens
ALTER TABLE refresh_tokens
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ;
