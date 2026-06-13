package com.identityplatform.authserver.filter;

import com.identityplatform.core.tenant.TenantContext;
import com.identityplatform.organization.domain.Organization;
import com.identityplatform.organization.service.OrganizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that runs first on every request.
 * Detects the tenant and stores it in TenantContext (ThreadLocal)
 * so that downstream services can use it when querying the DB.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final OrganizationService organizationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String slug = resolveTenantSlug(request);
            if (slug != null) {
                try {
                    Organization org = organizationService.findBySlug(slug);
                    if (org.isActive()) {
                        TenantContext.setCurrentTenant(org.getId());
                    }
                } catch (Exception e) {
                    log.debug("Tenant not found for slug: {}", slug);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            // Important: always clear after the request completes
            // to prevent leaking into other requests in the thread pool
            TenantContext.clear();
        }
    }

    private String resolveTenantSlug(HttpServletRequest request) {
        // Priority 1: Header
        String headerSlug = request.getHeader("X-Organization-Slug");
        if (headerSlug != null && !headerSlug.isBlank()) {
            return headerSlug;
        }

        // Priority 2: Subdomain
        String host = request.getServerName();
        if (host != null && host.contains(".")) {
            String subdomain = host.split("\\.")[0];
            if (!subdomain.equals("www") && !subdomain.equals("auth") && !subdomain.equals("api")) {
                return subdomain;
            }
        }

        // Priority 3: Path variable /api/v1/t/{slug}/...
        String path = request.getRequestURI();
        if (path.contains("/t/")) {
            String[] parts = path.split("/t/");
            if (parts.length > 1) {
                return parts[1].split("/")[0];
            }
        }

        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Tenant resolution not needed for public endpoints
        return path.startsWith("/actuator") ||
               path.startsWith("/.well-known") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/swagger-ui");
    }
}
