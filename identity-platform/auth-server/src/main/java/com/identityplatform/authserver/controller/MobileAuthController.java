package com.identityplatform.authserver.controller;

import com.identityplatform.authserver.security.UserPrincipal;
import com.identityplatform.usermanagement.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Form-login token endpoint for first-party mobile apps.
 *
 * POST /api/auth/token  (application/x-www-form-urlencoded)
 *   username=<email>&password=<password>
 *
 * Returns a signed JWT (same RSA key as the OAuth2 server) in OAuth2 token response format.
 * The access token carries the same custom claims as the standard authorization_code flow
 * (org_id, roles, permissions) — the DashboardScreen displays them identically.
 *
 * Trade-off vs PKCE (mobile-client-sample):
 *   PRO  — no browser popup, fully native UX
 *   CON  — app handles raw credentials (first-party only)
 *   CON  — tokens are not tracked in oauth2_authorization table (no revocation / introspection)
 *   CON  — OAuth 2.1 deprecates password grant; prefer PKCE for new greenfield projects
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class MobileAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final UserService userService;
    private final AuthorizationServerSettings authorizationServerSettings;

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> passwordLogin(
            @RequestParam String username,
            @RequestParam String password) {

        Authentication userAuth;
        try {
            userAuth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).body(Map.of(
                    "error",             "invalid_grant",
                    "error_description", "Invalid username or password"));
        }

        UserPrincipal principal = (UserPrincipal) userAuth.getPrincipal();
        UserService.UserClaims claims = userService.findClaimsById(principal.getUserId());

        Instant now = Instant.now();
        long expiresIn = 3600L;

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(authorizationServerSettings.getIssuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiresIn))
                .subject(principal.getUsername())
                .claim("email",       claims.email())
                .claim("name",        claims.fullName())
                .claim("org_id",      claims.organizationId().toString())
                .claim("roles",       claims.roles())
                .claim("permissions", claims.permissions())
                .claim("scope",       "openid profile email")
                .claim("azp",         "realestate-mobile-client")
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claimsSet));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", jwt.getTokenValue());
        body.put("token_type",   "Bearer");
        body.put("expires_in",   expiresIn);
        body.put("scope",        "openid profile email");
        return ResponseEntity.ok(body);
    }
}
