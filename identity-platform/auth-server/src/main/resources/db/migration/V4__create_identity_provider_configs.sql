-- V4: Create identity_provider_configs table

CREATE TABLE IF NOT EXISTS identity_provider_configs (
    id                          UUID PRIMARY KEY,
    organization_id             UUID          NOT NULL REFERENCES organizations(id) UNIQUE,
    type                        VARCHAR(20)   NOT NULL,
    issuer_url                  VARCHAR(500)  NOT NULL,
    client_id                   VARCHAR(255),
    client_secret               VARCHAR(500),
    saml_metadata               TEXT,
    email_attribute             VARCHAR(100)  NOT NULL DEFAULT 'email',
    first_name_attribute        VARCHAR(100)  NOT NULL DEFAULT 'given_name',
    last_name_attribute         VARCHAR(100)  NOT NULL DEFAULT 'family_name',
    enabled                     BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted                     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
