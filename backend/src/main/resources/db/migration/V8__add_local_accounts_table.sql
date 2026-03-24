CREATE TABLE local_accounts
(
    id            BIGSERIAL PRIMARY KEY,
    member_id     BIGINT      NOT NULL UNIQUE REFERENCES members (id),
    username      VARCHAR(30) NOT NULL UNIQUE,
    password_hash VARCHAR(72) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_local_accounts_username_lower ON local_accounts (LOWER(username));
