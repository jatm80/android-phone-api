"""Reference homelab client for Android Phone API."""

import os
import uuid
from dataclasses import dataclass
from typing import Optional

import requests


@dataclass
class PhoneApiConfig:
    base_url: str
    api_key: str
    timeout: float = 10.0
    verify_ssl: bool = True

    @classmethod
    def from_env(cls) -> "PhoneApiConfig":
        base_url = os.environ.get("PHONE_API_URL", "http://phone.local:8080")
        api_key = os.environ.get("PHONE_API_KEY", "")
        if not api_key:
            raise ValueError("PHONE_API_KEY environment variable is required")
        return cls(base_url=base_url, api_key=api_key)


class PhoneApiClient:
    def __init__(self, config: PhoneApiConfig):
        self._config = config
        self._session = requests.Session()
        self._session.headers.update({
            "Authorization": f"Bearer {config.api_key}",
        })
        self._session.verify = config.verify_ssl

    def _url(self, path: str) -> str:
        return f"{self._config.base_url}/api/v1{path}"

    def _request_id(self) -> str:
        return str(uuid.uuid4())

    def health(self) -> dict:
        resp = self._session.get(
            self._url("/health"),
            headers={"X-Request-ID": self._request_id()},
            timeout=self._config.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def auth_check(self) -> dict:
        resp = self._session.get(
            self._url("/auth/check"),
            headers={"X-Request-ID": self._request_id()},
            timeout=self._config.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def battery(self) -> dict:
        resp = self._session.get(
            self._url("/battery"),
            headers={"X-Request-ID": self._request_id()},
            timeout=self._config.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def device(self) -> dict:
        resp = self._session.get(
            self._url("/device"),
            headers={"X-Request-ID": self._request_id()},
            timeout=self._config.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def location(self) -> dict:
        resp = self._session.get(
            self._url("/location"),
            headers={"X-Request-ID": self._request_id()},
            timeout=self._config.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def notify(self, title: str, body: str = "", channel: str = "homelab", priority: str = "default") -> dict:
        resp = self._session.post(
            self._url("/notify"),
            headers={"X-Request-ID": self._request_id()},
            json={"title": title, "body": body, "channel": channel, "priority": priority},
            timeout=self._config.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def speak(self, text: str, locale: str = "en-US", rate: float = 1.0, pitch: float = 1.0) -> dict:
        resp = self._session.post(
            self._url("/tts/speak"),
            headers={"X-Request-ID": self._request_id()},
            json={"text": text, "locale": locale, "rate": rate, "pitch": pitch},
            timeout=self._config.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def capture_photo(self, camera_id: Optional[str] = None, facing: str = "back") -> dict:
        payload = {"facing": facing}
        if camera_id is not None:
            payload["cameraId"] = camera_id

        resp = self._session.post(
            self._url("/camera/capture"),
            headers={"X-Request-ID": self._request_id()},
            json=payload,
            timeout=self._config.timeout,
        )
        resp.raise_for_status()
        return resp.json()
