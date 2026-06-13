-- parking-admin application schema
-- Users are synced to IDP on creation (idp_user_id = IDP-assigned UUID)

CREATE TABLE IF NOT EXISTS app_users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    idp_user_id     UUID         UNIQUE,              -- IDP User.id (set after successful sync)
    email           VARCHAR(255) NOT NULL UNIQUE,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(30),
    vehicle_plate   VARCHAR(20),                      -- parking-specific
    status          VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    idp_sync_status VARCHAR(30)  NOT NULL DEFAULT 'PENDING', -- PENDING | SYNCED | FAILED
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_users_email       ON app_users(email);
CREATE INDEX idx_app_users_idp_user_id ON app_users(idp_user_id);
