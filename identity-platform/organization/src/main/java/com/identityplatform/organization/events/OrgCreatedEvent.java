package com.identityplatform.organization.events;

import java.util.UUID;

/** Published after a new organization is created and saved to DB. */
public record OrgCreatedEvent(
        UUID orgId,
        String orgSlug,
        String adminEmail,
        String adminFirstName,
        String adminLastName
) {}
