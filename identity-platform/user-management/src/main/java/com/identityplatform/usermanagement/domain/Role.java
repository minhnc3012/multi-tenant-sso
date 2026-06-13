package com.identityplatform.usermanagement.domain;

import com.identityplatform.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles", indexes = {
        @Index(name = "idx_role_org_name", columnList = "organizationId, name", unique = true)
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseEntity {

    /**
     * Null = system-wide role (PLATFORM_ADMIN)
     * Non-null = org-specific role
     */
    @Column
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    /**
     * System roles cannot be deleted or modified
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean systemRole = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission")
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    // Built-in system roles
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String ORG_ADMIN = "ORG_ADMIN";
    public static final String ORG_MEMBER = "ORG_MEMBER";
}
