package com.identityplatform.organization.repository;

import com.identityplatform.organization.domain.IdentityProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdpConfigRepository extends JpaRepository<IdentityProviderConfig, UUID> {

    Optional<IdentityProviderConfig> findByOrganizationId(UUID organizationId);

    Optional<IdentityProviderConfig> findByOrganizationIdAndEnabledTrue(UUID organizationId);

    boolean existsByOrganizationIdAndEnabledTrue(UUID organizationId);
}
