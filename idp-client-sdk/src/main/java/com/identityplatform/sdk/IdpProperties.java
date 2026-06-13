package com.identityplatform.sdk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the IDP Client SDK.
 *
 * <pre>
 * idp:
 *   client:
 *     base-url: http://localhost:8080
 *     management-base-url: http://localhost:8081
 *     client-id: parking-admin-api-client
 *     client-secret: parking-admin-api-secret
 *     org-slug: kerb          # slug of the org this app belongs to
 * </pre>
 *
 * The SDK resolves the org UUID from the slug automatically on first use.
 */
@ConfigurationProperties(prefix = "idp.client")
public record IdpProperties(
        /** Base URL of the Authentication API (OIDC/OAuth2 token endpoints), e.g. http://localhost:8080. */
        String baseUrl,

        /** OAuth2 client ID registered in IDP with client_credentials grant and users:write scope. */
        String clientId,

        /** OAuth2 client secret. Supply via env var in production. */
        String clientSecret,

        /** Slug of the organization this app belongs to (e.g. "kerb"). UUID is resolved automatically. */
        String orgSlug,

        /**
         * Base URL of the Management API (users/organizations/roles), e.g. http://localhost:8081.
         * If null/blank, falls back to {@link #baseUrl()} for backward compatibility.
         */
        String managementBaseUrl
) {
    /** Returns managementBaseUrl if configured, otherwise falls back to baseUrl. */
    public String resolvedManagementBaseUrl() {
        return (managementBaseUrl != null && !managementBaseUrl.isBlank()) ? managementBaseUrl : baseUrl;
    }
}
