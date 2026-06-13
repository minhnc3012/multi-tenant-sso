package com.identityplatform.organization.domain;

import com.identityplatform.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Organization = a Tenant in the multi-tenant system.
 * Each B2B company/customer is a separate Organization.
 */
@Entity
@Table(name = "organizations", indexes = {
        @Index(name = "idx_org_slug", columnList = "slug", unique = true),
        @Index(name = "idx_org_domain", columnList = "primaryDomain")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Organization extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /**
     * Slug used for subdomain: {slug}.yourplatform.com
     * Also used to detect the tenant from the URL
     */
    @Column(nullable = false, unique = true)
    private String slug;

    /**
     * Primary domain of the org, used to auto-detect the tenant from email
     * Example: "company.com" → when a user logs in as user@company.com
     * the system knows they belong to this org
     */
    @Column
    private String primaryDomain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    /**
     * Config: whether MFA is required
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean mfaRequired = false;

    /**
     * Config: whether users are allowed to self-register
     * or only admins can send invitations
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean selfRegistrationAllowed = false;

    /**
     * Branding: org logo URL to display on the login page
     */
    @Column
    private String logoUrl;

    /**
     * Branding: primary color of the org
     */
    @Column
    @Builder.Default
    private String primaryColor = "#4F46E5";

    /**
     * If this org uses external IdP federation
     * (e.g., they want to use their own Azure AD)
     */
    @OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private IdentityProviderConfig identityProviderConfig;

    public boolean isActive() {
        return OrganizationStatus.ACTIVE.equals(this.status);
    }
}
