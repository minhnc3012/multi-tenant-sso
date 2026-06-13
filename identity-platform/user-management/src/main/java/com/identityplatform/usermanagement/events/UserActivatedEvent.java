package com.identityplatform.usermanagement.events;

import java.util.UUID;

/** Published when a suspended user is reactivated. */
public record UserActivatedEvent(UUID userId, UUID orgId) {}
