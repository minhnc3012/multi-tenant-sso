package com.identityplatform.usermanagement.repository;

import com.identityplatform.usermanagement.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndOrganizationIdAndDeletedFalse(String email, UUID organizationId);

    Optional<User> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    Optional<User> findByExternalSubjectIdAndOrganizationId(String externalSubjectId, UUID organizationId);

    Page<User> findByOrganizationIdAndDeletedFalse(UUID organizationId, Pageable pageable);

    boolean existsByEmailAndOrganizationIdAndDeletedFalse(String email, UUID organizationId);

    Optional<User> findByInviteTokenAndDeletedFalse(String inviteToken);

    default Optional<User> findByInviteToken(String token) {
        return findByInviteTokenAndDeletedFalse(token);
    }

    Page<User> findAllByDeletedFalse(Pageable pageable);

    /** Fetches user with roles in a single query — use when roles are needed outside a transaction. */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id AND u.deleted = false")
    Optional<User> findByIdWithRoles(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginAt WHERE u.id = :userId")
    void updateLastLoginAt(UUID userId, Instant loginAt);
}
