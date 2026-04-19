# Release Process

## Semantic Versioning

This project uses semantic versioning (`vMAJOR.MINOR.PATCH`).

## Creating a Release

1. Update `versionName` in `app/build.gradle.kts` if needed
2. Tag the commit:
   ```sh
   git tag v0.1.0
   git push origin v0.1.0
   ```
3. GitHub Actions will automatically:
   - Run tests
   - Build a signed release APK
   - Verify the APK signature
   - Generate SHA-256 checksums
   - Create a GitHub Release with artifacts

## Dry Run

Use the manual workflow dispatch with "Dry run" checked to build without publishing:

```
gh workflow run release.yml -f dry_run=true
```

## Signing

Release APKs must be signed before they can be installed on Android devices. If a downloaded
`android-phone-api.apk` is recognized by `file` as an APK but cannot be installed on a Pixel,
check whether it came from an unsigned release build.

The GitHub release workflow requires signing secrets for published releases. Dry runs can build
without signing secrets, but those unsigned dry-run APKs are build artifacts only and are not
installable on a phone.

1. Generate a keystore (do NOT commit it):
   ```sh
   keytool -genkey -v -keystore release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Base64-encode the keystore:
   ```sh
   base64 -w 0 release.keystore
   ```

   On macOS, use:
   ```sh
   base64 -i release.keystore
   ```

3. Add GitHub repository secrets:
   - `RELEASE_KEYSTORE_BASE64` — base64-encoded keystore file
   - `RELEASE_KEYSTORE_PASSWORD` — keystore password
   - `RELEASE_KEY_ALIAS` — key alias
   - `RELEASE_KEY_PASSWORD` — key password

4. Push a `vMAJOR.MINOR.PATCH` tag. The workflow decodes the keystore, signs the APK, verifies
   the signature with `apksigner`, then publishes `android-phone-api.apk`.

## Local Signed Build

For a local signed release build, place the keystore outside version control and pass signing
values into the Docker Gradle invocation:

```sh
ANDROID_PHONE_API_RELEASE_STORE_PASSWORD='change-me' \
ANDROID_PHONE_API_RELEASE_KEY_ALIAS='release' \
ANDROID_PHONE_API_RELEASE_KEY_PASSWORD='change-me' \
docker compose run --rm \
  -e ANDROID_PHONE_API_RELEASE_STORE_FILE=/workspace/release.keystore \
  -e ANDROID_PHONE_API_RELEASE_STORE_PASSWORD \
  -e ANDROID_PHONE_API_RELEASE_KEY_ALIAS \
  -e ANDROID_PHONE_API_RELEASE_KEY_PASSWORD \
  android assembleRelease
```

Verify the resulting APK before installing it:

```sh
docker compose run --rm --entrypoint /opt/android-sdk/build-tools/36.0.0/apksigner \
  android verify --verbose app/build/outputs/apk/release/android-phone-api-release.apk
```

### Security Notes

- Never store signing keys in the repository
- Use GitHub encrypted secrets for all signing material
- Keep keystore backups in a secure location outside of version control
- Rotate signing keys if compromised
