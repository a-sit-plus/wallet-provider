-- The keys of all issued key attestations, by JWK thumbprint, so that no key is attested twice (TS3, 2.2.2.1).
-- Written for PostgreSQL; H2 runs it in PostgreSQL mode.

CREATE TABLE "wallet_provider".attested_keys (
    thumbprint  VARCHAR(128) PRIMARY KEY,
    attested_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
