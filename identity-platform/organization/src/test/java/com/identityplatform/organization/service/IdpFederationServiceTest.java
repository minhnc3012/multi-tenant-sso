package com.identityplatform.organization.service;

import com.identityplatform.core.exception.ResourceNotFoundException;
import com.identityplatform.organization.domain.IdentityProviderConfig;
import com.identityplatform.organization.domain.Organization;
import com.identityplatform.organization.repository.IdpConfigRepository;
import com.identityplatform.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdpFederationService tests")
class IdpFederationServiceTest {

    @Mock
    private IdpConfigRepository idpConfigRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private IdpFederationService idpFederationService;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
    }

    // ── configureIdpFederation (OIDC) ──────────────────────────

    @Test
    @DisplayName("configureIdpFederation: creates a new config for the org")
    void configureIdpFederation_newConfig() {
        Organization org = Organization.builder().slug("acme").name("Acme").build();

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(idpConfigRepository.findByOrganizationId(orgId)).thenReturn(Optional.empty());
        when(idpConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IdentityProviderConfig result = idpFederationService.configureIdpFederation(
                orgId,
                IdentityProviderConfig.IdpType.OIDC,
                "https://login.microsoftonline.com/tenant-id/v2.0",
                "azure-client-id",
                "azure-client-secret"
        );

        assertThat(result.getType()).isEqualTo(IdentityProviderConfig.IdpType.OIDC);
        assertThat(result.getIssuerUrl()).isEqualTo("https://login.microsoftonline.com/tenant-id/v2.0");
        assertThat(result.getClientId()).isEqualTo("azure-client-id");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("configureIdpFederation: organization not found → throws exception")
    void configureIdpFederation_orgNotFound_throwsException() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> idpFederationService.configureIdpFederation(
                        orgId, IdentityProviderConfig.IdpType.OIDC, "issuer", "cid", "csecret"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("configureIdpFederation: deletes existing config before creating a new one")
    void configureIdpFederation_deletesOldConfig() {
        Organization org = Organization.builder().slug("acme").name("Acme").build();
        IdentityProviderConfig oldConfig = IdentityProviderConfig.builder()
                .organization(org)
                .type(IdentityProviderConfig.IdpType.SAML)
                .build();

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(idpConfigRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(oldConfig));
        when(idpConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        idpFederationService.configureIdpFederation(
                orgId, IdentityProviderConfig.IdpType.OIDC, "issuer", "cid", "csecret"
        );

        verify(idpConfigRepository).delete(oldConfig);
    }

    @Test
    @DisplayName("configureIdpFederation: does not delete when no existing config")
    void configureIdpFederation_noOldConfig_noDelete() {
        Organization org = Organization.builder().slug("acme").name("Acme").build();

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(idpConfigRepository.findByOrganizationId(orgId)).thenReturn(Optional.empty());
        when(idpConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        idpFederationService.configureIdpFederation(
                orgId, IdentityProviderConfig.IdpType.OIDC, "issuer", "cid", "csecret"
        );

        verify(idpConfigRepository, never()).delete(any());
    }

    // ── configureSamlFederation ─────────────────────────────────

    @Test
    @DisplayName("configureSamlFederation: creates a SAML config")
    void configureSamlFederation_success() {
        Organization org = Organization.builder().slug("acme").name("Acme").build();

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(idpConfigRepository.findByOrganizationId(orgId)).thenReturn(Optional.empty());
        when(idpConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IdentityProviderConfig result = idpFederationService.configureSamlFederation(
                orgId,
                "https://saml.acme.com/entity-id",
                "<?xml version='1.0'?><EntityDescriptor>test</EntityDescriptor>"
        );

        assertThat(result.getType()).isEqualTo(IdentityProviderConfig.IdpType.SAML);
        assertThat(result.getIssuerUrl()).isEqualTo("https://saml.acme.com/entity-id");
        assertThat(result.getSamlMetadata()).contains("EntityDescriptor");
    }

    // ── findActiveConfig ────────────────────────────────────────

    @Test
    @DisplayName("findActiveConfig: returns config when present")
    void findActiveConfig_exists() {
        IdentityProviderConfig config = IdentityProviderConfig.builder()
                .enabled(true)
                .type(IdentityProviderConfig.IdpType.OIDC)
                .build();

        when(idpConfigRepository.findByOrganizationIdAndEnabledTrue(orgId))
                .thenReturn(Optional.of(config));

        Optional<IdentityProviderConfig> result = idpFederationService.findActiveConfig(orgId);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(IdentityProviderConfig.IdpType.OIDC);
    }

    @Test
    @DisplayName("findActiveConfig: returns Optional.empty when not present")
    void findActiveConfig_notExists() {
        when(idpConfigRepository.findByOrganizationIdAndEnabledTrue(orgId))
                .thenReturn(Optional.empty());

        Optional<IdentityProviderConfig> result = idpFederationService.findActiveConfig(orgId);

        assertThat(result).isEmpty();
    }

    // ── hasFederatedIdp ─────────────────────────────────────────

    @Test
    @DisplayName("hasFederatedIdp: true when an active config exists")
    void hasFederatedIdp_true() {
        when(idpConfigRepository.existsByOrganizationIdAndEnabledTrue(orgId))
                .thenReturn(true);

        assertThat(idpFederationService.hasFederatedIdp(orgId)).isTrue();
    }

    @Test
    @DisplayName("hasFederatedIdp: false when no active config exists")
    void hasFederatedIdp_false() {
        when(idpConfigRepository.existsByOrganizationIdAndEnabledTrue(orgId))
                .thenReturn(false);

        assertThat(idpFederationService.hasFederatedIdp(orgId)).isFalse();
    }

    // ── disableFederation ───────────────────────────────────────

    @Test
    @DisplayName("disableFederation: disables the current config")
    void disableFederation_success() {
        IdentityProviderConfig config = IdentityProviderConfig.builder()
                .enabled(true)
                .build();

        when(idpConfigRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(config));

        idpFederationService.disableFederation(orgId);

        assertThat(config.isEnabled()).isFalse();
        verify(idpConfigRepository).save(config);
    }

    @Test
    @DisplayName("disableFederation: does nothing when no config exists")
    void disableFederation_noConfig_doesNothing() {
        when(idpConfigRepository.findByOrganizationId(orgId)).thenReturn(Optional.empty());

        idpFederationService.disableFederation(orgId);

        verify(idpConfigRepository, never()).save(any());
    }

    // ── IdpType enum ─────────────────────────────────────────────

    @Test
    @DisplayName("IdpType: contains all values OIDC, SAML, LDAP")
    void idpType_values() {
        IdentityProviderConfig.IdpType[] values = IdentityProviderConfig.IdpType.values();
        assertThat(values).hasSize(3);
        assertThat(values).contains(
                IdentityProviderConfig.IdpType.OIDC,
                IdentityProviderConfig.IdpType.SAML,
                IdentityProviderConfig.IdpType.LDAP
        );
    }
}
