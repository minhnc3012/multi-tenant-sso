package com.identityplatform.sdk.model;

import java.util.Set;

/**
 * Payload sent to POST /api/v1/users/provision/{orgId}.
 *
 * @param email          User's email address (must be unique within the org in IDP).
 * @param firstName      User's first name.
 * @param lastName       User's last name.
 * @param externalUserId The ID of this user in the calling app's DB (stored as externalSubjectId in IDP).
 * @param roles          Role names to assign (e.g. "ORG_MEMBER"). Defaults to ORG_MEMBER if empty.
 */
public record IdpCreateUserRequest(
        String email,
        String firstName,
        String lastName,
        String externalUserId,
        Set<String> roles
) {}
