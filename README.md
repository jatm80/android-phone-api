# android-phone-api

Android app for exposing selected phone functions to trusted homelab systems through a secure local-network API.

## Design

- [Initial design pack](docs/design/initial-design-pack.md)

## Docker Workflow

Docker is the default development environment. See [Docker Development](docs/development/docker.md) for the full workflow.

Common commands:

```sh
docker compose build android
docker compose run --rm build
docker compose run --rm lint
docker compose run --rm test
docker compose run --rm coverage
```

## Development Status

The Android scaffold is intentionally narrow:
- `:app` is a Kotlin/Compose Android application.
- `MainActivity` shows local API server lifecycle state.
- `ApiServerForegroundService` provides the foreground-service start/stop path.
- The embedded Ktor skeleton exposes `GET /api/v1/health` with request IDs and structured errors.
- API key authentication and phone-side API controls are available as early project scaffolding.
- Device capabilities are not exposed yet.

API server behavior:
- Release/default server config requires HTTPS and fails closed until TLS trust material is implemented.
- Debug builds may use plaintext on `127.0.0.1:8080` only for local development.
- Plaintext LAN exposure is not a production path.
- API key authentication is the current auth path. Clients send `Authorization: Bearer <api-key>` to protected endpoints.
- API key controls are phone-side only: enable/disable, reveal, and reset.
- The API key verifier is salted and hashed; the revealable raw key is encrypted with an Android Keystore-backed AES/GCM key before persistence.

Tooling baseline:
- Android Gradle Plugin 9.1.0
- Gradle 9.3.1 expected by the selected Android Gradle Plugin
- AGP built-in Kotlin support with Compose compiler plugin 2.3.20
- Compose BOM 2026.02.01
- Java 17 toolchain for Android builds

Android assumptions:
- The active server lifecycle uses a foreground service with `dataSync` type until the concrete server transport is implemented and validated.
- Notification permission handling is not implemented yet; the current scaffold only declares the permission needed by modern Android notification behavior.
- mTLS and per-client identity are not part of the current simplified auth model; the shared API key is the current authentication mechanism.
- Unit coverage is below the repository target while the app consists mostly of Android UI and foreground-service scaffolding; instrumentation coverage should be added as lifecycle and UI behavior hardens.
