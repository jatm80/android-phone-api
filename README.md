# android-phone-api

Android app for exposing selected phone functions to trusted homelab systems through a secure local-network API.

## Design

- [Initial design pack](docs/design/initial-design-pack.md)

## Development Status

The Android scaffold is intentionally narrow:
- `:app` is a Kotlin/Compose Android application.
- `MainActivity` shows local API server lifecycle state.
- `ApiServerForegroundService` provides the foreground-service start/stop path.
- No API sockets, routes, pairing flow, or capabilities are exposed yet.

Tooling baseline:
- Android Gradle Plugin 9.1.0
- Gradle 9.3.1 expected by the selected Android Gradle Plugin
- Kotlin 2.3.20
- Compose BOM 2026.02.01
- Java 17 toolchain for Android builds

Android assumptions:
- The active server lifecycle uses a foreground service with `dataSync` type until the concrete server transport is implemented and validated.
- Notification permission handling is not implemented yet; the current scaffold only declares the permission needed by modern Android notification behavior.
- The Docker build, lint, test, and coverage workflow is added in the next task.
