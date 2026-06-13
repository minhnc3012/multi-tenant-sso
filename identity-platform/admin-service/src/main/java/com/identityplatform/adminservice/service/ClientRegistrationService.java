package com.identityplatform.adminservice.service;

import com.identityplatform.adminservice.domain.ClientType;
import com.identityplatform.adminservice.dto.ClientRegistrationDto;
import com.identityplatform.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientRegistrationService {

    private static final BCryptPasswordEncoder SECRET_ENCODER = new BCryptPasswordEncoder(12);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RegisteredClientRepository clientRepository;
    private final RegisteredClientMetadataService metadataService;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public ClientRegistrationDto.RegisterResponse registerClient(
            UUID organizationId,
            ClientRegistrationDto.RegisterRequest request) {

        // Verify org exists
        organizationRepository.findById(organizationId)
                .filter(o -> !o.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        String orgSlug = organizationRepository.findById(organizationId).get().getSlug();

        // Generate clientId
        String clientId = resolveClientId(request.clientIdHint(), orgSlug);
        if (clientRepository.findByClientId(clientId) != null) {
            throw new IllegalArgumentException("clientId '" + clientId + "' is already in use");
        }

        // Generate plain-text secret (returned once) + BCrypt hash (stored)
        String plainSecret = null;
        String hashedSecret = null;
        if (request.clientType() != ClientType.MOBILE_CLIENT) {
            plainSecret  = generateSecret();
            hashedSecret = SECRET_ENCODER.encode(plainSecret);
        }

        // Build scopes
        List<String> scopes = new ArrayList<>(List.of(
                OidcScopes.OPENID, OidcScopes.PROFILE, OidcScopes.EMAIL));
        if (request.additionalScopes() != null) {
            request.additionalScopes().stream()
                    .filter(s -> !scopes.contains(s))
                    .forEach(scopes::add);
        }

        // Token settings
        int ttl = (request.accessTokenTtlSeconds() != null && request.accessTokenTtlSeconds() > 0)
                ? request.accessTokenTtlSeconds() : 3600;

        TokenSettings tokenSettings = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofSeconds(ttl))
                .refreshTokenTimeToLive(Duration.ofDays(30))
                .reuseRefreshTokens(false)
                .build();

        // Build RegisteredClient based on type
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName(request.name())
                .clientIdIssuedAt(Instant.now())
                .tokenSettings(tokenSettings)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(request.clientType() == ClientType.MOBILE_CLIENT)
                        .build());

        switch (request.clientType()) {
            case WEB_CLIENT -> {
                builder.clientSecret(hashedSecret)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
                addRedirectUris(builder, request.redirectUris());
                addPostLogoutUris(builder, request.postLogoutRedirectUris());
                scopes.forEach(builder::scope);
            }
            case API_CLIENT, M2M_CLIENT -> {
                builder.clientSecret(hashedSecret)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
                // API clients don't need openid/profile/email — only custom scopes
                scopes.stream()
                        .filter(s -> !s.equals(OidcScopes.OPENID)
                                && !s.equals(OidcScopes.PROFILE)
                                && !s.equals(OidcScopes.EMAIL))
                        .forEach(builder::scope);
                // Re-add custom scopes if no additionalScopes provided
                if (request.additionalScopes() == null || request.additionalScopes().isEmpty()) {
                    builder.scope("api:read").scope("api:write");
                }
            }
            case MOBILE_CLIENT -> {
                builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
                addRedirectUris(builder, request.redirectUris());
                scopes.forEach(builder::scope);
            }
        }

        RegisteredClient saved = builder.build();
        clientRepository.save(saved);

        // Link to org
        metadataService.ensure(saved.getId(), organizationId, request.clientType(),
                request.description() != null ? request.description() : request.name());

        log.info("[ClientReg] Registered client: clientId={}, org={}, type={}",
                clientId, organizationId, request.clientType());

        return new ClientRegistrationDto.RegisterResponse(
                clientId,
                plainSecret,
                request.clientType(),
                organizationId,
                request.redirectUris(),
                new ArrayList<>(saved.getScopes()),
                saved.getClientIdIssuedAt(),
                request.description()
        );
    }

    public List<ClientRegistrationDto.ClientSummary> listByOrg(UUID organizationId) {
        return metadataService.findByOrganizationId(organizationId).stream()
                .map(meta -> {
                    RegisteredClient c = clientRepository.findById(meta.getRegisteredClientId());
                    if (c == null) return null;
                    return new ClientRegistrationDto.ClientSummary(
                            c.getClientId(),
                            c.getClientName(),
                            meta.getClientType(),
                            organizationId,
                            new ArrayList<>(c.getScopes()),
                            c.getClientIdIssuedAt(),
                            meta.getDescription()
                    );
                })
                .filter(s -> s != null)
                .toList();
    }

    @Transactional
    public void deleteClient(String clientId, UUID organizationId) {
        RegisteredClient client = clientRepository.findByClientId(clientId);
        if (client == null) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }
        metadataService.findByRegisteredClientId(client.getId())
                .filter(m -> organizationId.equals(m.getOrganizationId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Client '" + clientId + "' does not belong to org " + organizationId));

        // Spring AS JdbcRegisteredClientRepository has no delete — use JDBC directly via repo
        // For now mark as deactivated via metadata
        log.warn("[ClientReg] Delete requested for clientId={} in org={} — remove manually from oauth2_registered_client",
                clientId, organizationId);
        throw new UnsupportedOperationException(
                "Direct client deletion not supported by Spring AS JdbcRegisteredClientRepository. " +
                "Use: DELETE FROM oauth2_registered_client WHERE client_id = '" + clientId + "'");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveClientId(String hint, String orgSlug) {
        if (hint != null && !hint.isBlank()) {
            return hint.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        }
        byte[] bytes = new byte[4];
        SECURE_RANDOM.nextBytes(bytes);
        return orgSlug + "-" + HexFormat.of().formatHex(bytes);
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private void addRedirectUris(RegisteredClient.Builder builder, List<String> uris) {
        if (uris != null) uris.forEach(builder::redirectUri);
    }

    private void addPostLogoutUris(RegisteredClient.Builder builder, List<String> uris) {
        if (uris != null) uris.forEach(builder::postLogoutRedirectUri);
    }
}
