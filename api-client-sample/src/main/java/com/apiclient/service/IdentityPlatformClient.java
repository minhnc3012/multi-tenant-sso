package com.apiclient.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * HTTP client for calling Identity Platform REST APIs.
 * Attaches the M2M Bearer token on every request automatically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityPlatformClient {

    private final M2MTokenService tokenService;
    private final RestTemplate    restTemplate = new RestTemplate();

    @Value("${identity-platform.base-url}")
    private String baseUrl;

    /** GET /api/v1/organizations/{orgId}/clients — list registered clients of an org */
    public List<Map<String, Object>> listOrgClients(String orgId) {
        return get("/api/v1/organizations/" + orgId + "/clients",
                new ParameterizedTypeReference<>() {});
    }

    /** GET /api/v1/audit-logs — recent audit events (org scoped by token) */
    public List<Map<String, Object>> listAuditLogs() {
        return get("/api/v1/audit-logs",
                new ParameterizedTypeReference<>() {});
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private <T> T get(String path, ParameterizedTypeReference<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.getAccessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<T> response = restTemplate.exchange(
                baseUrl + path, HttpMethod.GET, new HttpEntity<>(headers), type);

        log.debug("[M2M] GET {} → {}", path, response.getStatusCode());
        return response.getBody();
    }
}
