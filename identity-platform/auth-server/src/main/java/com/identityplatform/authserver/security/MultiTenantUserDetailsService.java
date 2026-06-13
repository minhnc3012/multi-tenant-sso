package com.identityplatform.authserver.security;

import com.identityplatform.organization.domain.Organization;
import com.identityplatform.organization.service.OrganizationService;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiTenantUserDetailsService implements UserDetailsService {

    private final UserService userService;
    private final OrganizationService organizationService;

    /**
     * Loads user by email within the tenant context.
     *
     * Tenant is detected from:
     * 1. Request header X-Organization-Slug
     * 2. Subdomain: {slug}.yourplatform.com
     * 3. Email domain: user@company.com → find org with primaryDomain = company.com
     *
     * @Transactional keeps the JPA session open until UserPrincipal is fully built,
     * preventing LazyInitializationException when accessing User.roles.
     */
    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            String orgSlug = detectTenantFromRequest();
            Organization org;

            if (orgSlug != null) {
                org = organizationService.findBySlug(orgSlug);
            } else {
                // Fallback: detect from email domain
                org = organizationService.findByEmailDomain(email);
            }

            if (!org.isActive()) {
                throw new UsernameNotFoundException(
                        "Organization is not active: " + org.getSlug());
            }

            User user = userService.findByEmailAndOrg(email, org.getId());
            return new UserPrincipal(user);

        } catch (Exception e) {
            log.warn("Failed to load user: email={}, reason={}", email, e.getMessage());
            throw new UsernameNotFoundException("User not found: " + email);
        }
    }

    private String detectTenantFromRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attrs == null) return null;
            var request = attrs.getRequest();

            // Priority 1: explicit form field submitted with the login form
            String formSlug = request.getParameter("org-slug");
            if (formSlug != null && !formSlug.isBlank()) {
                return formSlug.trim().toLowerCase();
            }

            // Priority 2: API clients that set the header explicitly
            String headerSlug = request.getHeader("X-Organization-Slug");
            if (headerSlug != null && !headerSlug.isBlank()) {
                return headerSlug.trim().toLowerCase();
            }

            // Priority 3: multi-tenant subdomain (e.g. acme.yourplatform.com → "acme")
            String host = request.getServerName();
            if (host != null && host.contains(".")) {
                String subdomain = host.split("\\.")[0];
                if (!subdomain.equals("www") && !subdomain.equals("auth")
                        && !subdomain.equals("localhost")) {
                    return subdomain.toLowerCase();
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
