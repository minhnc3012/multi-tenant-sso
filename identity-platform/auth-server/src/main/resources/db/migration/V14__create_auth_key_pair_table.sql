-- Persistent RSA key pair storage for JWT signing.
-- On first startup the auth-server generates a 2048-bit RSA key and inserts it here.
-- Subsequent startups load the persisted key so JWTs remain valid across restarts.
-- To rotate: insert a new row (active=true), mark the old row active=false,
-- then redeploy (old tokens remain verifiable until they expire via JWKS endpoint).
CREATE TABLE IF NOT EXISTS auth_key_pair (
    id              VARCHAR(36)  PRIMARY KEY,
    key_id          VARCHAR(100) NOT NULL,
    algorithm       VARCHAR(20)  NOT NULL DEFAULT 'RSA',
    private_key_pem TEXT         NOT NULL,
    public_key_pem  TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);
