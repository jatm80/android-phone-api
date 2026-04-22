#!/usr/bin/env python3
"""Example usage of the Android Phone API client."""

import json
import sys

from phone_api import PhoneApiClient, PhoneApiConfig


def main():
    try:
        config = PhoneApiConfig.from_env()
    except ValueError as e:
        print(f"Configuration error: {e}", file=sys.stderr)
        print("Set PHONE_API_URL and PHONE_API_KEY environment variables.", file=sys.stderr)
        sys.exit(1)

    client = PhoneApiClient(config)

    print("=== Health Check ===")
    print(json.dumps(client.health(), indent=2))

    print("\n=== Auth Check ===")
    print(json.dumps(client.auth_check(), indent=2))

    print("\n=== Battery Info ===")
    print(json.dumps(client.battery(), indent=2))

    print("\n=== Device Info ===")
    print(json.dumps(client.device(), indent=2))

    print("\n=== Send Notification ===")
    result = client.notify(
        title="Homelab Alert",
        body="Test notification from sample client",
    )
    print(json.dumps(result, indent=2))

    print("\n=== Text to Speech ===")
    print(json.dumps(client.speak("hello"), indent=2))

    print("\n=== Take Photo ===")
    print(json.dumps(client.capture_photo(), indent=2))


if __name__ == "__main__":
    main()
