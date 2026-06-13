package com.identityplatform.authserver.security;

import com.identityplatform.usermanagement.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * Injects multi-tenant information into the JWT token.
 *
 * The token will include the following additional claims:
 * - org_id: UUID of the organization/tenant
 * - org_slug: slug of the org (used to detect tenant)
 * - roles: list of the user's roles within this org
 * - permissions: flattened permissions from roles
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiTenantTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final UserService userService;

    @Override
    public void customize(JwtEncodingContext context) {
        Authentication principal = context.getPrincipal();

        if (!(principal.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            return;
        }

        try {
            // UserClaims is a plain record — all lazy collections resolved inside the transaction
            UserService.UserClaims claims = userService.findClaimsById(userPrincipal.getUserId());

            context.getClaims()
                    .claim("org_id",      claims.organizationId().toString())
                    .claim("email",       claims.email())
                    .claim("name",        claims.fullName())
                    .claim("roles",       claims.roles())
                    .claim("permissions", claims.permissions());

        } catch (Exception e) {
            log.error("Failed to customize token for principal: {}", principal.getName(), e);
        }
    }
}
