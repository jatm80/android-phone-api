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
   - Build the release APK
   - Generate SHA-256 checksums
   - Create a GitHub Release with artifacts

## Dry Run

Use the manual workflow dispatch with "Dry run" checked to build without publishing:

```
gh workflow run release.yml -f dry_run=true
```

## Signing

### Current State

The release workflow builds unsigned APKs by default. For signed releases:

1. Generate a keystore (do NOT commit it):
   ```sh
   keytool -genkey -v -keystore release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Add GitHub repository secrets:
   - `RELEASE_KEYSTORE_BASE64` — base64-encoded keystore file
   - `RELEASE_KEYSTORE_PASSWORD` — keystore password
   - `RELEASE_KEY_ALIAS` — key alias
   - `RELEASE_KEY_PASSWORD` — key password

3. The workflow will decode the keystore and pass signing config to Gradle when these secrets are available.

### Security Notes

- Never store signing keys in the repository
- Use GitHub encrypted secrets for all signing material
- Keep keystore backups in a secure location outside of version control
- Rotate signing keys if compromised
