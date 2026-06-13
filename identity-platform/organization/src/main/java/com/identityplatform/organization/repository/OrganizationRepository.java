package com.identityplatform.organization.repository;

import com.identityplatform.organization.domain.Organization;
import com.identityplatform.organization.domain.OrganizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Page<Organization> findByDeletedFalse(Pageable pageable);

    Optional<Organization> findBySlugAndDeletedFalse(String slug);

    Optional<Organization> findByPrimaryDomainAndDeletedFalse(String domain);

    boolean existsBySlugAndDeletedFalse(String slug);

    boolean existsByPrimaryDomainAndDeletedFalse(String domain);

    @Query("SELECT o FROM Organization o WHERE o.id = :id AND o.deleted = false AND o.status = :status")
    Optional<Organization> findActiveById(UUID id, OrganizationStatus status);

    default Optional<Organization> findActiveById(UUID id) {
        return findActiveById(id, OrganizationStatus.ACTIVE);
    }
}
