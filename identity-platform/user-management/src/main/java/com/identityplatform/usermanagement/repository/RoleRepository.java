package com.identityplatform.usermanagement.repository;

import com.identityplatform.usermanagement.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    boolean existsByNameAndOrganizationId(String name, UUID organizationId);

    Optional<Role> findByNameAndSystemRoleTrue(String name);

    /** Find a role by name — either a system role or an org-specific role. */
    @Query("SELECT r FROM Role r WHERE r.deleted = false AND r.name = :name " +
           "AND (r.systemRole = true OR r.organizationId = :orgId)")
    Optional<Role> findByNameAndAccessibleToOrg(String name, UUID orgId);

    /**
     * Returns system roles + org-specific roles
     */
    @Query("SELECT r FROM Role r WHERE r.deleted = false AND (r.systemRole = true OR r.organizationId = :orgId)")
    List<Role> findByOrganizationIdOrSystemRoleTrue(UUID orgId);
}
