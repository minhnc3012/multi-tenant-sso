package com.identityplatform.core.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Wraps the primary DataSource and sets PostgreSQL session variables on every connection checkout.
 * This activates the RLS policies defined in V8__add_row_level_security.sql.
 *
 * SET (session-level, not LOCAL) is used so the variable survives outside explicit transactions.
 * Since this runs on every getConnection() call, connections returned to HikariCP's pool
 * are always re-initialized with the correct tenant context on the next checkout.
 *
 * UUID.toString() is injection-safe (hex + hyphens only). isPlatformAdmin reads the
 * Spring SecurityContext which is populated by the time JPA code runs.
 */
@Slf4j
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = super.getConnection();
        applyTenantSettings(conn);
        return conn;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conn = super.getConnection(username, password);
        applyTenantSettings(conn);
        return conn;
    }

    private void applyTenantSettings(Connection conn) throws SQLException {
        UUID tenantId = TenantContext.getCurrentTenantOrNull();
        boolean isPlatformAdmin = detectPlatformAdmin();

        try (Statement stmt = conn.createStatement()) {
            if (tenantId != null) {
                stmt.execute("SET app.tenant_id = '" + tenantId + "'");
                stmt.execute("SET app.is_platform_admin = 'false'");
            } else if (isPlatformAdmin) {
                stmt.execute("SET app.tenant_id = ''");
                stmt.execute("SET app.is_platform_admin = 'true'");
            } else {
                // System context (startup, Flyway, unauthenticated): RLS permits via coalesce fallback
                stmt.execute("SET app.tenant_id = ''");
                stmt.execute("SET app.is_platform_admin = 'false'");
            }
        } catch (SQLException ex) {
            log.error("Failed to set tenant session variables on PostgreSQL connection", ex);
            throw ex;
        }
    }

    private boolean detectPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
    }
}
