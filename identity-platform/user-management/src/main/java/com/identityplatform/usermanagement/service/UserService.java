package com.identityplatform.usermanagement.service;

import com.identityplatform.core.exception.DuplicateResourceException;
import com.identityplatform.core.exception.ResourceNotFoundException;
import com.identityplatform.usermanagement.domain.AuthProvider;
import com.identityplatform.usermanagement.domain.Role;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.domain.UserStatus;
import com.identityplatform.usermanagement.dto.UserDto;
import com.identityplatform.usermanagement.repository.UserRepository;
import com.identityplatform.usermanagement.events.UserActivatedEvent;
import com.identityplatform.usermanagement.events.UserInvitedEvent;
import com.identityplatform.usermanagement.events.UserSuspendedEvent;
import com.identityplatform.usermanagement.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InviteService inviteService;
    private final ApplicationEventPublisher events;
    private final RoleRepository roleRepository;

    /**
     * Creates a new user and sends an invite email.
     * Used when an org admin invites a new member.
     */
    @Transactional
    public User inviteUser(UUID organizationId, UserDto.InviteRequest request) {
        if (userRepository.existsByEmailAndOrganizationIdAndDeletedFalse(
                request.email(), organizationId)) {
            throw new DuplicateResourceException(
                    "User with email '" + request.email() + "' already exists in this organization");
        }

        Set<Role> roles = resolveRolesForInvite(organizationId, request.roleNames(), request.roleIds());

        User user = User.builder()
                .organizationId(organizationId)
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .status(UserStatus.PENDING_VERIFICATION)
                .authProvider(AuthProvider.LOCAL)
                .roles(roles)
                .build();

        user = userRepository.save(user);

        // Send invite email with token
        inviteService.sendInvite(user);
        events.publishEvent(new UserInvitedEvent(user.getId(), organizationId, request.email()));
        log.info("User invited: userId={}, orgId={}, email={}, roles={}",
                user.getId(), organizationId, request.email(),
                roles.stream().map(Role::getName).toList());
        return user;
    }

    /**
     * Resolves roles for invite/provision requests.
     * Priority: roleNames > roleIds > default ORG_MEMBER.
     */
    private Set<Role> resolveRolesForInvite(UUID organizationId,
                                             Set<String> roleNames,
                                             Set<UUID> roleIds) {
        // 1. Resolve by name (most convenient for API callers)
        if (roleNames != null && !roleNames.isEmpty()) {
            Set<Role> resolved = roleNames.stream()
                    .map(name -> roleRepository.findByNameAndAccessibleToOrg(name, organizationId).orElse(null))
                    .filter(r -> r != null)
                    .collect(Collectors.toSet());
            if (!resolved.isEmpty()) return resolved;
            log.warn("[Roles] roleNames {} could not be resolved for org={}", roleNames, organizationId);
        }

        // 2. Resolve by UUID
        if (roleIds != null && !roleIds.isEmpty()) {
            Set<Role> resolved = roleIds.stream()
                    .map(id -> roleRepository.findById(id).orElse(null))
                    .filter(r -> r != null && !r.isDeleted())
                    .filter(r -> r.isSystemRole() || organizationId.equals(r.getOrganizationId()))
                    .collect(Collectors.toSet());
            if (!resolved.isEmpty()) return resolved;
        }

        // 3. Default: ORG_MEMBER
        return roleRepository.findByNameAndSystemRoleTrue("ORG_MEMBER")
                .map(r -> Set.of(r))
                .orElse(Collections.emptySet());
    }

    /**
     * Provision a user on behalf of an org app via the idp-client-sdk (M2M).
     * Creates the user with PENDING_VERIFICATION and sends an invite email so the
     * user can set their IDP password and use SSO across all org apps.
     *
     * @param organizationId target org
     * @param request        provision payload from the calling app
     * @return the newly created IDP User (id usable as idpUserId in the calling app)
     */
    @Transactional
    public User provisionDirectUser(UUID organizationId, UserDto.ProvisionRequest request) {
        if (userRepository.existsByEmailAndOrganizationIdAndDeletedFalse(
                request.email(), organizationId)) {
            throw new DuplicateResourceException(
                    "User with email '" + request.email() + "' already exists in this organization");
        }

        Set<String> roleNames = (request.roles() == null || request.roles().isEmpty())
                ? Set.of("ORG_MEMBER")
                : request.roles();

        Set<Role> resolvedRoles = roleNames.stream()
                .map(name -> roleRepository.findByNameAndAccessibleToOrg(name, organizationId).orElse(null))
                .filter(r -> r != null)
                .collect(Collectors.toSet());

        User user = User.builder()
                .organizationId(organizationId)
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .status(UserStatus.PENDING_VERIFICATION)
                .authProvider(AuthProvider.LOCAL)
                .externalSubjectId(request.externalUserId())
                .roles(resolvedRoles)
                .build();

        user = userRepository.save(user);
        inviteService.sendInvite(user);
        events.publishEvent(new UserInvitedEvent(user.getId(), organizationId, request.email()));
        log.info("[SDK] User provisioned: userId={}, orgId={}, email={}", user.getId(), organizationId, request.email());
        return user;
    }

    /**
     * Just-in-time provisioning: creates a user on their first SSO login.
     * No prior invite is required.
     */
    @Transactional
    public User provisionSsoUser(UUID organizationId, UserDto.SsoProvisionRequest request) {
        return userRepository
                .findByExternalSubjectIdAndOrganizationId(request.externalSubjectId(), organizationId)
                .orElseGet(() -> {
                    Set<Role> roles = roleRepository.findByNameAndSystemRoleTrue("ORG_MEMBER")
                            .map(Set::of)
                            .orElse(Collections.emptySet());

                    User user = User.builder()
                            .organizationId(organizationId)
                            .email(request.email())
                            .firstName(request.firstName())
                            .lastName(request.lastName())
                            .externalSubjectId(request.externalSubjectId())
                            .authProvider(request.authProvider())
                            .status(UserStatus.ACTIVE)
                            .emailVerifiedAt(Instant.now())
                            .roles(roles)
                            .build();

                    user = userRepository.save(user);
                    log.info("SSO user provisioned: userId={}, orgId={}, provider={}",
                            user.getId(), organizationId, request.authProvider());
                    return user;
                });
    }

    @Transactional
    public void recordLogin(UUID userId) {
        userRepository.updateLastLoginAt(userId, Instant.now());
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = findById(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public User updateUser(UUID organizationId, UUID userId, UserDto.UpdateRequest request) {
        User user = findByIdAndOrg(userId, organizationId);
        return applyUpdate(user, organizationId, request);
    }

    @Transactional
    public User updateUserAsAdmin(UUID userId, UserDto.UpdateRequest request) {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return applyUpdate(user, user.getOrganizationId(), request);
    }

    private User applyUpdate(User user, UUID organizationId, UserDto.UpdateRequest request) {
        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmailAndOrganizationIdAndDeletedFalse(request.email(), organizationId)) {
                throw new DuplicateResourceException("Email '" + request.email() + "' is already in use in this organization");
            }
            user.setEmail(request.email());
        }
        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null)  user.setLastName(request.lastName());
        if (request.avatarUrl() != null)  user.setAvatarUrl(request.avatarUrl());
        return userRepository.save(user);
    }

    @Transactional
    public void suspendUser(UUID organizationId, UUID userId) {
        User user = findByIdAndOrg(userId, organizationId);
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
        events.publishEvent(new UserSuspendedEvent(userId, organizationId));
        log.warn("User suspended: userId={}, orgId={}", userId, organizationId);
    }

    @Transactional(readOnly = true)
    public Page<User> findByOrganization(UUID organizationId, Pageable pageable) {
        return userRepository.findByOrganizationIdAndDeletedFalse(organizationId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAllByDeletedFalse(pageable);
    }

    @Transactional(readOnly = true)
    public User findById(UUID id) {
        return userRepository.findById(id)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    /**
     * Returns plain-data claims for JWT token generation.
     * Resolves all lazy collections INSIDE the transaction so no entity escapes with a live proxy.
     */
    @Transactional(readOnly = true)
    public UserClaims findClaimsById(UUID id) {
        User user = userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        Set<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .collect(Collectors.toSet());

        return new UserClaims(
                user.getOrganizationId(),
                user.getEmail(),
                user.getFullName(),
                roles,
                permissions
        );
    }

    /** Plain-data snapshot of user claims — no Hibernate proxies, safe to use after transaction ends. */
    public record UserClaims(
            UUID organizationId,
            String email,
            String fullName,
            Set<String> roles,
            Set<String> permissions
    ) {}

    @Transactional(readOnly = true)
    public User findByIdAndOrg(UUID id, UUID organizationId) {
        return userRepository.findByIdAndOrganizationIdAndDeletedFalse(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Transactional(readOnly = true)
    public User findByEmailAndOrg(String email, UUID organizationId) {
        return userRepository.findByEmailAndOrganizationIdAndDeletedFalse(email, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + email));
    }

    @Transactional
    public User acceptInvite(String token, String rawPassword) {
        String encoded = passwordEncoder.encode(rawPassword);
        return inviteService.acceptInvite(token, rawPassword, encoded);
    }

    @Transactional
    public void activateUser(UUID organizationId, UUID userId) {
        User user = findByIdAndOrg(userId, organizationId);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        events.publishEvent(new UserActivatedEvent(userId, organizationId));
        log.info("User activated: userId={}, orgId={}", userId, organizationId);
    }

    @Transactional
    public void deactivateUser(UUID organizationId, UUID userId) {
        User user = findByIdAndOrg(userId, organizationId);
        user.setStatus(UserStatus.DEACTIVATED);
        user.setDeleted(true);
        userRepository.save(user);
        log.warn("User deactivated: userId={}, orgId={}", userId, organizationId);
    }
}
