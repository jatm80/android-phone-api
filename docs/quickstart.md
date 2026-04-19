# Quickstart

## Prerequisites
- Android device or emulator running Android 8.0+ (API 26+)
- Docker installed for building
- Python 3.8+ for the sample client (optional)

## Build the APK

```sh
docker compose build android
docker compose run --rm build
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Install and Run

1. Install the APK on your device
2. Open "Android Phone API"
3. Toggle the server switch to start the API server
4. The server runs on port 8080 (debug) or 8443 (release)

## Configure API Key

1. In the app, tap "Reveal" to see your API key
2. Toggle "Enabled" to allow API access
3. Use "Reset" to generate a new key (invalidates the old one)

## Test with curl

```sh
# Health check (no auth required)
curl http://<phone-ip>:8080/api/v1/health

# Authenticated request
curl -H "Authorization: Bearer <your-api-key>" \
     http://<phone-ip>:8080/api/v1/battery

# Send a notification
curl -X POST \
     -H "Authorization: Bearer <your-api-key>" \
     -H "Content-Type: application/json" \
     -d '{"title":"Hello","body":"From homelab"}' \
     http://<phone-ip>:8080/api/v1/notify
```

## Use the Python Client

```sh
cd sample-client
pip install -r requirements.txt
export PHONE_API_URL=http://<phone-ip>:8080
export PHONE_API_KEY=<your-api-key>
python examples.py
```

## Run Tests

```sh
docker compose run --rm test
docker compose run --rm lint
docker compose run --rm coverage
```

## Security Notes

- Debug builds use plaintext HTTP on loopback only
- Production builds require HTTPS (not yet configured)
- API keys are generated on-device and stored encrypted
- All authenticated actions are logged in the audit viewer
- Never share your API key in logs, commits, or public channels
