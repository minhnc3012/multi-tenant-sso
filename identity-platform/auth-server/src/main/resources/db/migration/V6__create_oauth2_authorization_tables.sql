-- V6: Create OAuth2 authorization tables (Spring Authorization Server 1.2+)

-- Stores registered OAuth2/OIDC clients (clients that can request tokens)
CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id                            VARCHAR(255)  PRIMARY KEY,
    client_id                   VARCHAR(255)  NOT NULL,
    client_id_issued_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret               VARCHAR(255),
    client_secret_expires_at    TIMESTAMP,
    client_name                 VARCHAR(255)  NOT NULL,
    client_authentication_methods VARCHAR(512)  NOT NULL,
    authorization_grant_types   VARCHAR(512)  NOT NULL,
    redirect_uris               VARCHAR(1024),
    post_logout_redirect_uris   VARCHAR(1024),
    scopes                      VARCHAR(1024) NOT NULL,
    client_settings             VARCHAR(2048) NOT NULL,
    token_settings              VARCHAR(2048) NOT NULL
);

-- Stores authorization codes, access tokens, refresh tokens
CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id                          VARCHAR(255)  PRIMARY KEY,
    registered_client_id      VARCHAR(255)  NOT NULL,
    principal_name              VARCHAR(255)  NOT NULL,
    authorization_code          VARCHAR(255),
    authorization_code_issued_at    TIMESTAMP,
    authorization_code_expires_at   TIMESTAMP,
    authorization_code_metadata   VARCHAR(2048),
    authorization_code_grant        TEXT,
    id_token                      TEXT,
    id_token_issued_at          TIMESTAMP,
    id_token_expires_at         TIMESTAMP,
    access_token_metadata         VARCHAR(2048),
    access_token                TEXT,
    access_token_issued_at      TIMESTAMP,
    access_token_expires_at     TIMESTAMP,
    access_token_type           VARCHAR(255),
    access_token_scopes         VARCHAR(1024),
    refresh_token               VARCHAR(255),
    refresh_token_issued_at     TIMESTAMP,
    refresh_token_expires_at    TIMESTAMP,
    refresh_token_metadata      VARCHAR(2048),
    client_authentication       TEXT,
    consent_metadata              VARCHAR(2048),
    session_id                    VARCHAR(255),
    status                      VARCHAR(255)  NOT NULL,
    revoked                     BOOLEAN       NOT NULL,
    type                        VARCHAR(255)  NOT NULL,
    scopes                      VARCHAR(1024) NOT NULL,
    CONSTRAINT fk_oauth2_auth_client FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client(id)
);

-- Stores authorization consents
CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id    VARCHAR(255)  NOT NULL,
    principal_name          VARCHAR(255)  NOT NULL,
    scopes                  VARCHAR(1024) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name),
    CONSTRAINT fk_oauth2_consent_client FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client(id)
);

CREATE INDEX IF NOT EXISTS idx_oauth2_auth_principal ON oauth2_authorization(principal_name);
CREATE INDEX IF NOT EXISTS idx_oauth2_auth_token     ON oauth2_authorization(authorization_code);
CREATE INDEX IF NOT EXISTS idx_oauth2_auth_access    ON oauth2_authorization(access_token);
CREATE INDEX IF NOT EXISTS idx_oauth2_auth_refresh   ON oauth2_authorization(refresh_token);
