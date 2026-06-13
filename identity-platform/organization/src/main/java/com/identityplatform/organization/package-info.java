/**
 * Organization bounded context — manages multi-tenant organizations.
 * Publishes: OrgCreatedEvent, OrgSuspendedEvent  (see events/package-info.java for public API)
 * Does NOT depend on usermanagement module (dependency goes the other way).
 */
package com.identityplatform.organization;
