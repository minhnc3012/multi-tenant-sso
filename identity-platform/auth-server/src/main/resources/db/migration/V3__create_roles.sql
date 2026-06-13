-- V3: Create roles and role_permissions tables + seed system roles

CREATE TABLE IF NOT EXISTS roles (
    id                          UUID PRIMARY KEY,
    organization_id             UUID,
    name                        VARCHAR(100)  NOT NULL,
    description                 VARCHAR(500),
    system_role                 BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted                     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id             UUID          NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission          VARCHAR(100)  NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id             UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id             UUID          NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_role_org_name ON roles(organization_id, name);

-- Seed system roles
INSERT INTO roles (id, name, description, system_role, deleted, created_at, updated_at)
VALUES (gen_random_uuid(), 'PLATFORM_ADMIN', 'Platform-wide administrator', TRUE, FALSE, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name, description, system_role, deleted, created_at, updated_at)
VALUES (gen_random_uuid(), 'ORG_ADMIN', 'Organization administrator', TRUE, FALSE, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name, description, system_role, deleted, created_at, updated_at)
VALUES (gen_random_uuid(), 'ORG_MEMBER', 'Regular organization member', TRUE, FALSE, NOW(), NOW())
ON CONFLICT DO NOTHING;
