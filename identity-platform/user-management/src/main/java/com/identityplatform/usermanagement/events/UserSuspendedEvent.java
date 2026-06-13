package com.identityplatform.usermanagement.events;

import java.util.UUID;

/** Published when a user is suspended — auth-server should revoke active tokens. */
public record UserSuspendedEvent(UUID userId, UUID orgId) {}
