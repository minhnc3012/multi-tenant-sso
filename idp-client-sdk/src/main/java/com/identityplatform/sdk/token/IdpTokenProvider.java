package com.identityplatform.sdk.token;

import com.identityplatform.sdk.IdpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

/**
 * Fetches and caches an M2M access token from the IDP using client_credentials grant.
 * Thread-safe via volatile + double-checked refresh.
 */
@Slf4j
@RequiredArgsConstructor
public class IdpTokenProvider {

    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    private final IdpProperties props;
    private final WebClient webClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    /** Returns a valid access token, refreshing if expired or within the buffer window. */
    public String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(TOKEN_EXPIRY_BUFFER_SECONDS))) {
            return cachedToken;
        }
        return refreshToken();
    }

    @SuppressWarnings("unchecked")
    private synchronized String refreshToken() {
        // Double-checked: another thread may have refreshed already
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(TOKEN_EXPIRY_BUFFER_SECONDS))) {
            return cachedToken;
        }

        log.debug("[IdpSDK] Fetching new M2M token from {}", props.baseUrl());

        Map<String, Object> response = webClient.post()
                .uri(props.baseUrl() + "/oauth2/token")
                .headers(h -> h.setBasicAuth(props.clientId(), props.clientSecret()))
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                        .with("scope", "users:read users:write"))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("[IdpSDK] Token endpoint returned no access_token");
        }

        cachedToken   = (String) response.get("access_token");
        int expiresIn = response.containsKey("expires_in")
                ? ((Number) response.get("expires_in")).intValue()
                : 300;
        tokenExpiry = Instant.now().plusSeconds(expiresIn);

        log.debug("[IdpSDK] Token acquired, expires in {}s", expiresIn);
        return cachedToken;
    }
}
