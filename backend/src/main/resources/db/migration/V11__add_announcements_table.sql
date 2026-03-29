CREATE TABLE announcements
(
    id         BIGSERIAL PRIMARY KEY,
    admin_id   BIGINT REFERENCES members (id),
    title      VARCHAR(200) NOT NULL,
    content    TEXT,
    type       VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    starts_at  TIMESTAMPTZ  NOT NULL,
    ends_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ           DEFAULT NOW()
);
CREATE INDEX idx_announcements_active ON announcements (active, starts_at, ends_at);
