package com.identityplatform.usermanagement.domain;

public enum AuthProvider {
    LOCAL,      // Username/password on this platform
    GOOGLE,     // Google OAuth2
    AZURE_AD,   // Microsoft Azure Active Directory
    OKTA,       // Okta OIDC
    SAML,       // Generic SAML 2.0
    LDAP        // LDAP / Active Directory
}
