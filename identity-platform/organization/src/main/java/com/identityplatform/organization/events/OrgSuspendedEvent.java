package com.identityplatform.organization.events;

import java.util.UUID;

/** Published when an organization is suspended — listeners should react (e.g. revoke sessions, notify). */
public record OrgSuspendedEvent(UUID orgId, String orgSlug) {}
