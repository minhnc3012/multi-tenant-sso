package com.kerb.parkingadmin.repository;

import com.kerb.parkingadmin.domain.AppUser;
import com.kerb.parkingadmin.domain.AppUser.IdpSyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByEmail(String email);

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByIdpUserId(UUID idpUserId);

    @Query("SELECT u FROM AppUser u WHERE " +
           "LOWER(u.email)     LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<AppUser> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    List<AppUser> findByIdpSyncStatus(IdpSyncStatus status);
}
