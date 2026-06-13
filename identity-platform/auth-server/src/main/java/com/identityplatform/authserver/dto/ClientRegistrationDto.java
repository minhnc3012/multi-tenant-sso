package com.identityplatform.authserver.dto;

import com.identityplatform.authserver.domain.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ClientRegistrationDto {

    public record RegisterRequest(
            /** Human-readable name shown on consent screen. */
            @NotBlank
            String name,

            /** Client type determines allowed grant types and authentication method. */
            @NotNull
            ClientType clientType,

            /**
             * Required for WEB_CLIENT and MOBILE_CLIENT.
             * Must be exact URIs — no wildcards.
             */
            List<String> redirectUris,

            /** Post-logout redirect URIs (optional, WEB_CLIENT only). */
            List<String> postLogoutRedirectUris,

            /**
             * Extra scopes beyond openid/profile/email.
             * For API_CLIENT: e.g. ["api:read","api:write"]
             */
            List<String> additionalScopes,

            /** Access token TTL in seconds. Defaults to 3600. */
            Integer accessTokenTtlSeconds,

            /**
             * Optional custom clientId slug. If blank, auto-generated as
             * "{orgSlug}-{randomHex8}".  Must be globally unique.
             */
            String clientIdHint,

            String description
    ) {}

    public record RegisterResponse(
            String clientId,

            /**
             * Plain-text client secret — shown EXACTLY ONCE here.
             * Store it securely; it cannot be retrieved again.
             * Not present for MOBILE_CLIENT (public client, no secret).
             */
            String clientSecret,

            ClientType clientType,

            UUID organizationId,

            List<String> redirectUris,

            List<String> scopes,

            Instant issuedAt,

            String description
    ) {}

    public record ClientSummary(
            String clientId,
            String clientName,
            ClientType clientType,
            UUID organizationId,
            List<String> scopes,
            Instant issuedAt,
            String description
    ) {}
}
