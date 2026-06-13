package com.identityplatform.usermanagement.domain;

public enum UserStatus {
    PENDING_VERIFICATION,  // Newly created, email not yet verified
    ACTIVE,                // Currently active and in normal use
    SUSPENDED,             // Temporarily suspended by an org admin
    LOCKED,                // Locked due to too many failed login attempts
    DEACTIVATED            // Deleted or deactivated
}
