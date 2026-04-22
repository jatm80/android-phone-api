
---

## TASKS.md

## Task-000 — Create the initial design pack
**Status:** done
**Recommended agent(s):** architect, security

### Goal
Produce the minimum design artifacts Codex needs before implementation begins.

### Deliverables
- capability matrix
- initial project scope
- trust boundaries
- short ADRs for server stack and auth model

### Acceptance criteria
- every candidate capability is marked as core, later, rejected, or Android-constrained
- trust assumptions for LAN clients are written down
- one preferred auth model is selected

### Codex prompt hint
Ask architect for the module plan and security for the threat model in parallel, then consolidate into one design note.

---

## Task-001 — Scaffold the Android project for server mode
**Status:** done
**Recommended agent(s):** android-platform

### Goal
Create the application skeleton that can host a local API server safely.

### Deliverables
- Kotlin app scaffold
- foreground service scaffold
- settings or home screen for server state
- dependency baseline

### Acceptance criteria
- app starts cleanly
- server lifecycle can be represented in UI
- foreground service start/stop path is clear

### Codex prompt hint
Keep this task structural only. Do not add broad capability logic yet.

---

## Task-002 — Create Docker development environment
**Status:** done
**Recommended agent(s):** android-platform, qa, docs

### Goal
Make all development, build, lint, test, and coverage workflows runnable in Docker so contributors do not need local Kotlin, Gradle, Android SDK, or Android build tooling installed.

### Deliverables
- Dockerfile for Android/Kotlin development
- optional `docker-compose.yml` for common workflows
- containerized Gradle build command
- containerized lint command
- containerized unit test and coverage commands
- README or developer docs with Docker usage
- CI notes for reusing the same image or commands in GitHub Actions

### Acceptance criteria
- a clean machine with Docker can build the project without installing Kotlin or Android tooling locally
- tests and coverage run inside Docker
- generated build outputs and Gradle caches are handled without polluting git
- Docker commands are documented and copy-pasteable
- the setup works consistently enough to be used by the GitHub pipeline

### Codex prompt hint
Prefer a practical Android SDK base image or a pinned custom image. Keep secrets and signing material out of the image.

---

## Task-003 — Implement the embedded API server skeleton
**Status:** done
**Recommended agent(s):** api-server

### Goal
Stand up the local API with routing, middleware, and structured errors.

### Deliverables
- embedded HTTPS server bootstrap
- versioned route prefix
- health endpoint
- request ID middleware
- consistent error model

### Acceptance criteria
- another LAN device can reach the health endpoint once trust is established
- plaintext mode is not the default production path
- logs identify request outcomes without exposing secrets

---

## Task-004 — Finalize API-key authentication model
**Status:** done
**Recommended agent(s):** security, architect, api-server

### Goal
Document the API-key auth model as the project trust mechanism so the project no longer depends on mTLS pairing.

### Deliverables
- short auth ADR
- request authentication header convention
- API enabled/disabled behavior
- API key reset and invalidation behavior
- rate-limit and audit expectations for failed auth
- migration note for removing existing pairing or mTLS code

### Acceptance criteria
- API key auth is explicitly selected as the project auth mechanism
- mTLS pairing is marked out of scope unless a future design reintroduces it
- API key handling rules match the phone-side controls task
- security tradeoffs are documented, including local-network assumptions and key exposure risk

### Codex prompt hint
Keep HTTPS for transport security, but remove mTLS client-certificate pairing from the project auth path.

---

## Task-005 — Implement API key generation and phone-side controls
**Status:** done
**Recommended agent(s):** security, android-platform, api-server, qa

### Goal
Generate a random API key inside the app and let the phone user enable, disable, reset, and view it from a simple local UI.

### Deliverables
- cryptographically secure API key generation
- API key storage model
- API authentication middleware
- simple phone UI with three controls:
  - API enabled or disabled
  - API key reset, which generates a new key
  - present API key
- audit events for enable, disable, reset, and failed auth attempts
- tests for key generation, reset invalidation, enabled state, and auth middleware

### Acceptance criteria
- first app launch creates or prompts creation of a strong random API key
- API can be disabled without deleting the key
- resetting the key immediately invalidates the previous key
- presenting the API key requires an intentional user action and never writes the key to logs
- API key is stored using Android-appropriate secure storage, preferably encrypted with Android Keystore-backed material
- unauthorized, disabled, and stale-key requests fail with consistent error shapes
- UI state survives app restarts

### Codex prompt hint
Keep the UI intentionally basic: one enabled/disabled control, one reset button, and one present-key action. Treat the API key as a secret even when the user chooses to reveal it.

---

## Task-006 — Clean up mTLS pairing code and docs
**Status:** done
**Recommended agent(s):** security, android-platform, api-server, qa, docs

### Goal
Remove mTLS client-certificate pairing code, models, storage, routes, docs, and tests now that API-key auth is the selected project trust mechanism.

### Deliverables
- removal of pairing domain code no longer needed for API-key auth
- removal of mTLS client-auth middleware and certificate trust-store logic
- cleanup of pairing storage, repositories, routes, screens, and docs
- updated tests for API-key auth as the only project auth path
- migration or data-clearing note for any persisted pairing state

### Acceptance criteria
- no production code path requires paired client certificates for project access
- stale pairing models, repositories, endpoints, and UI entry points are removed or explicitly marked future-only
- tests no longer expect mTLS pairing behavior
- API-key authentication remains covered by unit or integration tests
- docs and sample clients describe API-key auth instead of mTLS pairing
- HTTPS transport remains required even though mTLS client auth is removed

### Codex prompt hint
Search for `pairing`, `mTLS`, `client certificate`, trust store, certificate fingerprint, and related storage before editing. Keep any reusable generic HTTPS server pieces that are still needed.

---

## Task-007 — Persist API secrets and audit metadata
**Status:** done
**Recommended agent(s):** android-platform, security

### Goal
Store operational state safely.

### Deliverables
- Room schema or equivalent
- Keystore-backed secret handling
- storage for API enabled state, API key metadata, and audit metadata

### Acceptance criteria
- API key material is not stored insecurely
- server restarts preserve API enabled state and audit metadata
- migration strategy is at least minimally considered

---

## Task-008 — Add core endpoint: battery and device info
**Status:** done
**Recommended agent(s):** android-platform, api-server

### Goal
Ship the first genuinely useful, low-risk API capability.

### Deliverables
- authenticated battery endpoint
- authenticated device info endpoint
- schema docs
- tests

### Acceptance criteria
- requests with a valid enabled API key can retrieve data
- missing, disabled, or invalid API key requests cannot
- output shape is stable and documented

---

## Task-009 — Add core endpoint: notify the phone
**Status:** done
**Recommended agent(s):** android-platform, api-server, qa

### Goal
Let the homelab send a user-visible notification to the phone.

### Deliverables
- notification endpoint
- input validation
- notification channel/category defaults
- tests or manual verification notes

### Acceptance criteria
- valid payload produces a notification
- payload limits exist
- abuse and spam risk is reasonably constrained

---

## Task-010 — Build the audit log path end to end
**Status:** done
**Recommended agent(s):** security, android-platform

### Goal
Record what privileged actions happened and show them in-app.

### Deliverables
- audit event model
- logging hooks
- in-app audit screen

### Acceptance criteria
- each privileged action records route or capability, time, caller context where available, and outcome
- secrets are not logged
- log viewer is usable enough for homelab debugging

---

## Task-011 — Deliver a reference homelab client
**Status:** done
**Recommended agent(s):** docs, api-server

### Goal
Provide at least one clean reference client for real homelab use.

### Deliverables
- Python or Go sample client
- example config
- API key setup instructions
- usage examples for at least two endpoints

### Acceptance criteria
- a user can configure the API key and call the API from another device on the same Wi‑Fi
- auth flow matches the real product model

---

## Task-012 — Expand into optional capabilities
**Status:** done
**Recommended agent(s):** android-platform, api-server, security, qa

### Goal
Add further capabilities only after the secure core works.

### Candidate capabilities
- clipboard read/write
- media controls
- location read
- app-sandbox file exchange

### Acceptance criteria
- each new capability has explicit UX, permission notes, API-key auth behavior, consent behavior where needed, and tests proportional to risk
- Android limitations are documented where they affect behavior

---

## Task-013 — Implement optional API: text-to-speech
**Status:** done
**Recommended agent(s):** android-platform, api-server, security, qa

### Goal
Let requests with a valid API key trigger user-audible speech output on the phone.

### Deliverables
- TTS capability definition
- authenticated TTS endpoint
- Termux:API-inspired behavior mapped from `termux-tts-speak` and `termux-tts-engines`
- request schema for text, locale, rate, pitch, and queue behavior where supported
- Android TextToSpeech adapter
- audit events and tests or manual verification notes

### Acceptance criteria
- only requests with a valid enabled API key can trigger speech
- payload length and rate limits prevent obvious abuse
- speech is user-audible and auditable, not hidden behavior
- unsupported locale or TTS engine states return consistent API errors

### Codex prompt hint
Treat this as a user-visible action. Include controls or settings that let the phone user disable or revoke the capability quickly.

---

## Task-014 — Implement optional API: camera photo capture
**Status:** done
**Recommended agent(s):** android-platform, api-server, security, qa

### Goal
Let requests with a valid API key request a still photo while preserving explicit consent and Android privacy expectations.

### Deliverables
- camera capture capability definition
- authenticated photo capture endpoint
- Termux:API-inspired behavior mapped from `termux-camera-info` and `termux-camera-photo`
- request schema for camera facing, resolution preference, timeout, and storage target
- CameraX or platform camera adapter
- permission and consent UX
- audit events and tests or manual verification notes

### Acceptance criteria
- capture cannot occur without the required Android camera permission and phone-side user consent
- no background camera streaming is introduced
- captured photos are stored only in the approved app-managed or user-approved location
- endpoint responses expose metadata and retrieval path without leaking unrelated files
- failed, denied, and timed-out captures return consistent API errors and audit records

### Codex prompt hint
Prefer a foreground, user-visible capture flow. Do not use hidden APIs, accessibility automation, or silent surveillance behavior.

---

## Task-015 — Implement optional API: audio recording
**Status:** done
**Recommended agent(s):** android-platform, api-server, security, qa

### Goal
Let requests with a valid API key request bounded audio recordings with explicit user awareness and revocable controls.

### Deliverables
- audio recording capability definition
- authenticated recording endpoint
- Termux:API-inspired behavior mapped from `termux-microphone-record`
- request schema for duration, quality, format, and storage target
- recording service or adapter using supported Android media APIs
- foreground notification and permission UX
- audit events and tests or manual verification notes

### Acceptance criteria
- recording cannot start without microphone permission and explicit phone-side user consent
- recording duration and file size are bounded
- no background microphone streaming is introduced
- recordings are stored only in the approved app-managed or user-approved location
- start, stop, denial, timeout, and failure cases are audited without logging sensitive audio contents

### Codex prompt hint
Model this as a high-risk capability. Require strong API-key checks, visible recording state, and clear user controls before exposing it.

---

## Task-016 — Implement optional API: SMS send
**Status:** done
**Recommended agent(s):** android-platform, api-server, security, qa, docs

### Goal
Let requests with a valid API key request SMS delivery to explicit recipient numbers while preserving phone-side consent, carrier cost awareness, and abuse controls.

### Deliverables
- SMS send capability definition
- authenticated SMS send endpoint
- Termux:API-inspired behavior mapped from `termux-sms-send`
- request schema for recipient number list, message text, SIM slot where supported, and idempotency key
- permission and consent UX for SMS sending
- delivery attempt result model
- audit events and tests or manual verification notes

### Acceptance criteria
- SMS sending cannot occur without Android SMS permission, explicit phone-side approval, and a valid enabled API key
- recipients must be explicit and validated; address book expansion or unrestricted contact access is not introduced
- message length, recipient count, frequency, and retry behavior are bounded to limit spam and accidental carrier charges
- SIM slot selection is optional and degrades clearly on devices or Android versions where it is unavailable
- successful, denied, failed, and partially delivered sends return consistent API errors or result objects
- audit records include client, capability, recipient count, time, and outcome, but do not log full message contents by default

### Codex prompt hint
Model this as a high-risk capability. Follow `termux-sms-send` for the command shape, but add stronger LAN API safeguards: API-key auth, on-device approval, rate limits, idempotency, and careful audit logging.

---

## Task-017 — Add open source license and project metadata
**Status:** done
**Recommended agent(s):** docs, security

### Goal
Make the repository ready for open source use with an explicit license and basic project metadata.

### Deliverables
- selected OSS license
- `LICENSE` file
- README license section
- copyright and attribution notes where needed
- dependency license review checklist

### Acceptance criteria
- repository has a clear OSI-approved license
- README identifies the license and any important third-party attribution expectations
- license choice is compatible with intended Android app distribution and any reused code
- dependency license review is documented before release

### Codex prompt hint
If no license has been chosen yet, propose a short tradeoff between Apache-2.0, MIT, and GPL-compatible choices before editing files.

---

## Task-018 — Create GitHub CI pipeline
**Status:** done
**Recommended agent(s):** qa, android-platform, docs

### Goal
Run repeatable validation on every pull request and main-branch push.

### Deliverables
- GitHub Actions workflow for CI
- Docker-based CI execution using the same development image or compose workflow
- Gradle dependency caching
- formatting or lint checks where available
- unit test execution
- coverage report generation and enforcement
- build artifact retention for CI runs

### Acceptance criteria
- pull requests run build, lint, tests, and coverage checks automatically
- CI runs inside Docker or builds and runs the same Docker development image used locally
- CI fails if production-code coverage drops below 90%
- workflow avoids committing secrets or generated build outputs
- workflow documents required local and CI commands

### Codex prompt hint
Keep CI separate from release publishing. Use the repository's Gradle wrapper once it exists, prefer pinned action versions, and keep local Docker commands aligned with CI steps.

---

## Task-019 — Create semantic-versioned Android release pipeline
**Status:** done
**Recommended agent(s):** android-platform, qa, security, docs

### Goal
Build signed Android APK artifacts and publish them as GitHub Releases using semantic versioning.

### Deliverables
- GitHub Actions release workflow
- Docker-based release build using the same Android build environment as CI
- semantic version tag convention such as `vMAJOR.MINOR.PATCH`
- APK build task for release variants
- signing configuration using GitHub Actions secrets
- generated checksums for release artifacts
- GitHub Release creation with APK files attached
- release notes template or changelog source

### Acceptance criteria
- pushing a valid semantic version tag creates a GitHub Release
- release APK files are built inside Docker or the same pinned Android build image used by CI
- release APK files are built reproducibly from the tagged commit
- signing keys and passwords are read only from encrypted repository secrets
- unsigned/debug artifacts are clearly separated from signed release APKs
- release includes checksums and concise notes
- failed builds do not publish partial releases

### Codex prompt hint
Do not store Android signing keys in git. Document the required GitHub secrets and include a dry-run or manual-dispatch path if useful.

---

## Task-020 — Hardening and release-readiness pass
**Status:** done
**Recommended agent(s):** security, qa, docs

### Goal
Prepare the homelab project for real personal use.

### Deliverables
- security review delta
- test matrix completion
- quickstart docs
- known limitations list
- Docker development environment verification notes
- CI and release pipeline verification notes

### Acceptance criteria
- critical issues are closed or explicitly documented
- onboarding path is documented end to end
- server, API-key auth, first endpoints, and audit path are all verified together
- local Docker build, test, lint, and coverage commands are verified
- CI, coverage enforcement, licensing, and release artifact generation are verified before publishing

---

## Task-021 — Implement foreground UI for camera photo capture
**Status:** to-do
**Recommended agent(s):** android-platform, api-server, security, qa, docs

### Goal
Make `POST /api/v1/camera/capture` take a real still photo through a foreground, user-visible Android camera flow while preserving explicit consent and avoiding silent capture.

### Deliverables
- Android `CAMERA` permission and non-required camera feature declaration
- CameraX dependency baseline and app-managed JPEG capture implementation
- foreground capture Activity or Compose screen with preview, capture, cancel, and permission states
- API-to-UI coordination layer for pending capture requests, user approval, cancellation, timeout, and result delivery
- updated `CameraProvider` contract if capture must become suspendable
- app-managed capture storage and metadata response mapping
- audit events for request, approval, denial, cancellation, timeout, success, and failure
- tests for route mapping, validation, coordinator outcomes, and permission/consent failure modes
- README and sample-client notes documenting phone-side approval and Android permission behavior

### Acceptance criteria
- a valid enabled API key is required before any capture request is accepted
- capture cannot occur unless the app is in a foreground, user-visible consent flow
- first use requests Android camera permission just in time
- the phone user can approve, cancel, or ignore a capture request
- timed-out, cancelled, denied, invalid camera, unavailable camera, and failed capture states return consistent API responses
- successful capture stores a JPEG only in app-managed or user-approved storage
- API responses expose only capture metadata and approved retrieval paths, not arbitrary filesystem access
- no hidden APIs, accessibility automation, silent surveillance behavior, or background camera streaming are introduced
- privileged camera actions are audited without logging image contents or secrets

### Codex prompt hint
Prefer a waiting request with a short timeout for the first implementation unless an async result model is introduced deliberately. Be careful with Android background activity launch limits; a notification or in-app pending request UI may be needed to bring the phone user into the foreground flow reliably.

---

## Suggested Codex execution order
1. Task-000
2. Task-001 and Task-002
3. Task-003
4. Task-004 and Task-005
5. Task-006
6. Task-007
7. Task-008 and Task-009
8. Task-010
9. Task-011
10. Task-012
11. Task-013, Task-014, Task-015, and Task-016
12. Task-017 and Task-018
13. Task-019
14. Task-020
15. Task-021

---

## Example top-level Codex task prompt for this repo
Implement the next task for the secure Android homelab API app. Read AGENTS.md first. Plan before coding. If the task crosses architecture and security concerns, use the appropriate subagents. Keep changes narrow, add tests where behavior changes, and include a short handoff summary with files changed, tests run, and any Android-specific limitations.
