-- V11: PostgreSQL Row Level Security — tenant isolation at DB layer
--
-- Defense-in-depth: even if application code has a bug, the DB enforces tenant boundaries.
-- Policies read app.tenant_id (set by TenantResolutionFilter) and app.is_platform_admin.
--
-- FORCE ROW LEVEL SECURITY: table owner (app DB user) is also subject to RLS.
-- coalesce(..., '') = '' condition: backward compat — allows queries when tenant not yet resolved
-- (e.g. system startup, auth-server internal ops). Tighten this later by removing the fallback.

-- ── Users ────────────────────────────────────────────────────────────────────
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON users
    AS PERMISSIVE FOR ALL TO PUBLIC
    USING (
        organization_id::text = current_setting('app.tenant_id', true)
        OR current_setting('app.is_platform_admin', true) = 'true'
        OR coalesce(current_setting('app.tenant_id', true), '') = ''
    );

-- ── Roles ─────────────────────────────────────────────────────────────────────
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON roles
    AS PERMISSIVE FOR ALL TO PUBLIC
    USING (
        organization_id::text = current_setting('app.tenant_id', true)
        OR organization_id IS NULL                                            -- system roles visible to all tenants
        OR current_setting('app.is_platform_admin', true) = 'true'
        OR coalesce(current_setting('app.tenant_id', true), '') = ''
    );

-- ── Audit Logs ────────────────────────────────────────────────────────────────
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON audit_logs
    AS PERMISSIVE FOR ALL TO PUBLIC
    USING (
        organization_id::text = current_setting('app.tenant_id', true)
        OR current_setting('app.is_platform_admin', true) = 'true'
        OR coalesce(current_setting('app.tenant_id', true), '') = ''
    );
