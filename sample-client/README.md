# Android Phone API — Sample Client

Reference Python client for the Android Phone API homelab server.

## Prerequisites

- Python 3.8+
- [requests](https://pypi.org/project/requests/) library

## Quick Start

```sh
cd sample-client
pip install -r requirements.txt

export PHONE_API_URL="http://<phone-ip>:8080"
export PHONE_API_KEY="your-api-key-here"

python examples.py
```

## Configuration

| Variable | Description | Default |
|---|---|---|
| `PHONE_API_URL` | Base URL of the phone API server | `http://phone.local:8080` |
| `PHONE_API_KEY` | API key for authenticated endpoints (required) | — |

## Library Usage

```python
from phone_api import PhoneApiClient, PhoneApiConfig

# Configure from environment variables
config = PhoneApiConfig.from_env()

# Or configure explicitly
config = PhoneApiConfig(
    base_url="http://<phone-ip>:8080",
    api_key="your-api-key",
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
curl http://<phone-ip>:8080/api/v1/health
```

### Auth Check

```sh
curl -H "Authorization: Bearer $PHONE_API_KEY" \
     http://<phone-ip>:8080/api/v1/auth/check
```

### Battery Info

```sh
curl -H "Authorization: Bearer $PHONE_API_KEY" \
     http://<phone-ip>:8080/api/v1/battery
```

### Device Info

```sh
curl -H "Authorization: Bearer $PHONE_API_KEY" \
     http://<phone-ip>:8080/api/v1/device
```

### Send Notification

```sh
curl -X POST \
     -H "Authorization: Bearer $PHONE_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"title":"Homelab Alert","body":"Test notification","channel":"homelab","priority":"default"}' \
     http://<phone-ip>:8080/api/v1/notify
```

### With Request ID

Any endpoint accepts an optional `X-Request-ID` header for tracing:

```sh
curl -H "Authorization: Bearer $PHONE_API_KEY" \
     -H "X-Request-ID: my-trace-id-123" \
     http://<phone-ip>:8080/api/v1/battery
```

## Security Notes

- **Never commit API keys** to version control. Always use environment variables or a secrets manager.
- **Use only trusted networks.** HTTP is intentionally enabled for local-network use, so API keys can be observed by anyone who can sniff that network.
- **Default URL** is `http://phone.local:8080`. Replace `phone.local` with the phone IP address if local DNS/mDNS is not configured.
- **API keys are phone-side managed.** Generate, reveal, reset, enable, and disable keys from the Android app — not from the client.
- **Rotate keys** if you suspect compromise. Use the phone app to reset and re-export the key.
