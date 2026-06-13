/**
 * User Management bounded context — users, roles, permissions, MFA, password reset.
 * Publishes: UserInvitedEvent, UserSuspendedEvent, UserActivatedEvent  (see events/package-info.java)
 * Depends on: organization module (to resolve org when looking up users)
 */
package com.identityplatform.usermanagement;
