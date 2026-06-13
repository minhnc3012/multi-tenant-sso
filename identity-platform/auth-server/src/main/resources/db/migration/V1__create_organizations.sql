-- V1: Create organizations table (tenant management)

CREATE TABLE IF NOT EXISTS organizations (
    id                          UUID PRIMARY KEY,
    name                        VARCHAR(100)  NOT NULL,
    slug                        VARCHAR(50)   NOT NULL,
    primary_domain              VARCHAR(255),
    status                      VARCHAR(30)   NOT NULL DEFAULT 'PENDING_SETUP',
    mfa_required                BOOLEAN       NOT NULL DEFAULT FALSE,
    self_registration_allowed   BOOLEAN       NOT NULL DEFAULT FALSE,
    logo_url                    VARCHAR(500),
    primary_color               VARCHAR(10)   NOT NULL DEFAULT '#4F46E5',
    deleted                     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_org_slug ON organizations(slug);
CREATE INDEX IF NOT EXISTS idx_org_domain   ON organizations(primary_domain);
CREATE INDEX IF NOT EXISTS idx_org_status   ON organizations(status);
