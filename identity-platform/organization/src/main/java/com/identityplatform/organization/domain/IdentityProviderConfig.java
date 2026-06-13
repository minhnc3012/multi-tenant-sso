package com.identityplatform.organization.domain;

import com.identityplatform.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * IdP federation configuration for each Organization.
 * Allows tenants to use their own Azure AD/Okta/Google Workspace
 * instead of username/password on this platform.
 */
@Entity
@Table(name = "identity_provider_configs")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityProviderConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, unique = true)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdpType type;

    /**
     * OIDC: issuer URL (https://login.microsoftonline.com/{tenant}/v2.0)
     * SAML: entity ID
     */
    @Column(nullable = false)
    private String issuerUrl;

    /**
     * OIDC: client ID
     * SAML: not used
     */
    @Column
    private String clientId;

    /**
     * OIDC: client secret (encrypted)
     * SAML: not used
     */
    @Column
    private String clientSecret;

    /**
     * SAML: XML metadata URL or content
     */
    @Column(columnDefinition = "TEXT")
    private String samlMetadata;

    /**
     * Attribute mapping: email field name from the IdP
     */
    @Column
    @Builder.Default
    private String emailAttribute = "email";

    /**
     * Attribute mapping: first name field
     */
    @Column
    @Builder.Default
    private String firstNameAttribute = "given_name";

    /**
     * Attribute mapping: last name field
     */
    @Column
    @Builder.Default
    private String lastNameAttribute = "family_name";

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    public enum IdpType {
        OIDC,    // Azure AD, Google, Okta via OIDC
        SAML,    // Enterprise SAML 2.0
        LDAP     // Active Directory / LDAP
    }
}
