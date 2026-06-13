package com.identityplatform.authserver.domain;

public enum ClientType {
    /** Browser-based app — authorization_code + client_secret */
    WEB_CLIENT,
    /** Server-to-server — client_credentials */
    API_CLIENT,
    /** Mobile / native app — authorization_code + PKCE, no secret */
    MOBILE_CLIENT,
    /** Machine-to-machine daemon — client_credentials */
    M2M_CLIENT
}
