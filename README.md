# Wallet Provider

<div align="center">
<img src="https://raw.githubusercontent.com/a-sit-plus/a-sit-plus.github.io/709e802b3e00cb57916cbb254ca5e1a5756ad2a8/A-SIT%20Plus_%20official_opt.svg" alt="A-SIT Plus Official" />
<img src="https://img.shields.io/badge/license-Apache%20License%202.0-brightgreen.svg?style=flat" alt="GitHub license" />
<img src="https://img.shields.io/badge/kotlin-2.3.21-blue.svg?logo=kotlin" alt="Kotlin" />
</div>

A Kotlin-based backend service for EUDI Wallet ecosystem attestation flows, providing challenges, token issuance, and status list management.

## **Demo Setup**

### Create keystore file
```bash
keytool -genkeypair -alias CHANGEME -keyalg EC -groupname secp256r1 -storetype PKCS12 -keystore keystore.p12 -validity 3650
```

### Customize application.yaml
| **Module**              | **Option**        | **Description**                                                                          |
|-------------------------|-------------------|------------------------------------------------------------------------------------------|
| **ktor**                |                   |                                                                                          |
|                         | port              | Port the server listens on                                                               |
|                         | host              | IP address the server listens on                                                         |
| **database**            |                   |                                                                                          |
|                         | url               | URL for the database connection                                                          |
|                         | driver            | JDBC driver class                                                                        |
|                         | exportInterval    | Interval of the export scheduler (ISO 8601)                                              |
| **keystore**            |                   |                                                                                          |
|                         | file              | Path to p12 keystore file                                                                |
|                         | alias             | Alias of key to be used                                                                  |
|                         | secrect           | Secrect to unlock keystore file                                                          |
| **provider**            |                   |                                                                                          |
|                         | publicContext     | Public base url                                                                          |
|                         | clientId          | Value used as the WIA subject/client identifier                                          |
|                         | issuer            | Provider/certification URL used in certification information fields                       |
|                         | providerName      | Provider display name                                                                    |
|                         | solutionId        | Value used as the WIA `wallet_name` claim                                                |
|                         | solutionOid       | Identifies additional information in the csr attributes                                  |
| **endpoints**           |                   |                                                                                          |
|                         | challenge         | Endpoint for the wallet solution to request a attestation challenge via `warden-supreme` |
|                         | instance          | Endpoint where the wallet solution posts the `InstanceAttestationRequest`                |
|                         | unit              | Endpoint where the wallet solution posts the `UnitAttestationRequest`                    |
|                         | status            | Endpoint to obtain the revocation status of unit attestations                            |
|                         | nonce             | Endpoint for the wallet solution to obtain a nonce for the instance attestaion proof jwt |
|                         | update            | Testing endpoint to update the revocation status of the unit attestations                |
|                         | root              | Testing endpoint to display the revocation status of the unit attestations               |
| **attestation.android** |                   |                                                                                          |
|                         | packageName       | Package name used by `warden-supreme` during the wallet solution verification            |
|                         | signerFingerprint | Signer fingerprint used by `warden-supreme` during the wallet solution verification      |
| **attestation.ios**     |                   |                                                                                          |
|                         | teamIdentifier    | Team identifier used by `warden-supreme` during the wallet solution verification         |
|                         | bundleIdentifier  | Bundle identifier used by `warden-supreme` during the wallet solution verification       |


### Run Terminal Command
```bash
./gradlew run
```

### Docker
In `docker-entrypoint.sh` the runtime logic of the Docker container is defined.
The default behavior fetches a YAML config file (as defined above) from a composed url:
```bash
CONFIG_URL="${CONFIG_SERVER_URL}/${CONFIG_LABEL}/${CONFIG_APPLICATION}-${CONFIG_PROFILE}.yaml"
```
So adjust the `CONFIG_URL` and or the env vars to your needs.

## **Endpoints**

### Attestation & Authentication Endpoints

| Endpoint            | Method | Description                               |
|---------------------|--------|-------------------------------------------|
| `/api/v1/challenge` | GET    | Challenge for instance attestation        |
| `/api/v1/nonce`     | GET    | Nonce for client authentication           |
| `/api/v1/instance`  | POST   | JSON-encoded `InstanceAttestationRequest` |
| `/api/v1/unit`      | POST   | JSON-encoded `UnitAttestationRequest`     |

`/api/v1/instance` returns a Wallet Instance Attestation using VC-K's current client attestation builder. The JWT
contains `wallet_name`, `wallet_version`, `wallet_solution_certification_information`, and `client_status`; it does
not emit the removed `iss`, `eudi_wallet_info`, or top-level `status` claims.

`/api/v1/unit` returns a Key Attestation. The request body uses TS3/OID4VCI key storage fields:

```json
{
  "token": "<wallet-instance-attestation-jwt>",
  "proof": "<client-attestation-pop-jwt>",
  "keys": [
    {
      "kty": "EC",
      "...": "..."
    }
  ],
  "key_storage": ["iso_18045_high"],
  "user_authentication": ["iso_18045_high"]
}
```

The Key Attestation contains `attested_keys`, `key_storage`, `user_authentication`, `certification`, and
`key_storage_status`. It does not emit the removed `iss`, `eudi_wallet_info`, `storage_type`, or top-level `status`
claims.

### Revocations Status Endpoints

| Endpoint                  | Method | Description                                         |
|---------------------------|--------|-----------------------------------------------------|
| `/api/v1/status/{period}` | GET    | Status list JWT. Use `uri` from `KeyAttestationJwt` |

### Web Interface

| Endpoint  | Method | Description                                        |
|-----------|--------|----------------------------------------------------|
| `/`       | GET    | Web interface for managing revocation data         |
| `/update` | POST   | Form handler for updating revocation status values |

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
