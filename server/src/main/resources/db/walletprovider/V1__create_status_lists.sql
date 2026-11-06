-- Status lists of the issued attestations, one row per time period with the counter of handed out indices.
-- Written for PostgreSQL; H2 runs it in PostgreSQL mode.

CREATE TABLE "wallet_provider".key_storage_status_lists (
    time_period     INT         PRIMARY KEY,
    data            BYTEA       NOT NULL,
    counter         INT         NOT NULL,
    status_bit_size VARCHAR(50) NOT NULL
);

CREATE TABLE "wallet_provider".client_status_lists (
    time_period     INT         PRIMARY KEY,
    data            BYTEA       NOT NULL,
    counter         INT         NOT NULL,
    status_bit_size VARCHAR(50) NOT NULL
);
