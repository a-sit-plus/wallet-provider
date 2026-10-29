# Deployment Assets

This directory contains the configuration artifacts for running the wallet provider as a container with a Spring Cloud
Config Server, without Docker Compose. For a quick local start see `compose.yaml` and the main README instead.

## 1. Create the `/vault` contents

Prepare a target directory that will later be mounted into the container as `/vault`:

```bash
./deployment/prepare-vault.sh /absolute/path/to/vault --storepass <keystore password>
```

Options: `--alias` (default `wallet-provider`), `--dname` (default `CN=Wallet Provider`) and `--validity` in days
(default `3650`). The script needs `keytool` from a JDK.

This script:

- creates `keystore.p12` with an EC P-256 key and a self-signed certificate, unless it is already present (an existing
  keystore is never overwritten)
- prints the `keystore` settings to put into the config server file

Files placed in the vault:

- `keystore.p12`

The key signs the Wallet Instance Attestations, the Key Attestations and the status lists; its certificate is included
in their JWS header. To use a certificate issued by a CA instead, put a PKCS#12 keystore with that key and certificate
chain into the vault as `keystore.p12` and configure its alias and password.

The provider loads the key at startup; a missing keystore or a wrong alias or password fails startup. The provider
does not write to `/vault`, so it can be mounted read-only.

## 2. Build the image

Run this command from the repository root:

```bash
docker build -t wallet-provider:local .
```

The image contains no key material (`vault/`, `*.p12`, `.env` and `application-secrets.yaml` are excluded by
`.dockerignore`); mount `/vault` at runtime as shown below.

## 3. Config server file

Copy `deployment/config-server/wallet-provider.yml` into the Git repository used by your external Spring Cloud Config
Server and replace its placeholders.

The file is consumed through Spring Cloud Config Client (`application.yaml` already imports `optional:configserver:`)
with:

- `SPRING_APPLICATION_NAME=wallet-provider` (set in the image)
- `SPRING_CLOUD_CONFIG_ENABLED=true` (set in the image)
- `SPRING_CLOUD_CONFIG_URI=http://<config-server>:8888`
- optional `SPRING_CLOUD_CONFIG_LABEL=main` and `SPRING_PROFILES_ACTIVE`

The import is `optional:`: if the config server cannot be reached, the provider starts with the built-in defaults of
its `application.yaml`, including its in-memory database. Set `SPRING_CLOUD_CONFIG_FAIL_FAST=true` to stop instead, and
check the startup log for the loaded config source.

The port is set by the image (`PORT`, default `8080`), not by the config file.

## 4. Database

The provider persists the status lists through a JDBC datasource configured with `spring.datasource.*` (`url`,
`username`, `password`) in `wallet-provider.yml`, or through Spring's environment variables:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/wallet_provider
SPRING_DATASOURCE_USERNAME=wallet_provider
SPRING_DATASOURCE_PASSWORD=<database password>
```

The PostgreSQL database must already exist. Flyway creates the schema `wallet_provider` and its tables at startup, so
the database user needs permission to create schemas and tables. The provider keeps its own migration history
(`flyway_schema_history_wallet_provider`), so it can share one database with other services, e.g. those of the wallet
registry. It takes its schema from `spring.flyway.default-schema`; set only that property to use another schema.
Connection or migration failures fail startup rather than falling back to another database.

What the provider stores: for the Wallet Instance Attestations and for the Key Attestations, one status list per time
period with the counter of the status list indices handed out so far.

Without `spring.datasource.*` the provider uses an in-memory H2 database for demos and tests. **Configure PostgreSQL to
retain the status lists across container restarts**: when they are lost, revoked attestations count as valid again and
new attestations reuse the indices of earlier ones.

### Upgrading from the Ktor version

On the first start against the database of the Ktor version, migration V2 copies its status lists (tables
`key_storage_status` and `client_status_lists` of the default schema) into the schema `wallet_provider`; the old tables
are left unchanged. Move `database.url` to `spring.datasource.url` and remove `database.driver` before: the provider does
not start while they are still configured. Check the startup log for the migrations `1 - create status lists` and
`2 - Import legacy status lists`.

## 5. Public endpoints

Wallets call the attestation endpoints, and wallets and relying parties fetch the status lists at the URIs carried in
the attestations, so these endpoints must be publicly reachable over stable HTTPS:

- `GET /api/v1/challenge`, `POST /api/v1/instanceAttestation`, `GET /api/v1/nonce`, `POST /api/v1/keyAttestation`
- `GET /api/v1/clientStatus/{period}` and `GET /api/v1/keyStorageStatus/{period}`

Set `provider.publicContext` to the public URL under which the reverse proxy exposes the provider: the status list
URIs in the issued attestations are built from it and cannot be changed afterwards. The reverse proxy should pass
`X-Forwarded-*` headers (`server.forward-headers-strategy: framework`).

The admin pages (`/admin/...`) change the revocation status of attestations. They require the admin login, but need
not be public; restrict them at the reverse proxy where possible.

## 6. Example container run

```bash
docker run --rm \
  --network your-shared-network \
  -p 8080:8080 \
  -e SPRING_CLOUD_CONFIG_URI=http://spring-cloud-config-server.internal:8888 \
  -e SPRING_CLOUD_CONFIG_LABEL=main \
  -v /absolute/path/to/vault:/vault:ro \
  wallet-provider:local
```

The provider connects to the database configured with `spring.datasource.*` in `wallet-provider.yml`; no data volume
is needed (the `/vault` mount stays read-only, used only for the signing key).

Attach the config server, the database and the provider to the same Docker network, so that they resolve each other by
container name.

Replace all placeholder passwords, URLs, and secrets in the config server file before using it outside a local demo.
