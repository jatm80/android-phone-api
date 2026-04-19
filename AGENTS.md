# AGENTS.md

## Purpose
This repository builds an Android app that exposes a **secure, local-network API** so trusted homelab systems can access selected phone functions ove([developers.openai.com](https://developers.openai.com/codex/guides/agents-md?utm_source=chatgpt.com)). Codex should read it before doing any work and follow it as the default operating contract for planning, coding, testing, and handing off changes.

---

## How Codex should work in this repo

### Plan first for non-trivial work
For anything larger than a small isolated edit, start in planning mode before making changes.

Codex should:
1. inspect the relevant code and docs,
2. produce a short implementation plan,
3. identify risks, Android constraints, and test impact,
4. then execute in small verifiable steps.

Do not jump straight into broad implementation if the task touches security, permissions, app lifecycle, networking, persistence, or API contracts.

### Keep changes narrow and reviewable
- Prefer small diffs over wide speculative refactors.
- Do not rename or move files unless the payoff is clear.
- Preserve working behavior unless the task explicitly changes it.
- Add or update tests when behavior changes.

### State assumptions explicitly
If Android platform behavior, permission limits, or background restrictions are relevant, state the assumption in the task output or PR summary.

### Never bypass security for convenience
This app intentionally exposes phone functions over the LAN. Security is part of the feature, not a later hardening pass.

---

## Product goal
Build a standalone Android application inspired by the goal of Termux:API, but designed for modern Android and a homelab setting:
- local Wi‑Fi access,
- strong authentication,
- least privilege,
- explicit user consent,
- auditable actions,
- clear capability boundaries.

The app should let homelab callers with the shared API key call selected phone capabilities such as:
- health and status
- battery and device info
- user-visible notifications
- clipboard access where allowed
- media controls where feasible
- location read with explicit permission
- file exchange inside app-managed storage

The app must **not** assume root, hidden APIs, or unsafe Android workarounds.

---

## Codex subagent model for this repo
Codex only spawns subagents when explicitly instructed, so use the following roles when a task benefits from parallel work.

### architect
Use for:
- module boundaries
- API and lifecycle design
- ADRs
- tradeoff analysis

Expected outputs:
- short design note
- file/module impact summary
- recommended implementation order

### android-platform
Use for:
- Kotlin app structure
- services
- permissions
- lifecycle
- capability adapters
- Android-specific constraints

Expected outputs:
- implementation diff
- notes on Android version behavior
- test notes

### api-server
Use for:
- embedded server
- routing
- schemas
- validation
- OpenAPI
- protocol decisions

Expected outputs:
- endpoint design
- server implementation
- request/response examples

### security
Use for:
- API-key authentication
- authn/authz
- key management
- policy enforcement
- threat review
- logging hygiene

Expected outputs:
- threat notes
- auth flow updates
- hardening checklist deltas

### qa
Use for:
- unit/integration/instrumentation coverage
- regression checks
- lifecycle edge cases
- Wi‑Fi and permission-state validation

Expected outputs:
- test additions
- manual verification list
- failure-mode notes

### docs
Use for:
- README
- quickstart
- API docs
- user-facing permission explanations
- troubleshooting

Expected outputs:
- updated docs
- concise setup steps
- known limitations section

---

## When to use subagents
Use subagents for tasks that have natural separation. Examples:
- ask **architect** for the design and **security** for the trust model before implementing API-key authentication
- ask **android-platform** and **api-server** to split UI/service work from API work
- ask **qa** to prepare or extend tests while implementation proceeds

Do not spawn subagents for trivial changes.
Do not spawn many agents if the task is small enough for one coherent change.

---

## Core engineering rules

### Security rules
- Default to local-network only.
- Default to deny-all capabilities until explicitly granted.
- Require encrypted transport.
- Use API-key authentication for the project trust model unless a future ADR explicitly reintroduces mTLS.
- Generate API keys randomly in-app, store them securely, allow phone-side enable/disable and reset, and never log or expose them except through an intentional user reveal action.
- Keep server private keys in Android Keystore where possible.
- Log privileged actions, but never secrets.
- Treat API key reveal, reset, and enable/disable actions as privileged workflows requiring intentional on-device user action.

### API rules
- Version endpoints under a stable prefix.
- Every endpoint must map to a named capability.
- Every privileged endpoint must pass through authn and authz middleware.
- Keep request and response schemas explicit.
- Return consistent error shapes.

### Android rules
- Respect modern background execution limits.
- Use a foreground service when the server is actively running if required by platform behavior.
- Do not rely on hidden APIs.
- Request permissions just in time, not all at startup.
- Document behavior differences across Android versions where relevant.

### Code quality rules
- Kotlin first.
- Use coroutines.
- Prefer interfaces around Android system services.
- Keep business logic testable outside the UI.
- Prefer clear names over clever abstractions.
- Follow SOLID design principles, especially around capability adapters, policy enforcement, and Android service boundaries.
- Use test-driven development for coding tasks: write or update focused tests before implementing behavior when feasible.
- Maintain at least 90% code test coverage for production code, with coverage gaps called out explicitly in handoff notes.

### Development environment rules
- Use Docker as the default development environment so contributors do not need to install Kotlin, Gradle, Android SDK tools, or related build tooling locally.
- Prefer `docker compose` for repeatable local workflows when it makes build, test, lint, coverage, or emulator/service orchestration clearer.
- All standard build, lint, test, and coverage commands must be runnable inside Docker.
- GitHub Actions must use the same Docker-based workflow, or build and run the same development image, so local and CI behavior stay aligned.
- Document local Docker commands in README or developer docs whenever the workflow changes.

---

## Project scope
Codex should optimize for a small, secure homelab project before stretching into broader device control.

### Core scope
- embedded HTTPS server
- API-key authentication and phone-side controls
- health endpoint
- battery and device info endpoints
- send notification to phone
- audit log viewer
- sample homelab client

### Optional after core basics are stable
- clipboard read/write
- media controls
- location read
- app-sandbox file exchange
- mDNS discovery

### Out of scope unless explicitly approved
- root features
- silent surveillance features
- unrestricted contacts/calendar access
- SMS/call control without a well-defined, policy-approved flow
- background camera or microphone streaming
- accessibility-service automation as a shortcut for unsupported features

---

## Repo workflow conventions for Codex

### Before coding
Codex should read:
- this file
- README if present
- app/service/security docs if present
- build files and existing architecture notes
- Dockerfile, compose files, and CI workflow files if present

### During coding
Codex should:
- keep a short task plan,
- use TDD for behavior changes by defining the expected behavior in tests before implementation when feasible,
- update tests near the changed logic,
- preserve or improve the repository's 90% production-code test coverage target,
- run build, lint, tests, and coverage through Docker unless a task is explicitly documentation-only or Docker is not yet available,
- avoid broad speculative cleanup,
- note any Android or security constraints that shaped the implementation.

### Before handoff
Codex should provide:
- what changed
- why it changed
- files touched
- tests run or not run
- follow-up risks or limitations

---

## Definition of done
A task is done only when:
- the code builds or the diff is internally consistent,
- tests were added or updated where appropriate,
- relevant tests were written before or alongside implementation in a TDD workflow when feasible,
- production-code test coverage remains at or above 90%, or any shortfall is documented with a concrete follow-up,
- build, lint, tests, and coverage pass in Docker once the Docker development environment exists,
- security implications were considered,
- docs were updated if user-visible behavior changed,
- the solution stays inside Android platform constraints.

---

## Preferred implementation sequence for the overall project
1. threat model and capability matrix
2. Docker development and CI baseline
3. architecture and module layout
4. API-key auth design
5. embedded server skeleton
6. shared API-key authentication and audit logging
7. first end-to-end core capability
8. audit log UI
9. sample homelab client
10. testing and hardening pass
