-- FK 컬럼 누락 인덱스 (PostgreSQL은 FK 자동 인덱스를 만들지 않음)
CREATE INDEX IF NOT EXISTS idx_sentence_uploads_admin
    ON sentence_uploads (admin_id);

CREATE INDEX IF NOT EXISTS idx_social_accounts_member
    ON social_accounts (member_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_member
    ON refresh_tokens (member_id);

-- 복합 인덱스: 등호 필터 + 정렬 커버
CREATE INDEX IF NOT EXISTS idx_game_sessions_member_created
    ON game_sessions (member_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created
    ON audit_logs (action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_members_banned_created
    ON members (banned, created_at DESC);

-- 기존 단일 인덱스는 복합의 접두사에 포함되므로 DROP 후 복합으로 대체
DROP INDEX IF EXISTS idx_guess_history_session;
CREATE INDEX IF NOT EXISTS idx_guess_history_session_attempt
    ON guess_history (session_id, attempt_number);
