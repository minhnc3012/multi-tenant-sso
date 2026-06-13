package com.identityplatform.organization.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Organization domain tests")
class OrganizationTest {

    @Test
    @DisplayName("isActive: true when status = ACTIVE")
    void isActive_active() {
        Organization org = Organization.builder()
                .status(OrganizationStatus.ACTIVE)
                .slug("acme")
                .name("Acme Corp")
                .build();
        assertThat(org.isActive()).isTrue();
    }

    @Test
    @DisplayName("isActive: false when status = SUSPENDED")
    void isActive_suspended() {
        Organization org = Organization.builder()
                .status(OrganizationStatus.SUSPENDED)
                .slug("acme")
                .name("Acme Corp")
                .build();
        assertThat(org.isActive()).isFalse();
    }

    @Test
    @DisplayName("isActive: false when status = PENDING_SETUP")
    void isActive_pendingSetup() {
        Organization org = Organization.builder()
                .status(OrganizationStatus.PENDING_SETUP)
                .slug("acme")
                .name("Acme Corp")
                .build();
        assertThat(org.isActive()).isFalse();
    }

    @Test
    @DisplayName("isActive: false when status = DEACTIVATED")
    void isActive_deactivated() {
        Organization org = Organization.builder()
                .status(OrganizationStatus.DEACTIVATED)
                .slug("acme")
                .name("Acme Corp")
                .build();
        assertThat(org.isActive()).isFalse();
    }

    @Test
    @DisplayName("Builder: default values match the spec")
    void builder_defaultValues() {
        Organization org = Organization.builder()
                .slug("acme")
                .name("Acme Corp")
                .build();

        assertThat(org.isMfaRequired()).isFalse();
        assertThat(org.isSelfRegistrationAllowed()).isFalse();
        assertThat(org.getPrimaryColor()).isEqualTo("#4F46E5");
    }

    @Test
    @DisplayName("Status enum: contains all 4 values")
    void status_values() {
        OrganizationStatus[] values = OrganizationStatus.values();
        assertThat(values).hasSize(4);
        assertThat(values).contains(
                OrganizationStatus.ACTIVE,
                OrganizationStatus.SUSPENDED,
                OrganizationStatus.PENDING_SETUP,
                OrganizationStatus.DEACTIVATED
        );
    }

    @Test
    @DisplayName("IdentityProviderConfig.IdpType: contains all 3 values")
    void idpType_values() {
        IdentityProviderConfig.IdpType[] values = IdentityProviderConfig.IdpType.values();
        assertThat(values).hasSize(3);
        assertThat(values).contains(
                IdentityProviderConfig.IdpType.OIDC,
                IdentityProviderConfig.IdpType.SAML,
                IdentityProviderConfig.IdpType.LDAP
        );
    }

    @Test
    @DisplayName("IdentityProviderConfig: defaults match the spec")
    void idpConfig_defaults() {
        IdentityProviderConfig config = IdentityProviderConfig.builder()
                .type(IdentityProviderConfig.IdpType.OIDC)
                .issuerUrl("https://login.microsoftonline.com/tenant/v2.0")
                .clientId("client-id")
                .clientSecret("client-secret")
                .build();

        assertThat(config.getEmailAttribute()).isEqualTo("email");
        assertThat(config.getFirstNameAttribute()).isEqualTo("given_name");
        assertThat(config.getLastNameAttribute()).isEqualTo("family_name");
        assertThat(config.isEnabled()).isTrue();
    }
}
