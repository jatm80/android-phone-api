# Initial Design Pack

## Status
Accepted for MVP planning.

## Context
This project is an Android app that exposes a secure API to trusted homelab systems on the local network. The app must assume no root access, no hidden Android APIs, explicit user consent, least privilege, and auditable privileged actions.

The repository is starting from documentation only. This design pack defines the minimum direction needed before scaffolding the Android app, Docker workflow, API server, and trust model.

## MVP Scope
The MVP should prove a secure, user-controlled local API path before adding broader device capabilities.

MVP includes:
- Android app shell with a home or settings screen showing server state.
- Foreground service lifecycle for the API server when actively running.
- Embedded HTTPS API server.
- Pairing and trust establishment with explicit phone-side approval.
- Per-client capability grants, denied by default.
- Health endpoint.
- Battery and device info endpoints.
- User-visible notification endpoint.
- Audit log storage and phone-side viewer.
- Basic trusted client management UI.
- Sample homelab client.

Later candidates:
- Clipboard read/write where Android restrictions allow.
- Media controls where Android APIs and user expectations permit.
- Location read with just-in-time permission and explicit grant.
- App-sandbox file exchange.
- mDNS discovery.

Rejected unless explicitly approved:
- Root features.
- Hidden APIs.
- Silent surveillance features.
- Unrestricted contacts or calendar access.
- SMS or call control without a policy-approved flow.
- Background camera or microphone streaming.
- Accessibility-service automation as a workaround for unsupported APIs.

## Capability Matrix
| Capability | Classification | Notes |
| --- | --- | --- |
| Health/status | MVP | Low sensitivity. Keep output minimal and versioned. Default to authenticated unless a deliberately public readiness endpoint is documented. |
| Battery info | MVP | Low to moderate sensitivity. Requires trusted client authentication and a capability grant. |
| Device info | MVP | Moderate fingerprinting risk. Avoid serials, IMEI, advertising IDs, account data, and other stable unique identifiers. |
| Send user-visible notification | MVP | Abuse and spam risk. Requires authentication, authorization, payload limits, rate limits, audit logging, and Android notification permission on modern Android. |
| Audit log viewer | MVP | Phone-side UI by default. Remote audit access is a separate later capability. |
| Client management UI | MVP | Phone-side privileged UI. No remote grant or revoke path until explicitly designed. |
| Pairing/trust establishment | MVP | Privileged workflow. Must require explicit on-device approval. |
| Per-client capability grants | MVP | Deny by default. Required before meaningful privileged endpoints. |
| Clipboard read/write | Android-constrained/later | Modern Android restricts background clipboard access and may show privacy indicators. Requires version-specific documentation. |
| Media controls | Android-constrained/later | Feasibility depends on media session APIs and active sessions. Do not use unsupported control workarounds. |
| Location read | Later | Sensitive. Requires just-in-time permission, clear UI disclosure, capability grant, and audit logging. |
| App-sandbox file exchange | Later | Safer than shared storage. Requires path validation, size limits, MIME/type handling, and retention rules. |
| mDNS discovery | Later | Convenience only. Must not imply trust or advertise secrets. |
| Root features | Rejected | Violates product constraints. |
| Silent surveillance | Rejected | Includes stealth location, background camera/microphone, or hidden monitoring. |
| Unrestricted contacts/calendar access | Rejected unless explicitly approved | High sensitivity and outside MVP. |
| SMS/call control | Rejected unless explicitly approved | High abuse risk and platform-policy complexity. |
| Background camera/microphone streaming | Rejected | Explicitly outside the product boundary. |
| Accessibility-service automation shortcut | Rejected | Must not be used to bypass unsupported APIs. |

## Trust Boundaries
Phone/app process:
- Highest-trust app boundary.
- Owns server lifecycle, server private key access, client registry, capability grants, audit metadata, API enabled state, and Android capability adapters.

Android OS and Keystore:
- Trusted platform boundary for permission enforcement and key protection.
- Assumption: the device is not rooted and the app does not depend on hidden APIs or unsafe workarounds.

Local network:
- Untrusted transport environment, even on home Wi-Fi.
- Other LAN devices may scan ports, spoof addresses, attempt pairing, replay traffic, or observe weak transport.

Trusted homelab client:
- Trusted only after explicit phone-side enrollment.
- Trust is scoped by client identity and per-client grants, never by subnet or IP address alone.

User/operator:
- Sole authority for pairing approval, API enable/disable, credential reveal/reset, capability grants, and revocation.

Remote internet:
- Out of scope by default.
- Port forwarding, reverse proxies, VPN exposure, and tunnels materially change the threat model and require separate review.

## LAN Client Assumptions
- LAN presence is not proof of trust.
- Client IP addresses are useful for audit context and local filtering, but not primary authentication.
- Trusted clients may be compromised later, so revocation must be available and checked on every request.
- Pairing must show enough metadata for the user to recognize the enrolling client.
- Every privileged request must be authenticated, authorized, and auditable.

## Architecture Boundaries
Use a Kotlin-first Android app with boundaries that keep security, server behavior, and platform adapters independently testable.

Initial logical modules or packages:
- `app`: Android entry points, Compose UI, app wiring, foreground service declaration, navigation, and resources.
- `core:model`: stable domain models for clients, capabilities, grants, audit events, request IDs, and error shapes.
- `core:security`: pairing model, client identity, mTLS policy, credential abstractions, and certificate/key lifecycle interfaces.
- `core:policy`: deny-by-default authorization engine mapping authenticated clients and endpoint capabilities to decisions.
- `core:audit`: audit event contracts, sanitization rules, and logging discipline.
- `server`: embedded server bootstrap, routing, middleware, request IDs, schema serialization, and structured errors.
- `capabilities`: capability interfaces and Android-backed adapters for health, battery/device info, notifications, and later capabilities.
- `data`: persistence for clients, grants, audit metadata, and Keystore-backed secret handling.
- `sample-client`: external client once pairing and authentication stabilize.

Early scaffolding may keep these as packages inside `:app` if a multi-module Gradle setup would slow the initial app skeleton. Package boundaries should still preserve a clear extraction path.

## Lifecycle Shape
- Main activity owns user-facing controls and visible server state.
- A foreground service owns the active API server lifecycle when the server is running.
- The server cannot start silently as an indefinite background process.
- Server start requires explicit user action or a clearly persisted enabled setting, subject to Android background execution limits.
- The service exposes lifecycle state to UI through a repository or observable state holder.
- Capability adapters check Android permission state at call time and return structured errors. The app must not request all permissions at startup.

## ADR-0001: Server Stack
Decision: use Ktor embedded server with the CIO engine, Kotlin coroutines, and Kotlinx Serialization.

Rationale:
- Ktor fits Kotlin and coroutine-first Android code.
- Routing and middleware support versioned APIs, request IDs, structured errors, authentication, and authorization checks.
- Most routing and policy behavior can be tested on the JVM without Android instrumentation.
- It avoids hand-rolled socket or HTTP handling in security-sensitive code.

Constraints:
- Production API transport is HTTPS.
- Plain HTTP may exist only as an explicitly gated debug or test mode and must not be the default path.
- The server binds only when explicitly enabled by the user.
- The server should prefer local-network exposure and avoid public internet assumptions.

Risks to validate in implementation:
- Android behavior for embedded TLS and client certificate authentication.
- Ktor/CIO mTLS configuration details on Android.
- APK size and dependency footprint.

Alternatives considered:
- NanoHTTPD: smaller, but weaker routing, middleware, and TLS ergonomics.
- OkHttp MockWebServer: useful for tests, not a production embedded app server.
- Raw sockets: too much custom security-sensitive code.

## ADR-0002: Auth Model
Decision: prefer mTLS with per-client certificates established through explicit phone-side pairing.

Model:
- On setup, the app generates or provisions server TLS trust material and keeps private key access Android-appropriate, preferring Keystore-backed material where feasible.
- Pairing creates or approves a stable client identity and binds it to a client certificate or public key.
- Normal API requests require TLS and trusted client certificate authentication.
- Authorization is enforced by client ID and capability grants.
- Revocation is checked by app policy on every request, even if TLS accepts a certificate chain.

Rationale:
- Aligns with the repository security rule to prefer mTLS.
- Avoids trusting LAN location.
- Gives stable client identity independent of IP address.
- Supports per-client grants, revocation, and audit records.
- Reduces reliance on long-lived bearer secrets.

API key compatibility:
- `TASKS.md` includes a later task for API key generation and phone-side controls.
- Treat API keys as an explicitly approved compatibility or transitional mode behind the same authentication and authorization middleware, not as the preferred long-term trust model.
- API keys, if implemented, must be randomly generated in-app, securely stored, revealable only by intentional phone-side action, resettable, disableable, and never logged.

## Pairing Requirements
- Pairing is disabled by default.
- The user starts a short-lived pairing session on the phone.
- Pairing requires an out-of-band verifier such as a QR code, numeric code, or fingerprint confirmation.
- A pending client receives no privileged access until the phone user approves it.
- Pairing attempts, approvals, denials, expirations, and revocations are audited.
- Revocation removes or invalidates client trust and denies subsequent requests.

## API Rules
- Version API endpoints under `/api/v1`.
- Every privileged endpoint declares a named capability requirement.
- Authentication and authorization are centralized in middleware or route wrappers.
- Errors use a consistent shape with a request ID.
- Security failures avoid revealing whether a credential, client, or grant exists unless the user is viewing phone-side diagnostics.
- Request outcomes are logged without secrets.

## Logging and Audit Hygiene
Never log:
- API keys, tokens, private keys, certificate private material, pairing secrets, or bearer credentials.
- Full privileged request bodies.
- Android permission-sensitive values unless they are explicitly meant for audit and are sanitized.

Log or audit:
- Request ID.
- Timestamp.
- Authenticated client ID when available.
- Route and capability.
- Allow/deny decision.
- Response status.
- Coarse failure reason.

For notification, file, location, and similar endpoints, log metadata and outcome rather than sensitive payloads.

## Hardening Checklist
- Enforce HTTPS/mTLS by default.
- Keep plaintext transport behind explicit debug/test gates only.
- Deny all capabilities until granted to a trusted client.
- Centralize authentication and authorization so endpoints cannot bypass checks.
- Add rate limits for pairing, failed auth, notification sends, and future write-like endpoints.
- Use consistent error shapes that avoid leaking security-sensitive details.
- Prefer Android Keystore-backed material for server private keys and secret wrapping.
- Require phone-side UI for API enable/disable, pairing approval, credential reveal/reset, grant edits, and revocation.
- Document Android version constraints for notifications, clipboard, location, foreground services, and background execution.
- Treat public exposure through VPN, tunnels, or reverse proxies as a separate deployment mode requiring review.

## Implementation Order
1. Scaffold the Android app and foreground service lifecycle representation.
2. Add Docker build, lint, test, and coverage workflows.
3. Introduce core domain packages for capabilities, clients, audit events, and errors.
4. Add Ktor server skeleton with `/api/v1`, request IDs, health, and structured errors.
5. Implement pairing and mTLS trust establishment.
6. Add deny-by-default policy enforcement and persistence.
7. Ship authenticated battery and device info endpoints.
8. Add validated user-visible notification endpoint.
9. Build audit log and client management UI.
10. Add sample homelab client and hardening checks.

## Test Strategy
This design task is documentation-only, so no tests are required.

Future implementation should add JVM tests for:
- Policy decisions.
- Error mapping.
- Authentication middleware.
- Audit sanitization.
- Capability adapters through interfaces.
- Server routing behavior.

Android instrumentation should cover:
- Foreground service lifecycle.
- Permission-state behavior.
- Notification delivery.
- UI flows for pairing, API controls, grants, audit, and revocation.

