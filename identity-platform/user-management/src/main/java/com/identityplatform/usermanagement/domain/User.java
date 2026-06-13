package com.identityplatform.usermanagement.domain;

import com.identityplatform.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email_org", columnList = "email, organizationId", unique = true),
        @Index(name = "idx_user_org", columnList = "organizationId")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    /**
     * Tenant owner - required; each user belongs to one org
     */
    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    /**
     * Null if the user authenticates via SSO/external IdP
     */
    @Column
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    /**
     * If the user was created via an external IdP (SSO),
     * stores the external subject ID to link the account
     */
    @Column
    private String externalSubjectId;

    /**
     * User creation source: LOCAL, GOOGLE, AZURE_AD, SAML, ...
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column
    private String avatarUrl;

    @Column(name = "is_system_user", nullable = false)
    private boolean systemUser = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean mfaEnabled = false;

    @Column
    private String mfaSecret;

    @Column
    private Instant lastLoginAt;

    @Column
    private Instant emailVerifiedAt;

    @Column
    private String inviteToken;

    @Column
    private Instant inviteTokenExpiresAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isActive() {
        return UserStatus.ACTIVE.equals(this.status);
    }

    public boolean hasLocalPassword() {
        return this.passwordHash != null;
    }
}
