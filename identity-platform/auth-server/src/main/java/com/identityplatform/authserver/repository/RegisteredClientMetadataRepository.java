package com.identityplatform.authserver.repository;

import com.identityplatform.authserver.domain.RegisteredClientMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegisteredClientMetadataRepository extends JpaRepository<RegisteredClientMetadata, UUID> {
    Optional<RegisteredClientMetadata> findByRegisteredClientId(String registeredClientId);
    List<RegisteredClientMetadata> findByOrganizationId(UUID organizationId);
    boolean existsByRegisteredClientId(String registeredClientId);
}
