package com.identityplatform.core.tenant;

import lombok.experimental.UtilityClass;

import java.util.UUID;

/**
 * Thread-local tenant context.
 * Ensures every request knows which tenant it belongs to
 * in order to enforce data isolation.
 */
@UtilityClass
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getCurrentTenant() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new TenantNotSetException("No tenant context found in current thread");
        }
        return tenantId;
    }

    public static UUID getCurrentTenantOrNull() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }
}
