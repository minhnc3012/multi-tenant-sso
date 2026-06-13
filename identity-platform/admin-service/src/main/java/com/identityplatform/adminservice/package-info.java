/**
 * Admin Service module — cross-cutting infrastructure:
 *  SecurityConfig, TenantResolutionFilter, AuditEventListener.
 *
 * Event subscriptions (via @TransactionalEventListener + @Async):
 *  - OrgCreatedEvent, OrgSuspendedEvent  (from organization module)
 *  - UserInvitedEvent, UserSuspendedEvent, UserActivatedEvent  (from user-management module)
 */
package com.identityplatform.adminservice;
