package com.identityplatform.usermanagement.controller;

import com.identityplatform.core.tenant.TenantContext;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.dto.UserDto;
import com.identityplatform.usermanagement.service.MfaService;
import com.identityplatform.usermanagement.service.PasswordResetService;
import com.identityplatform.usermanagement.service.RoleService;
import com.identityplatform.usermanagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management within an organization")
public class UserController {

    private final UserService userService;
    private final MfaService mfaService;
    private final PasswordResetService passwordResetService;
    private final RoleService roleService;

    @PostMapping("/invite")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Invite a new user to the organization")
    public ResponseEntity<UserDto.Response> invite(
            @Valid @RequestBody UserDto.InviteRequest request) {

        boolean isPlatformAdmin = isPlatformAdmin();

        UUID orgId = (isPlatformAdmin && request.organizationId() != null)
                ? request.organizationId()
                : TenantContext.getCurrentTenant();

        User user = userService.inviteUser(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @GetMapping
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "List users in the current organization (scoped by X-Organization-Slug header)")
    public ResponseEntity<Page<UserDto.Response>> list(
            @PageableDefault(size = 20) Pageable pageable) {

        UUID orgId = TenantContext.getCurrentTenant();
        Page<User> users = userService.findByOrganization(orgId, pageable);
        return ResponseEntity.ok(users.map(this::toResponse));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "List all users across all organizations (PLATFORM_ADMIN only)")
    public ResponseEntity<Page<UserDto.Response>> listAll(
            @PageableDefault(size = 20) Pageable pageable) {

        Page<User> users = userService.findAll(pageable);
        return ResponseEntity.ok(users.map(this::toResponse));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ORG_ADMIN') or @userAccessChecker.isSelf(#userId)")
    @Operation(summary = "Get details of a single user")
    public ResponseEntity<UserDto.Response> getById(@PathVariable UUID userId) {
        UUID orgId = TenantContext.getCurrentTenant();
        User user = userService.findByIdAndOrg(userId, orgId);
        return ResponseEntity.ok(toResponse(user));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Update user profile (firstName, lastName, email, avatarUrl)")
    public ResponseEntity<UserDto.Response> update(
            @PathVariable UUID userId,
            @Valid @RequestBody UserDto.UpdateRequest request) {

        boolean isPlatformAdmin = isPlatformAdmin();

        // Resolve the target user first to check systemUser flag
        User target = isPlatformAdmin
                ? userService.findById(userId)
                : userService.findByIdAndOrg(userId, TenantContext.getCurrentTenant());

        // System admin can only edit their own profile; nobody else can touch it
        if (target.isSystemUser() && !isSelf(target)) {
            throw new AccessDeniedException("System admin account can only be modified by the account owner");
        }

        User updated = isPlatformAdmin
                ? userService.updateUserAsAdmin(userId, request)
                : userService.updateUser(TenantContext.getCurrentTenant(), userId, request);

        return ResponseEntity.ok(toResponse(updated));
    }

    @PostMapping("/{userId}/suspend")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Suspend a user")
    public ResponseEntity<Void> suspend(@PathVariable UUID userId) {
        UUID orgId = TenantContext.getCurrentTenant();
        guardNotSystemUser(userService.findByIdAndOrg(userId, orgId), "suspended");
        userService.suspendUser(orgId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/activate")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Reactivate a suspended user")
    public ResponseEntity<Void> activate(@PathVariable UUID userId) {
        UUID orgId = TenantContext.getCurrentTenant();
        guardNotSystemUser(userService.findByIdAndOrg(userId, orgId), "activated");
        userService.activateUser(orgId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Deactivate a user (soft delete)")
    public ResponseEntity<Void> deactivate(@PathVariable UUID userId) {
        UUID orgId = TenantContext.getCurrentTenant();
        guardNotSystemUser(userService.findByIdAndOrg(userId, orgId), "deactivated");
        userService.deactivateUser(orgId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── Role assignment ───────────────────────────────────────────

    @PostMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Assign a role to a user")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID userId,
            @PathVariable UUID roleId) {
        UUID orgId = TenantContext.getCurrentTenant();
        guardNotSystemUser(userService.findByIdAndOrg(userId, orgId), "role-assigned");
        roleService.assignRoleToUser(orgId, userId, roleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Remove a role from a user")
    public ResponseEntity<Void> removeRole(
            @PathVariable UUID userId,
            @PathVariable UUID roleId) {
        UUID orgId = TenantContext.getCurrentTenant();
        guardNotSystemUser(userService.findByIdAndOrg(userId, orgId), "role-removed");
        roleService.removeRoleFromUser(orgId, userId, roleId);
        return ResponseEntity.noContent().build();
    }

    // ── Invite Accept (public — no auth required) ────────────────────────

    @PostMapping("/invite/accept")
    @Operation(summary = "Accept an invitation and set a password")
    public ResponseEntity<UserDto.Response> acceptInvite(
            @Valid @RequestBody UserDto.AcceptInviteRequest request) {
        User user = userService.acceptInvite(request.token(), request.password());
        return ResponseEntity.ok(toResponse(user));
    }

    // ── Password ──────────────────────────────────────────────────────────

    @PostMapping("/password/reset-request")
    @Operation(summary = "Request a password reset (Forgot Password)")
    public ResponseEntity<Void> requestPasswordReset(
            @RequestBody PasswordResetRequestDto request) {

        UUID orgId = TenantContext.getCurrentTenant();
        passwordResetService.requestPasswordReset(request.email(), orgId);
        // Always return 204 regardless of whether the email exists (anti-enumeration)
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Reset password using a reset token")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody PasswordResetDto request) {

        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/change")
    @Operation(summary = "Change password (user-initiated)")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserDto.ChangePasswordRequest request) {

        UUID userId = UUID.fromString(jwt.getSubject());
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    // ── MFA ───────────────────────────────────────────────────────────────

    @PostMapping("/mfa/setup")
    @Operation(summary = "Begin MFA setup - returns QR code URI")
    public ResponseEntity<MfaSetupResponseDto> setupMfa(
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = UUID.fromString(jwt.getSubject());
        MfaService.MfaSetupResult result = mfaService.generateMfaSecret(userId, "Identity Platform");

        return ResponseEntity.ok(new MfaSetupResponseDto(
                result.otpAuthUri(),
                result.base32Secret()
        ));
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "Confirm MFA setup using TOTP code from the app")
    public ResponseEntity<Void> enableMfa(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody MfaVerifyDto request) {

        UUID userId = UUID.fromString(jwt.getSubject());
        boolean success = mfaService.enableMfa(userId, request.code());

        if (!success) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/disable")
    @Operation(summary = "Disable MFA (requires code verification first)")
    public ResponseEntity<Void> disableMfa(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody MfaVerifyDto request) {

        UUID userId = UUID.fromString(jwt.getSubject());
        mfaService.disableMfa(userId, request.code());
        return ResponseEntity.noContent().build();
    }

    // ── Guards ────────────────────────────────────────────────────────────

    /** Throws 403 if user is a system account (cannot be suspended/deactivated/role-changed by anyone). */
    private void guardNotSystemUser(User user, String operation) {
        if (user.isSystemUser()) {
            throw new AccessDeniedException(
                    "System admin account cannot be " + operation);
        }
    }

    /**
     * Returns true when the currently authenticated principal is the same person as the target user.
     * Works for both session-based auth (principal name = email) and JWT auth (principal name = UUID subject).
     */
    private boolean isSelf(User target) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        String callerName = auth.getName();
        return callerName.equals(target.getEmail())
                || callerName.equals(target.getId().toString());
    }

    private boolean isPlatformAdmin() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
    }

    // ── SDK endpoints (M2M — called by idp-client-sdk) ──────────────────────

    /**
     * Lists users for an org — accessible by M2M client_credentials token with scope users:read.
     * Used by org apps to pull IDP users down for local sync.
     */
    @GetMapping("/org/{orgId}")
    @PreAuthorize("hasAuthority('SCOPE_users:read') or hasRole('PLATFORM_ADMIN') or hasRole('ORG_ADMIN')")
    @Operation(summary = "List users for an org (M2M SDK endpoint)")
    public ResponseEntity<Page<UserDto.Response>> listByOrgForSdk(
            @PathVariable UUID orgId,
            @PageableDefault(size = 100) Pageable pageable) {
        Page<User> users = userService.findByOrganization(orgId, pageable);
        return ResponseEntity.ok(users.map(this::toResponse));
    }

    // ── SDK Provision endpoint ────────────────────────────────────────────────

    /**
     * Creates a user directly in the IDP on behalf of an org app.
     * Secured by M2M client_credentials token with scope users:write.
     * The user receives an invite email to set their IDP password.
     */
    @PostMapping("/provision/{orgId}")
    @PreAuthorize("hasAuthority('SCOPE_users:write') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Provision a user from an org app (M2M SDK endpoint)")
    public ResponseEntity<UserDto.Response> provision(
            @PathVariable UUID orgId,
            @Valid @RequestBody UserDto.ProvisionRequest request) {
        User user = userService.provisionDirectUser(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    // ── Mappers & DTOs ────────────────────────────────────────────────────

    private UserDto.Response toResponse(User user) {
        var roles = user.getRoles() == null ? Set.<UserDto.RoleSummary>of() :
                user.getRoles().stream()
                        .map(r -> new UserDto.RoleSummary(r.getId(), r.getName(), r.isSystemRole()))
                        .collect(Collectors.toSet());
        return new UserDto.Response(
                user.getId(),
                user.getOrganizationId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getFullName(),
                user.getStatus(),
                user.getAuthProvider(),
                user.isMfaEnabled(),
                user.getAvatarUrl(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                roles
        );
    }

    public record PasswordResetRequestDto(@Email @NotBlank String email) {}

    public record PasswordResetDto(
            @NotBlank String token,
            @NotBlank String newPassword
    ) {}

    public record MfaSetupResponseDto(String otpAuthUri, String manualEntryKey) {}

    public record MfaVerifyDto(@NotBlank String code) {}
}
