# Security Review

## Review Date
2024-01-01

## Scope
Security posture review for homelab use of the Android Phone API.

## Authentication
- ✅ API key generated with cryptographically secure random source
- ✅ API key stored encrypted with Android Keystore-backed AES/GCM
- ✅ Authentication uses salted SHA-256 with constant-time comparison
- ✅ API can be disabled without deleting the key
- ✅ Key reset immediately invalidates the previous key
- ✅ Key reveal requires intentional user action
- ✅ API key never logged

## Transport
- ✅ HTTP is intentionally enabled for trusted local-network use
- ✅ Debug and release builds use the same explicit port, 8080
- ⚠️ API keys travel in plaintext on the local network
- ⚠️ No TLS encryption or certificate pinning

## Authorization
- ✅ Single shared API key model (appropriate for homelab)
- ✅ All privileged endpoints require authentication
- ⚠️ No per-capability authorization granularity
- ⚠️ No rate limiting on authenticated endpoints

## Audit
- ✅ All privileged actions logged with type, timestamp, request ID
- ✅ Secrets never appear in audit logs
- ✅ In-app audit viewer available
- ✅ Audit events capped at 500 to prevent unbounded growth

## High-Risk Capabilities
- ✅ Camera, audio, SMS return stubs until permission + consent flows implemented
- ✅ SMS audit logs recipient count, not phone numbers
- ✅ Clipboard read documents Android 10+ background restriction
- ✅ Location requires explicit permission grant

## Network Exposure
- ✅ Default to local-network only
- ⚠️ Server binds to all device interfaces; do not port-forward or expose beyond trusted Wi-Fi/VPN
- ⚠️ No mDNS discovery (future candidate)
- ⚠️ Port forwarding/VPN exposure requires separate review

## Known Gaps
1. Plaintext HTTP transport exposes API keys to local-network observers
2. No rate limiting on API endpoints
3. No per-capability enable/disable beyond global API toggle
4. Camera capture requires foreground Activity (stub until implemented)
5. Audio recording requires foreground service + RECORD_AUDIO permission (stub)
6. SMS requires SEND_SMS permission + on-device approval flow (stub)
7. Unit test coverage below 90% target (UI/service code needs instrumentation)

## Recommendations
1. Add optional HTTPS later if the API is ever exposed outside a trusted LAN/VPN
2. Add per-endpoint rate limiting
3. Add per-capability toggle in settings
4. Implement camera/audio consent UX before enabling those capabilities
5. Add instrumentation tests for lifecycle and UI flows
