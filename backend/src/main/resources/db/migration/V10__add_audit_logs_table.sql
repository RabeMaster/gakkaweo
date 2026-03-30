CREATE TABLE audit_logs
(
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT REFERENCES members (id),
    action      VARCHAR(100) NOT NULL,
    target_type VARCHAR(50)  NOT NULL,
    target_id   VARCHAR(255),
    detail      TEXT,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_audit_logs_admin ON audit_logs (admin_id);
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at);
