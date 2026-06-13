package com.identityplatform.sdk.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an IDP user returned from the provision / update / list endpoints.
 * Store {@code id} as {@code idpUserId} in the calling app's DB.
 */
public record IdpUserResponse(
        UUID id,
        UUID organizationId,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String status,
        Instant createdAt,
        Set<RoleSummary> roles
) {
    public record RoleSummary(UUID id, String name, boolean systemRole) {}
}
