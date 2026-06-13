package com.identityplatform.usermanagement.events;

import java.util.UUID;

/** Published after an invite email is sent to a new user. */
public record UserInvitedEvent(UUID userId, UUID orgId, String email) {}
