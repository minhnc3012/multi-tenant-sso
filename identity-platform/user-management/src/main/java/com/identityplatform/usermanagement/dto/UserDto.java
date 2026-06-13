package com.identityplatform.usermanagement.dto;

import com.identityplatform.usermanagement.domain.AuthProvider;
import com.identityplatform.usermanagement.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class UserDto {

    // Records use canonical constructors — @Builder is not needed
    public record InviteRequest(
            @NotBlank @Email
            String email,

            @NotBlank
            String firstName,

            @NotBlank
            String lastName,

            /**
             * Assign roles by UUID. Use {@code roleNames} when UUIDs are unknown.
             * If both are empty, user is assigned the default ORG_MEMBER role.
             */
            Set<UUID> roleIds,

            /**
             * Assign roles by name — easier to use than UUID.
             * Accepted values: "PLATFORM_ADMIN", "ORG_ADMIN", "ORG_MEMBER", or any custom role name.
             * Takes precedence over {@code roleIds} if both are provided.
             * Example: ["ORG_ADMIN"]
             */
            Set<String> roleNames,

            /** PLATFORM_ADMIN only: invite user into a specific org instead of the caller's org. */
            UUID organizationId
    ) {}

    public record SsoProvisionRequest(
            String email,
            String firstName,
            String lastName,
            String externalSubjectId,
            AuthProvider authProvider
    ) {}

    public record Response(
            UUID id,
            UUID organizationId,
            String email,
            String firstName,
            String lastName,
            String fullName,
            UserStatus status,
            AuthProvider authProvider,
            boolean mfaEnabled,
            String avatarUrl,
            Instant lastLoginAt,
            Instant createdAt,
            Set<RoleSummary> roles
    ) {}

    /** Lightweight role info included in user responses. */
    public record RoleSummary(UUID id, String name, boolean systemRole) {}

    public record UpdateRequest(
            @Email
            String email,

            String firstName,

            String lastName,

            String avatarUrl
    ) {}

    public record ChangePasswordRequest(
            @NotBlank
            String currentPassword,

            @NotBlank
            String newPassword
    ) {}

    public record AcceptInviteRequest(
            @NotBlank String token,
            @NotBlank String password
    ) {}

    /**
     * Used by the idp-client-sdk: org apps call POST /api/v1/users/provision/{orgId}
     * to create a user in the IDP when the user is first created in the org's own DB.
     * An invite email is sent so the user can set their IDP password for SSO.
     */
    public record ProvisionRequest(
            @NotBlank @Email
            String email,

            @NotBlank
            String firstName,

            @NotBlank
            String lastName,

            /** The ID of this user in the calling app's own database — stored as externalSubjectId. */
            String externalUserId,

            /**
             * Role names to assign (e.g. "ORG_MEMBER", "ORG_ADMIN").
             * Must match system roles seeded in the IDP.
             * Defaults to ORG_MEMBER if empty.
             */
            Set<String> roles
    ) {}
}
