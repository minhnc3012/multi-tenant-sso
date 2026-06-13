-- V8: Extension table linking OAuth2 clients to organizations + client type
-- Does NOT modify oauth2_registered_client — Spring Authorization Server owns that schema.
-- ON DELETE CASCADE: removing a registered client auto-removes its metadata.

CREATE TABLE registered_client_metadata (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    registered_client_id VARCHAR(255) NOT NULL UNIQUE
                         REFERENCES oauth2_registered_client(id) ON DELETE CASCADE,
    organization_id      UUID         REFERENCES organizations(id) ON DELETE CASCADE,
    client_type          VARCHAR(30)  NOT NULL DEFAULT 'WEB_CLIENT',
    description          VARCHAR(500),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- organization_id nullable: NULL = platform-level client not owned by a specific org
CREATE INDEX idx_rcm_org    ON registered_client_metadata(organization_id);
CREATE INDEX idx_rcm_client ON registered_client_metadata(registered_client_id);
