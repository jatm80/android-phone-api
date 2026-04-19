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

## Sample Client

A Python reference client is available in [`sample-client/`](sample-client/README.md). It provides a `PhoneApiClient` class and runnable examples for all current endpoints. See [sample-client/README.md](sample-client/README.md) for setup, usage, and curl examples.

## Development Status

### Application
- `:app` is a Kotlin/Compose Android application
- `MainActivity` shows server lifecycle, API key controls, and audit log viewer
- `ApiServerForegroundService` provides the foreground-service start/stop path

### API Endpoints
All endpoints under `/api/v1`. Authenticated endpoints require `Authorization: Bearer <api-key>`.

| Endpoint | Method | Auth | Description |
| --- | --- | --- | --- |
| `/health` | GET | No | Server health and status |
| `/auth/check` | GET | Yes | Verify API key authentication |
| `/battery` | GET | Yes | Battery level, status, temperature |
| `/device` | GET | Yes | Device manufacturer, model, Android version |
| `/notify` | POST | Yes | Send user-visible notification |
| `/clipboard` | GET | Yes | Read clipboard content |
| `/clipboard` | POST | Yes | Write text to clipboard |
| `/location` | GET | Yes | Last known GPS/network location |
| `/tts/speak` | POST | Yes | Text-to-speech playback |
| `/tts/engines` | GET | Yes | List available TTS engines |
| `/camera/list` | GET | Yes | List available cameras |
| `/camera/capture` | POST | Yes | Capture photo (requires foreground UI) |
| `/audio/record` | POST | Yes | Record audio (requires permission) |
| `/sms/send` | POST | Yes | Send SMS (requires permission and approval) |

### Security
- API key authentication with salted SHA-256 hash verification
- Raw API key encrypted with Android Keystore-backed AES/GCM
- All privileged actions audited (never logging secrets)
- HTTPS required in production; plaintext only in debug on loopback
- Phone-side controls: enable/disable, reveal, reset

### Known Limitations
- Camera capture and audio recording return stub responses pending foreground UI consent flow
- SMS sending returns stub response pending SEND_SMS permission and on-device approval flow
- Clipboard read may be restricted on Android 10+ when app is in background
- Location requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permission grant
- TTS initialization is async and may briefly return "not ready" on first request
- HTTPS/TLS transport not yet configured (production blocks until TLS material is available)
- Coverage is below 90% target due to Android UI and service code requiring instrumentation tests
- No rate limiting implemented yet for authenticated endpoints

### Tooling
- Android Gradle Plugin 9.1.0 / Gradle 9.3.1
- Compose BOM 2026.02.01 / Kotlin 2.3.20
- Java 17 toolchain
- Docker-based development and CI

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

This project uses open source dependencies under their respective licenses. All current dependencies are Apache-2.0 compatible.
