package com.identityplatform.organization.service;

import com.identityplatform.core.exception.ResourceNotFoundException;
import com.identityplatform.organization.domain.IdentityProviderConfig;
import com.identityplatform.organization.domain.Organization;
import com.identityplatform.organization.repository.IdpConfigRepository;
import com.identityplatform.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Manages IdP federation configuration for each tenant.
 *
 * Allows tenants to attach their own Azure AD / Okta / SAML provider.
 * When a user of that tenant logs in, the system redirects to the tenant's IdP
 * instead of using local username/password.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdpFederationService {

    private final IdpConfigRepository idpConfigRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public IdentityProviderConfig configureIdpFederation(
            UUID organizationId,
            IdentityProviderConfig.IdpType type,
            String issuerUrl,
            String clientId,
            String clientSecret) {

        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));

        // Delete existing config if present
        idpConfigRepository.findByOrganizationId(organizationId)
                .ifPresent(idpConfigRepository::delete);

        IdentityProviderConfig config = IdentityProviderConfig.builder()
                .organization(org)
                .type(type)
                .issuerUrl(issuerUrl)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .enabled(true)
                .build();

        config = idpConfigRepository.save(config);
        log.info("IdP federation configured for org {}: type={}, issuer={}",
                organizationId, type, issuerUrl);
        return config;
    }

    @Transactional
    public IdentityProviderConfig configureSamlFederation(
            UUID organizationId,
            String entityId,
            String samlMetadata) {

        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));

        idpConfigRepository.findByOrganizationId(organizationId)
                .ifPresent(idpConfigRepository::delete);

        IdentityProviderConfig config = IdentityProviderConfig.builder()
                .organization(org)
                .type(IdentityProviderConfig.IdpType.SAML)
                .issuerUrl(entityId)
                .samlMetadata(samlMetadata)
                .enabled(true)
                .build();

        return idpConfigRepository.save(config);
    }

    @Transactional(readOnly = true)
    public Optional<IdentityProviderConfig> findActiveConfig(UUID organizationId) {
        return idpConfigRepository.findByOrganizationIdAndEnabledTrue(organizationId);
    }

    /**
     * Checks whether the tenant has an external IdP configured.
     * If yes → redirect to that IdP on login.
     * If no → use the local login form.
     */
    @Transactional(readOnly = true)
    public boolean hasFederatedIdp(UUID organizationId) {
        return idpConfigRepository.existsByOrganizationIdAndEnabledTrue(organizationId);
    }

    @Transactional
    public void disableFederation(UUID organizationId) {
        idpConfigRepository.findByOrganizationId(organizationId)
                .ifPresent(config -> {
                    config.setEnabled(false);
                    idpConfigRepository.save(config);
                    log.info("IdP federation disabled for org: {}", organizationId);
                });
    }
}
