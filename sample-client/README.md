# Android Phone API — Sample Client

Reference Python client for the Android Phone API homelab server.

## Prerequisites

- Python 3.8+
- [requests](https://pypi.org/project/requests/) library

## Quick Start

```sh
cd sample-client
pip install -r requirements.txt

export PHONE_API_URL="http://127.0.0.1:8080"   # debug server
export PHONE_API_KEY="your-api-key-here"

python examples.py
```

## Configuration

| Variable | Description | Default |
|---|---|---|
| `PHONE_API_URL` | Base URL of the phone API server | `https://phone.local:8443` |
| `PHONE_API_KEY` | API key for authenticated endpoints (required) | — |

## Library Usage

```python
from phone_api import PhoneApiClient, PhoneApiConfig

# Configure from environment variables
config = PhoneApiConfig.from_env()

# Or configure explicitly
config = PhoneApiConfig(
    base_url="http://127.0.0.1:8080",
    api_key="your-api-key",
    verify_ssl=False,  # disable for debug plaintext server
)

client = PhoneApiClient(config)
```

### Health Check (public)

```python
result = client.health()
# {"status": "ok", ...}
```

### Auth Check

```python
result = client.auth_check()
# {"authenticated": true, ...}
```

### Battery Info

```python
result = client.battery()
# {"level": 85, "charging": true, ...}
```

### Device Info

```python
result = client.device()
# {"manufacturer": "...", "model": "...", ...}
```

### Send Notification

```python
result = client.notify(
    title="Homelab Alert",
    body="Backup completed successfully",
    channel="homelab",    # optional, default "homelab"
    priority="default",   # optional, default "default"
)
```

## curl Examples

All authenticated endpoints require the `Authorization: Bearer <api-key>` header.

### Health (public)

```sh
curl http://127.0.0.1:8080/api/v1/health
```

### Auth Check

```sh
curl -H "Authorization: Bearer $PHONE_API_KEY" \
     http://127.0.0.1:8080/api/v1/auth/check
```

### Battery Info

```sh
curl -H "Authorization: Bearer $PHONE_API_KEY" \
     http://127.0.0.1:8080/api/v1/battery
```

### Device Info

```sh
curl -H "Authorization: Bearer $PHONE_API_KEY" \
     http://127.0.0.1:8080/api/v1/device
```

### Send Notification

```sh
curl -X POST \
     -H "Authorization: Bearer $PHONE_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"title":"Homelab Alert","body":"Test notification","channel":"homelab","priority":"default"}' \
     http://127.0.0.1:8080/api/v1/notify
```

### With Request ID

Any endpoint accepts an optional `X-Request-ID` header for tracing:

```sh
curl -H "Authorization: Bearer $PHONE_API_KEY" \
     -H "X-Request-ID: my-trace-id-123" \
     http://127.0.0.1:8080/api/v1/battery
```

## Security Notes

- **Never commit API keys** to version control. Always use environment variables or a secrets manager.
- **Use HTTPS in production.** The debug plaintext server (`127.0.0.1:8080`) is for local development only and must not be exposed on the network.
- **Production default** is `https://phone.local:8443`. Set `verify_ssl=True` (the default) and ensure your CA certificate is trusted or provide a custom CA bundle.
- **API keys are phone-side managed.** Generate, reveal, reset, enable, and disable keys from the Android app — not from the client.
- **Rotate keys** if you suspect compromise. Use the phone app to reset and re-export the key.
