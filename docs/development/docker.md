# Docker Development

Docker is the default development environment for this repository. Contributors should not need local Kotlin, Gradle, Android SDK, or Android build tooling installed.

## Prerequisites
- Docker with the `docker compose` plugin.

## Image Contents
The development image is pinned to:
- Eclipse Temurin JDK 17.
- Android command-line tools `14742923`.
- Android platform `android-36`.
- Android build tools `36.0.0`.
- Gradle `9.3.1`, verified with the published SHA-256 checksum.

The image does not contain signing keys or secrets.

## Commands
Build the development image:

```sh
docker compose build android
```

Run a debug build:

```sh
docker compose run --rm build
```

Run Android lint:

```sh
docker compose run --rm lint
```

Run unit tests:

```sh
docker compose run --rm test
```

Run unit tests with the Android Gradle Plugin coverage report:

```sh
docker compose run --rm coverage
```

Open a shell inside the development image:

```sh
docker compose run --rm shell
```

Gradle dependencies are cached in the named Docker volume `gradle-cache`. Build outputs stay under normal Gradle `build/` directories, which are ignored by git.

Gradle's project cache is also redirected into the container-owned Gradle cache volume with `org.gradle.projectcachedir=/home/android/.gradle/project-cache`. This avoids permission failures when CI bind-mounts the checked-out repository at `/workspace`.

## CI

GitHub Actions uses the same Dockerfile and `docker compose` services as local development. Keep local and CI commands aligned when adding build, lint, test, or coverage steps.

### CI enhancements

The workflow at `.github/workflows/android.yml` includes:

- **Docker Buildx** with layer caching via `actions/cache` to speed up image rebuilds.
- **Coverage report artifact** — the JaCoCo HTML/XML coverage report is uploaded as a build artifact (`coverage-report`) and retained for 14 days.
- **Debug APK artifact** — the assembled debug APK is uploaded as a build artifact (`debug-apk`) and retained for 14 days.
- **JaCoCo report configuration** — `app/build.gradle.kts` configures JaCoCo to produce both XML and HTML reports. A hard 90% coverage enforcement gate is deferred until UI-untestable code is separated or covered by instrumentation tests.

Since docker-compose bind-mounts `.:/workspace`, build outputs (reports, APKs) are available on the host after each step without extra extraction.
