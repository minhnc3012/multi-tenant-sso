package com.identityplatform.sdk.service;

import com.identityplatform.sdk.IdpProperties;
import com.identityplatform.sdk.token.IdpTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the org UUID from the configured slug by calling the IDP once,
 * then caches the result for the lifetime of the application.
 */
@Slf4j
@RequiredArgsConstructor
public class IdpOrgResolver {

    private final IdpProperties props;
    private final IdpTokenProvider tokenProvider;
    private final WebClient webClient;

    private final AtomicReference<UUID> cached = new AtomicReference<>();

    public UUID getOrgId() {
        UUID id = cached.get();
        if (id != null) return id;
        synchronized (this) {
            id = cached.get();
            if (id != null) return id;
            id = resolve();
            cached.set(id);
        }
        return id;
    }

    @SuppressWarnings("unchecked")
    private UUID resolve() {
        String url = props.baseUrl() + "/api/v1/organizations/slug/" + props.orgSlug();
        log.info("[IdpSDK] Resolving org UUID for slug '{}'", props.orgSlug());
        try {
            Map<String, Object> resp = webClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (resp == null || resp.get("id") == null) {
                throw new IllegalStateException("IDP returned no id for org slug: " + props.orgSlug());
            }
            UUID orgId = UUID.fromString(resp.get("id").toString());
            log.info("[IdpSDK] Resolved org slug '{}' → {}", props.orgSlug(), orgId);
            return orgId;
        } catch (WebClientResponseException e) {
            log.error("[IdpSDK] Failed to resolve org slug '{}': status={}, body={}",
                    props.orgSlug(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException(
                    "Cannot resolve org slug '" + props.orgSlug() + "' from IDP: " + e.getMessage(), e);
        }
    }
}
