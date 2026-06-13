package com.apiclient.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protected endpoints — require a valid Bearer token from the Identity Platform.
 *
 * In Postman: Auth → OAuth 2.0 → Client Credentials → get token → attach as Bearer.
 */
@RestController
@RequestMapping("/api")
public class ProtectedController {

    /**
     * GET /api/me
     * Returns the claims from the caller's JWT token.
     * Useful for verifying which client/user is making the request.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("subject",    jwt.getSubject());
        info.put("issuer",     jwt.getIssuer());
        info.put("scopes",     jwt.getClaimAsStringList("scope"));
        info.put("issued_at",  jwt.getIssuedAt());
        info.put("expires_at", jwt.getExpiresAt());

        // Extra claims present for user tokens (authorization_code flow)
        if (jwt.hasClaim("org_id"))  info.put("org_id",  jwt.getClaim("org_id"));
        if (jwt.hasClaim("email"))   info.put("email",   jwt.getClaim("email"));
        if (jwt.hasClaim("roles"))   info.put("roles",   jwt.getClaim("roles"));

        info.put("all_claims", jwt.getClaims());
        return ResponseEntity.ok(info);
    }
}
