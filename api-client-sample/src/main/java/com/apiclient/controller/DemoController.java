package com.apiclient.controller;

import com.apiclient.service.IdentityPlatformClient;
import com.apiclient.service.M2MTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo endpoints showing the M2M token and how it can be used to call
 * Identity Platform APIs from a backend service.
 *
 * All endpoints on port 8083 — no user login required.
 */
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final M2MTokenService         tokenService;
    private final IdentityPlatformClient  idpClient;

    /**
     * GET /demo/token
     * Returns the current M2M access token + its decoded claims.
     * Shows that the token is issued to the API client (no sub = service account).
     */
    @GetMapping("/token")
    public ResponseEntity<Map<String, Object>> showToken() {
        String token = tokenService.getAccessToken();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token_preview", token.substring(0, Math.min(token.length(), 50)) + "...");
        result.put("claims", decodeJwtPayload(token));
        result.put("note", "client_credentials — no 'sub' user claim, token belongs to the service");
        return ResponseEntity.ok(result);
    }

    /**
     * GET /demo/audit-logs
     * Calls Identity Platform's audit-log API using the M2M token.
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<Map<String, Object>>> auditLogs() {
        return ResponseEntity.ok(idpClient.listAuditLogs());
    }

    /**
     * GET /demo/org/{orgId}/clients
     * Lists the registered OAuth2 clients belonging to an organization.
     */
    @GetMapping("/org/{orgId}/clients")
    public ResponseEntity<List<Map<String, Object>>> orgClients(@PathVariable String orgId) {
        return ResponseEntity.ok(idpClient.listOrgClients(orgId));
    }

    // ── helper: decode JWT payload without verification (demo only) ──────────

    private Map<String, Object> decodeJwtPayload(String jwt) {
        try {
            String[] parts   = jwt.split("\\.");
            byte[]   decoded = Base64.getUrlDecoder().decode(parts[1]);
            String   json    = new String(decoded);
            // parse manually to avoid extra dependency
            Map<String, Object> claims = new LinkedHashMap<>();
            json = json.replaceAll("[{}\"]", "");
            for (String entry : json.split(",")) {
                String[] kv = entry.split(":", 2);
                if (kv.length == 2) claims.put(kv[0].trim(), kv[1].trim());
            }
            return claims;
        } catch (Exception e) {
            return Map.of("error", "Could not decode: " + e.getMessage());
        }
    }
}
