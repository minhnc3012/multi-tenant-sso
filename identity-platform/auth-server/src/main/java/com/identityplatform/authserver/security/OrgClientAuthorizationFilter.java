package com.identityplatform.authserver.security;

import com.identityplatform.authserver.service.RegisteredClientMetadataService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Guards the OAuth2 authorization endpoint by checking that the authenticated user's
 * organization is permitted to use the requested client.
 *
 * Runs inside the authorization server filter chain, before OAuth2AuthorizationEndpointFilter.
 * On denial, redirects to /error/org-access-denied instead of returning a raw 400 error.
 */
@Slf4j
@RequiredArgsConstructor
public class OrgClientAuthorizationFilter extends OncePerRequestFilter {

    private final RegisteredClientRepository clientRepository;
    private final RegisteredClientMetadataService metadataService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/oauth2/authorize".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Not authenticated yet — let the normal login-redirect flow handle it
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            chain.doFilter(request, response);
            return;
        }

        String clientId = request.getParameter("client_id");
        if (clientId != null) {
            var client = clientRepository.findByClientId(clientId);
            if (client != null) {
                var metadata = metadataService.findByRegisteredClientId(client.getId());

                if (metadata.isEmpty() || metadata.get().getOrganizationId() == null) {
                    log.error("OAuth2 access denied: client='{}' has no org assignment in registered_client_metadata", clientId);
                    response.sendRedirect("/error/org-access-denied");
                    return;
                }

                UUID clientOrgId = metadata.get().getOrganizationId();
                UUID userOrgId = userPrincipal.getOrganizationId();

                if (!clientOrgId.equals(userOrgId)) {
                    log.warn("OAuth2 access denied: user org={} is not authorized for client='{}' (restricted to org={})",
                            userOrgId, clientId, clientOrgId);
                    response.sendRedirect("/error/org-access-denied");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }
}
