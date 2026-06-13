package com.identityplatform.adminservice.filter;

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
 * Resolves the current tenant from the X-Organization-Slug header (or subdomain)
 * and stores it in TenantContext so downstream JPA queries are scoped correctly.
 * Mirror of auth-server's TenantResolutionFilter — both services share the same logic.
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
            TenantContext.clear();
        }
    }

    private String resolveTenantSlug(HttpServletRequest request) {
        String headerSlug = request.getHeader("X-Organization-Slug");
        if (headerSlug != null && !headerSlug.isBlank()) {
            return headerSlug;
        }
        String host = request.getServerName();
        if (host != null && host.contains(".")) {
            String subdomain = host.split("\\.")[0];
            if (!subdomain.equals("www") && !subdomain.equals("auth") && !subdomain.equals("api")) {
                return subdomain;
            }
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") ||
               path.startsWith("/.well-known") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/swagger-ui");
    }
}
