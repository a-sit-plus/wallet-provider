<div align="center">


<picture>
  <source media="(prefers-color-scheme: dark)" srcset="provider-light.png">
  <source media="(prefers-color-scheme: light)" srcset="provider-dark.png">
  <img alt="Wallet Provider based on Spring Boot and Warden Supreme" src="provider-dark.png">
</picture>
<br><br>

<img src="https://raw.githubusercontent.com/a-sit-plus/a-sit-plus.github.io/709e802b3e00cb57916cbb254ca5e1a5756ad2a8/A-SIT%20Plus_%20official_opt.svg" alt="A-SIT Plus Official" />
<img src="https://img.shields.io/badge/license-Apache%20License%202.0-brightgreen.svg?style=flat" alt="GitHub license" />
<img src="https://img.shields.io/badge/kotlin-2.3.21-blue.svg?logo=kotlin" alt="Kotlin" />
</div>

A Kotlin-based Spring Boot service for EUDI Wallet ecosystem attestation flows, providing challenges, token issuance, and status list management.

| ⚠️ Warning                                             |
|:-------------------------------------------------------|
| This service is intended as a Technology Demonstrator! |

## **Demo Setup**

### Create keystore file
```bash
keytool -genkeypair -alias CHANGEME -keyalg EC -groupname secp256r1 -storetype PKCS12 -keystore keystore.p12 -validity 3650
```

### Customize application.yaml
The service is a Spring Boot application, configured through `server/src/main/resources/application.yaml`.

| **Module**                          | **Option**             | **Description**                                                                                |
|-------------------------------------|------------------------|------------------------------------------------------------------------------------------------|
| **server**                          |                        |                                                                                                |
|                                     | port                   | Port the server listens on                                                                     |
|                                     | address                | IP address the server listens on                                                               |
| **spring.datasource**               |                        |                                                                                                |
|                                     | url                    | JDBC URL of the database (H2 in PostgreSQL mode, or PostgreSQL), default in-memory H2         |
|                                     | username / password    | Credentials of the database                                                                    |
| **database**                        |                        |                                                                                                |
|                                     | exportInterval         | Interval of the export scheduler (ISO 8601)                                                    |
| **keystore**                        |                        |                                                                                                |
|                                     | file                   | Path to p12 keystore file                                                                      |
|                                     | alias                  | Alias of key to be used                                                                        |
|                                     | secret                 | Secret to unlock keystore file                                                                 |
| **admin**                           |                        |                                                                                                |
|                                     | username               | User name of the admin login (default `admin`)                                                 |
|                                     | password               | Password of the admin login, plain or as hash (e.g. `{bcrypt}$2a$...`); empty disables login   |
| **provider**                        |                        |                                                                                                |
|                                     | publicContext          | Public base url                                                                                |
|                                     | clientId               | Value used as the WIA subject/client identifier                                                |
|                                     | issuer                 | Provider/certification URL used in certification information fields                            |
|                                     | solutionId             | Value used as the WIA `wallet_name` claim                                                      |
|                                     | solutionOid            | Identifies additional information in the csr attributes                                        |
| **endpoints**                       |                        |                                                                                                |
|                                     | challenge              | Endpoint for the wallet solution to request an attestation challenge via `warden-supreme`      |
|                                     | instanceAttestation    | Endpoint where the wallet solution posts the `InstanceAttestationRequest`                      |
|                                     | keyAttestation         | Endpoint where the wallet solution posts the `KeyAttestationRequest`                           |
|                                     | clientStatus           | Endpoint to obtain the revocation status of instance attestations                              |
|                                     | keyStorageStatus       | Endpoint to obtain the revocation status of key attestations                                   |
|                                     | nonce                  | Endpoint for the wallet solution to obtain a nonce for the instance attestation proof jwt      |
| **attestation.keyAttestation**      |                        |                                                                                                |
|                                     | maintenance            | Duration how long the provider maintains the keyStorageStatus                                  |
|                                     | lifetime               | Technical validity of the key attestation jwt                                                  |
| **attestation.instanceAttestation** |                        |                                                                                                |
|                                     | maintenance            | Duration how long the provider maintains the clientStatus                                      |
|                                     | lifetime               | Technical validity of the instance attestation jwt                                             |
| **supreme**                         | SupremeConfiguration   | Configuration yaml according to https://a-sit-plus.github.io/warden-supreme/integration/config |

The database schema `wallet_provider` (`spring.flyway.default-schema`) is created and migrated by Flyway on startup,
with the scripts in `server/src/main/resources/db/walletprovider` and its own history table
`flyway_schema_history_wallet_provider`, so the provider can share a PostgreSQL database with other services, like the
services of the wallet registry.

#### Upgrading from the Ktor version
The Ktor version kept its status lists in the tables `key_storage_status` and `client_status_lists` of the default
schema. On the first start, migration V2 copies them into the provider's schema, so revocations and the status list
indices are kept; the old tables are left unchanged. Before upgrading, move the database settings:

| Ktor version      | Now                                                                          |
|-------------------|------------------------------------------------------------------------------|
| `database.url`    | `spring.datasource.url`; for H2 replace `MODE=MYSQL` with `MODE=PostgreSQL`  |
| `database.driver` | not needed, the driver is inferred from the URL                              |
| user and password | `spring.datasource.username` and `spring.datasource.password` (or in the URL); none for the H2 file, as before |

The provider does not start while `database.url` or `database.driver` is still configured.

The `supreme` section is loaded with Warden Supreme's Spring Boot binding (`config-spring`), like any other Spring
configuration: single values of a source with higher precedence (e.g. the config server) override those of
`application.yaml`, a list is replaced as a whole. Not supported by this binding: overriding a value with an explicit
`null`, and `supreme` values from environment variables. An empty list can only be written as Warden Supreme's sentinel
value (e.g. `revocation: DISABLED`), as Spring reads `[]` like `null`; quote strings that look like numbers (e.g. iOS
build numbers like `"21E236"`).

For local overrides, e.g. the admin password, create `application-secrets.yaml` (gitignored) in the working directory:
```yaml
admin:
  password: "set-a-local-password"
```

### Run Terminal Command
```bash
./gradlew :server:bootRun
```
The working directory is `server/`, so the keystore file and `application-secrets.yaml` are resolved relative to it.
Without `spring.datasource.url` the status lists are kept in an in-memory H2 database, so they are lost on restart.

### Docker
The image loads its configuration from a Spring Cloud Config server, i.e. the YAML file (as defined above) named
`${SPRING_APPLICATION_NAME}-${SPRING_PROFILES_ACTIVE}.yaml` on branch `${SPRING_CLOUD_CONFIG_LABEL}`:

| **Variable**                 | **Description**                                                       |
|------------------------------|-----------------------------------------------------------------------|
| `SPRING_CLOUD_CONFIG_URI`    | URL of the config server, e.g. `http://config-server:8888`            |
| `SPRING_APPLICATION_NAME`    | Name of the configuration (default `wallet-provider`)                 |
| `SPRING_PROFILES_ACTIVE`     | Profile of the configuration (default `default`)                      |
| `SPRING_CLOUD_CONFIG_LABEL`  | Branch of the configuration (default `main`)                          |
| `PORT`                       | Port the server listens on (default `8080`)                           |
| `JAVA_OPTS`                  | Additional JVM options                                                |

The config server is optional: if it cannot be reached, the service starts with the defaults of `application.yaml`.
Set `SPRING_CLOUD_CONFIG_FAIL_FAST=true` to stop instead. Any setting can also be given as environment variable,
e.g. `ADMIN_PASSWORD` for `admin.password`.

### Docker Compose
`compose.yaml` builds and starts the provider on `http://localhost:8080`, without a config server: it uses the
defaults of `application.yaml` (including the `supreme` section), with the settings below from environment variables.
Like the default configuration, it keeps the status lists in an in-memory database, so revocations are lost when the
container restarts; set `SPRING_DATASOURCE_URL` (and username/password) in `environment` for a persistent database.

Create the signing key in `./vault` (mounted read-only as `/vault`) and the secrets in `.env` (both gitignored):
```bash
mkdir vault
keytool -genkeypair -alias provider -keyalg EC -groupname secp256r1 -storetype PKCS12 -keystore vault/keystore.p12 -validity 3650
cat > .env <<'END'
KEYSTORE_SECRET=<password of vault/keystore.p12>
ADMIN_PASSWORD=<password of the admin login>
# Optional: public URL of the provider (used in the status list references), default http://localhost:8080
PUBLIC_CONTEXT=https://wallet-provider.example
END
docker compose up --build
```
Set `KEYSTORE_ALIAS` in `.env` if the key has another alias than `provider`. Further settings can be added to the
`environment` of `compose.yaml`, e.g. `PROVIDER_SOLUTIONID` for `provider.solutionId`.

## **Endpoints**

### Attestation & Authentication Endpoints

| Endpoint                      | Method | Description                               |
|-------------------------------|--------|-------------------------------------------|
| `/api/v1/challenge`           | GET    | Challenge for instance attestation        |
| `/api/v1/nonce`               | GET    | Nonce for client authentication           |
| `/api/v1/instanceAttestation` | POST   | JSON-encoded `InstanceAttestationRequest` |
| `/api/v1/keyAttestation`      | POST   | JSON-encoded `KeyAttestationRequest`      |

`/api/v1/instanceAttestation` returns a Wallet Instance Attestation which is a jwt of type `oauth-client-attestation+jwt` 
extended with `wallet_name`, `wallet_version`, `wallet_solution_certification_information` and `client_status` fields according to the ts3-wallet-unit-attestation. 

**Example content:**
```json
{
  "sub" : "https://wallet.a-sit.at/app",
  "iat" : 1778512996,
  "exp" : 1778556196,
  "cnf" : {
    "jwk" : {
      "crv" : "P-256",
      "kty" : "EC",
      "x" : "...",
      "y" : "..."
    }
  },
  "wallet_name" : "Valera",
  "wallet_version" : "1.0",
  "wallet_solution_certification_information" : "https://wallet.a-sit.at",
  "client_status" : {
    "status" : {
      "status_list" : {
        "idx" : 0,
        "uri" : "https://wallet-provider.a-sit.plus/api/v1/clientStatus/1"
      }
    },
    "exp" : 1781191696
  }
}
```

`/api/v1/keyAttestation` returns a Key Attestation which is a jwt of type `keyattestation+jwt`
extended with `key_storage`, `user_authentication`, `certification` and `key_storage_status` fields according to the ts3-wallet-unit-attestation.
**Example content:**
```json
{
  "iat" : 1778513051,
  "exp" : 1778556251,
  "attested_keys" : [ {
    "crv" : "P-256",
    "kty" : "EC",
    "x" : "...",
    "y" : "..."
  } ],
  "key_storage" : [ "iso_18045_high" ],
  "user_authentication" : [ "iso_18045_high" ],
  "certification" : "https://wallet.a-sit.at",
  "key_storage_status" : {
    "status" : {
      "status_list" : {
        "idx" : 0,
        "uri" : "https://wallet-provider.a-sit.plus/api/v1/keyStorageStatus/1"
      }
    },
    "exp" : 1781191751
  }
}
```

### Revocations Status Endpoints

| Endpoint                            | Method | Description                                              |
|-------------------------------------|--------|----------------------------------------------------------|
| `/api/v1/keyStorageStatus/{period}` | GET    | Status list JWT. Use `uri` from `KeyAttestationJwt`      |
| `/api/v1/clientStatus/{period}`     | GET    | Status list JWT. Use `uri` from `InstanceAttestationJwt` |

### Web Interface

| Endpoint                     | Method   | Description                                                        |
|------------------------------|----------|--------------------------------------------------------------------|
| `/`                          | GET      | Overview of the provider and its endpoints                         |
| `/admin/login`               | GET/POST | Login for the admin pages                                          |
| `/admin/key-storage-status`  | GET/POST | View and change the revocation status of key attestations (admin)  |
| `/admin/client-status`       | GET/POST | View and change the revocation status of instance attestations (admin) |
| `/admin/logout`              | POST     | Logout                                                             |

## References
* https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/main/docs/technical-specifications/ts3-wallet-unit-attestation.md
* https://eudi.dev/2.7.3/architecture-and-reference-framework-main

## Contributing
External contributions are greatly appreciated!
Just be sure to observe the contribution guidelines (see [CONTRIBUTING.md](CONTRIBUTING.md)).

---
<p align="center">
The Apache License does not apply to the logos, (including the A-SIT logo) and the project/module name(s), as these are the sole property of
A-SIT/A-SIT Plus GmbH and may not be used in derivative works without explicit permission!
</p>
