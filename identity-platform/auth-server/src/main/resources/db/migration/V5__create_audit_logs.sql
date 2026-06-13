-- V5: Create audit_logs table

CREATE TABLE IF NOT EXISTS audit_logs (
    id                          UUID PRIMARY KEY,
    organization_id             UUID,
    event_type                  VARCHAR(60)   NOT NULL,
    actor_user_id               UUID,
    actor_email                 VARCHAR(255),
    target_id                   VARCHAR(255),
    target_type                 VARCHAR(100),
    metadata                    TEXT,
    ip_address                  VARCHAR(45),
    user_agent                  VARCHAR(500),
    result                      VARCHAR(20)   NOT NULL DEFAULT 'SUCCESS',
    failure_reason              VARCHAR(500),
    occurred_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_org_time ON audit_logs(organization_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_user     ON audit_logs(actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_event    ON audit_logs(event_type);
