-- 소셜 프로바이더 ENUM 타입
CREATE TYPE social_provider AS ENUM ('KAKAO', 'GOOGLE', 'NAVER');

-- 회원
CREATE TABLE members
(
    id          BIGSERIAL PRIMARY KEY,
    public_id   UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    nickname    VARCHAR(50) NOT NULL,
    profile_url VARCHAR(500),
    role       VARCHAR(20) DEFAULT 'USER',
    created_at TIMESTAMP   DEFAULT NOW(),
    updated_at TIMESTAMP   DEFAULT NOW()
);

-- 소셜 계정 연동
CREATE TABLE social_accounts
(
    id           BIGSERIAL PRIMARY KEY,
    member_id   BIGINT       NOT NULL REFERENCES members (id),
    provider     social_provider NOT NULL,
    provider_id VARCHAR(100) NOT NULL,
    email        VARCHAR(255),
    connected_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (provider, provider_id)
);

-- 데일리 문장
CREATE TABLE daily_sentences
(
    id            BIGSERIAL PRIMARY KEY,
    public_id     UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    sentence      TEXT        NOT NULL,
    used_at       DATE UNIQUE,
    embedding     BYTEA,
    model_version VARCHAR(100),
    status        VARCHAR(20)          DEFAULT 'ACTIVE',
    created_at    TIMESTAMP            DEFAULT NOW()
);

-- 게임 세션
CREATE TABLE game_sessions
(
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    member_id       BIGINT NOT NULL REFERENCES members (id),
    sentence_id     BIGINT NOT NULL REFERENCES daily_sentences (id),
    status          VARCHAR(20)   DEFAULT 'IN_PROGRESS',
    best_similarity DECIMAL(5, 2) DEFAULT 0,
    attempt_count   INT           DEFAULT 0,
    cleared_at      TIMESTAMP,
    version         INT           DEFAULT 0,
    created_at      TIMESTAMP     DEFAULT NOW(),
    updated_at      TIMESTAMP     DEFAULT NOW(),
    UNIQUE (member_id, sentence_id)
);

-- 추측 히스토리
CREATE TABLE guess_history
(
    id             BIGSERIAL PRIMARY KEY,
    session_id     BIGINT        NOT NULL REFERENCES game_sessions (id),
    guess_text     TEXT          NOT NULL,
    similarity     DECIMAL(5, 2) NOT NULL,
    attempt_number INT           NOT NULL,
    created_at     TIMESTAMP DEFAULT NOW()
);

-- 문장 업로드 이력
CREATE TABLE sentence_uploads
(
    id           BIGSERIAL PRIMARY KEY,
    admin_id     BIGINT NOT NULL REFERENCES members (id),
    file_name    VARCHAR(255) NOT NULL,
    record_count INT    NOT NULL,
    created_at   TIMESTAMP DEFAULT NOW()
);

-- Refresh Token 관리
CREATE TABLE refresh_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT      NOT NULL REFERENCES members (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id  UUID        NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    revoked    BOOLEAN   DEFAULT FALSE
);

-- 추가 인덱스
CREATE INDEX idx_game_sessions_ranking ON game_sessions (sentence_id, status, best_similarity);
CREATE INDEX idx_guess_history_session ON guess_history (session_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
