package com.identityplatform.sdk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the IDP Client SDK.
 *
 * <pre>
 * idp:
 *   client:
 *     base-url: http://localhost:8080
 *     client-id: parking-admin-api-client
 *     client-secret: parking-admin-api-secret
 *     org-slug: kerb          # slug of the org this app belongs to
 * </pre>
 *
 * The SDK resolves the org UUID from the slug automatically on first use.
 */
@ConfigurationProperties(prefix = "idp.client")
public record IdpProperties(
        /** Base URL of the Identity Platform (e.g. http://localhost:8080 or https://auth.company.com). */
        String baseUrl,

        /** OAuth2 client ID registered in IDP with client_credentials grant and users:write scope. */
        String clientId,

        /** OAuth2 client secret. Supply via env var in production. */
        String clientSecret,

        /** Slug of the organization this app belongs to (e.g. "kerb"). UUID is resolved automatically. */
        String orgSlug
) {}
