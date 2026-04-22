#!/usr/bin/env python3
"""Example usage of the Android Phone API client."""

import json
import sys

import requests

from phone_api import PhoneApiClient, PhoneApiConfig


def reverse_geocode(latitude, longitude, timeout=10.0):
    response = requests.get(
        "https://nominatim.openstreetmap.org/reverse",
        params={
            "format": "jsonv2",
            "lat": latitude,
            "lon": longitude,
        },
        headers={
            "User-Agent": "android-phone-api-sample-client/0.1",
        },
        timeout=timeout,
    )
    response.raise_for_status()
    return response.json()


def print_http_error(prefix, error):
    response = error.response
    if response is None:
        print(f"{prefix}: {error}", file=sys.stderr)
        return

    try:
        body = response.json()
    except ValueError:
        body = response.text

    print(f"{prefix}: HTTP {response.status_code}", file=sys.stderr)
    print(json.dumps(body, indent=2) if isinstance(body, dict) else body, file=sys.stderr)


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

    print("\n=== Location ===")
    try:
        location = client.location()
        print(json.dumps(location, indent=2))

        print("\n=== Location Address ===")
        address = reverse_geocode(
            latitude=location["latitude"],
            longitude=location["longitude"],
            timeout=config.timeout,
        )
        print(address.get("display_name", "No address found"))
    except requests.HTTPError as e:
        print_http_error("Location lookup failed", e)
        print("Skipping reverse geocoding because no location was returned.", file=sys.stderr)
    except requests.RequestException as e:
        print(f"Reverse geocoding failed: {e}", file=sys.stderr)

    print("\n=== Send Notification ===")
    result = client.notify(
        title="Homelab Alert",
        body="Test notification from sample client",
    )
    print(json.dumps(result, indent=2))

    print("\n=== Text to Speech ===")
    print(json.dumps(client.speak("hello, can you say something?"), indent=2))

    print("\n=== Take Photo ===")
    print(json.dumps(client.capture_photo(), indent=2))


if __name__ == "__main__":
    main()
