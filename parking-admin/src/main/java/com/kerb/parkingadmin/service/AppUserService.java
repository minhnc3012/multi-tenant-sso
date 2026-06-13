package com.kerb.parkingadmin.service;

import com.identityplatform.sdk.model.IdpCreateUserRequest;
import com.identityplatform.sdk.model.IdpUserResponse;
import com.identityplatform.sdk.service.IdpUserSyncService;
import com.kerb.parkingadmin.domain.AppUser;
import com.kerb.parkingadmin.domain.AppUser.AppUserStatus;
import com.kerb.parkingadmin.domain.AppUser.IdpSyncStatus;
import com.kerb.parkingadmin.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository userRepository;
    private final IdpUserSyncService idpUserSyncService;

    /**
     * "IDP first" create:
     * 1. Sync to IDP → get idpUserId
     * 2. Save locally with idpUserId
     * If IDP call fails → exception → nothing is saved locally (atomic).
     */
    @Transactional
    public AppUser createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use: " + request.email());
        }

        // Step 1: sync to IDP
        Set<String> idpRoles = (request.idpRoles() == null || request.idpRoles().isEmpty())
                ? Set.of("ORG_MEMBER")
                : request.idpRoles();
        IdpUserResponse idpUser = idpUserSyncService.createUser(new IdpCreateUserRequest(
                request.email(),
                request.firstName(),
                request.lastName(),
                null,       // externalUserId filled after local save below
                idpRoles
        ));

        // Step 2: save locally
        AppUser user = AppUser.builder()
                .idpUserId(idpUser.id())
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .vehiclePlate(request.vehiclePlate())
                .status(AppUserStatus.ACTIVE)
                .idpSyncStatus(IdpSyncStatus.SYNCED)
                .build();

        AppUser saved = userRepository.save(user);
        log.info("User created: id={}, idpUserId={}, email={}", saved.getId(), idpUser.id(), request.email());
        return saved;
    }

    @Transactional
    public AppUser updateUser(UUID id, UpdateUserRequest request) {
        AppUser user = findById(id);

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setVehiclePlate(request.vehiclePlate());

        // Sync profile update to IDP if user is already synced
        if (user.getIdpSyncStatus() == IdpSyncStatus.SYNCED && user.getIdpUserId() != null) {
            try {
                idpUserSyncService.updateUser(user.getIdpUserId(), new IdpCreateUserRequest(
                        user.getEmail(),
                        request.firstName(),
                        request.lastName(),
                        user.getId().toString(),
                        Set.of("ORG_MEMBER")
                ));
            } catch (Exception e) {
                log.warn("IDP sync update failed for user {}: {}", id, e.getMessage());
                // Non-fatal: local update still proceeds
            }
        }

        return userRepository.save(user);
    }

    @Transactional
    public void suspendUser(UUID id) {
        AppUser user = findById(id);
        user.setStatus(AppUserStatus.SUSPENDED);
        userRepository.save(user);
        log.info("User suspended: id={}", id);
    }

    @Transactional
    public void activateUser(UUID id) {
        AppUser user = findById(id);
        user.setStatus(AppUserStatus.ACTIVE);
        userRepository.save(user);
        log.info("User activated: id={}", id);
    }

    @Transactional
    public void deleteUser(UUID id) {
        AppUser user = findById(id);
        user.setStatus(AppUserStatus.DEACTIVATED);

        if (user.getIdpSyncStatus() == IdpSyncStatus.SYNCED && user.getIdpUserId() != null) {
            try {
                idpUserSyncService.deactivateUser(user.getIdpUserId());
            } catch (Exception e) {
                log.warn("IDP deactivate failed for user {}: {}", id, e.getMessage());
            }
        }

        userRepository.save(user);
        log.info("User deactivated: id={}", id);
    }

    /**
     * Pulls all users from the IDP for this org and upserts them locally.
     * - Match by idpUserId: if found locally → update profile if anything changed
     * - No local record: create new AppUser with ACTIVE status + SYNCED
     *
     * @return sync result summary
     */
    @Transactional
    public SyncResult syncFromIdp() {
        var idpUsers = idpUserSyncService.listAllUsers();
        int created = 0, updated = 0, skipped = 0;

        for (var idpUser : idpUsers) {
            var existing = userRepository.findByIdpUserId(idpUser.id());
            if (existing.isPresent()) {
                AppUser local = existing.get();
                boolean changed = !idpUser.firstName().equals(local.getFirstName())
                        || !idpUser.lastName().equals(local.getLastName())
                        || !idpUser.email().equals(local.getEmail());
                if (changed) {
                    local.setFirstName(idpUser.firstName());
                    local.setLastName(idpUser.lastName());
                    local.setEmail(idpUser.email());
                    local.setIdpSyncStatus(IdpSyncStatus.SYNCED);
                    userRepository.save(local);
                    updated++;
                } else {
                    skipped++;
                }
            } else {
                AppUser newUser = AppUser.builder()
                        .idpUserId(idpUser.id())
                        .email(idpUser.email())
                        .firstName(idpUser.firstName())
                        .lastName(idpUser.lastName())
                        .status(AppUserStatus.ACTIVE)
                        .idpSyncStatus(IdpSyncStatus.SYNCED)
                        .build();
                userRepository.save(newUser);
                created++;
            }
        }
        log.info("[Sync] IDP→local: created={}, updated={}, skipped={}", created, updated, skipped);
        return new SyncResult(idpUsers.size(), created, updated, skipped);
    }

    @Transactional(readOnly = true)
    public Page<AppUser> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return userRepository.findAll(pageable);
        }
        return userRepository.searchByKeyword(query.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public AppUser findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    // ── Request records ────────────────────────────────────────────────────────

    public record CreateUserRequest(
            String email,
            String firstName,
            String lastName,
            String phone,
            String vehiclePlate,
            Set<String> idpRoles
    ) {}

    public record UpdateUserRequest(
            String firstName,
            String lastName,
            String phone,
            String vehiclePlate
    ) {}

    public record SyncResult(int total, int created, int updated, int skipped) {}
}
