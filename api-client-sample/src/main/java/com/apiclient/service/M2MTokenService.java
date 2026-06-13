package com.apiclient.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Fetches and caches a client_credentials access token.
 * Automatically re-fetches when the token is about to expire.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class M2MTokenService {

    private static final String REGISTRATION_ID = "identity-platform-m2m";
    private static final int    EXPIRY_BUFFER_SECONDS = 30;

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final DefaultClientCredentialsTokenResponseClient tokenClient =
            new DefaultClientCredentialsTokenResponseClient();

    private OAuth2AccessToken cachedToken;
    private Instant           expiresAt;

    /** Returns a valid access token, fetching a new one if expired. */
    public synchronized String getAccessToken() {
        if (cachedToken == null || isExpired()) {
            log.info("[M2M] Fetching new access token from Identity Platform...");
            ClientRegistration reg = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
            OAuth2AccessTokenResponse response = tokenClient.getTokenResponse(
                    new OAuth2ClientCredentialsGrantRequest(reg));
            cachedToken = response.getAccessToken();
            expiresAt   = cachedToken.getExpiresAt();
            log.info("[M2M] Token acquired. Scopes={}, ExpiresAt={}", cachedToken.getScopes(), expiresAt);
        }
        return cachedToken.getTokenValue();
    }

    private boolean isExpired() {
        if (expiresAt == null) return true;
        return Instant.now().isAfter(expiresAt.minusSeconds(EXPIRY_BUFFER_SECONDS));
    }
}
