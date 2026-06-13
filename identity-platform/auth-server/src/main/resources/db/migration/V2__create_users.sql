-- V2: Create users table

CREATE TABLE IF NOT EXISTS users (
    id                          UUID PRIMARY KEY,
    organization_id             UUID          NOT NULL REFERENCES organizations(id),
    email                       VARCHAR(255)  NOT NULL,
    first_name                  VARCHAR(100)  NOT NULL,
    last_name                   VARCHAR(100)  NOT NULL,
    password_hash               VARCHAR(255),
    status                      VARCHAR(30)   NOT NULL DEFAULT 'PENDING_VERIFICATION',
    external_subject_id         VARCHAR(255),
    auth_provider               VARCHAR(30)   NOT NULL DEFAULT 'LOCAL',
    avatar_url                  VARCHAR(500),
    mfa_enabled                 BOOLEAN       NOT NULL DEFAULT FALSE,
    mfa_secret                  VARCHAR(255),
    last_login_at               TIMESTAMP WITH TIME ZONE,
    email_verified_at           TIMESTAMP WITH TIME ZONE,
    invite_token                VARCHAR(255),
    invite_token_expires_at     TIMESTAMP WITH TIME ZONE,
    deleted                     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

ALTER TABLE users ADD CONSTRAINT uq_user_email_org UNIQUE (email, organization_id);
CREATE INDEX IF NOT EXISTS idx_user_org       ON users(organization_id);
CREATE INDEX IF NOT EXISTS idx_user_email_org ON users(email, organization_id);
CREATE INDEX IF NOT EXISTS idx_user_ext_sub   ON users(external_subject_id);
