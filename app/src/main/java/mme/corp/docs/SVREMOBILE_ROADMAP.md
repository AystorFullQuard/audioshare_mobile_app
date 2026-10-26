# SvReMobile — Android / ServerRelay integration roadmap

**Baseline:** 26 October 2026, ~13:07 Europe/Chisinau (EET, UTC+02), supplied `SvReMobile.zip` and `ServerRelay.zip`. **Mobile HEAD** `afb1926`; **server HEAD** `220c391` embedded in these archives. Snapshot labels are user-specified; Git hashes identify the extracted sources.
**Scope of this revision:** static analysis and planning/documentation only. No application source, APIs, builds, or infrastructure have been changed.
**Backend contract authority:** actual `ServerRelay/boot/src/main/java/sv/re/controller/`, `ServerRelay/dto/src/main/java/`, `ServerRelay/docs/src/main/docs/bootstrap/DEVICE_BOOTSTRAP_JOINT_CONTRACT_V1.md`, `security/SEC05_CSRF_CLIENT_CONTRACT.md`, and `realtime/` documents. **Do not use old mobile mock fixtures as proof of compatibility.**
**Cross-references:** [gaps and architecture](SVREMOBILE_SERVERRELAY_INTEGRATION_GAPS.md), [dependency tree](SVREMOBILE_ROADMAP_DEPENDENCIES_TREE.md), [owner decisions](SVREMOBILE_PRE_IMPLEMENTATION_DECISIONS.md).

## Status legend

| Symbol | Meaning | Rule |
|---|---|---|
| ✅ | Verified / accepted for the *specific described scope* | Only static analysis artifacts receive ✅ in this planning revision; later code steps require acceptance evidence and owner approval. |
| 🟡 | Partially present, under review, or decision pending | Existing classes do **not** demonstrate interoperability or release acceptance. |
| ⬜ | Planned | No accepted implementation evidence for this step. |
| ❌ | Confirmed blocker/incompatible candidate behavior | Static code-versus-contract mismatch or release-insecure configuration has been identified. |

Stage ✅ requires every mandatory child ✅. **Status bars count ✅ steps only, not estimated effort, and do not claim tests were run.** Current stage closure is intentionally conservative.

## Overall completion

**Verified planning/code-acceptance items:** `2/73` (2 are analysis artifacts; **0 integration implementation steps accepted**).

| Stage | Status | Bar — verified steps | Priority / dependency |
|---|---|---|---|
| **ANL** — Baseline and project understanding | 🟡 | `████████░░░░` 2/3 | Gate 0 / owner decisions |
| **ARCH** — Mobile architecture and ownership | 🟡 | `░░░░░░░░░░░░` 0/3 | Gate 0 / ANL |
| **ENV** — Environments and network routing | ❌ | `░░░░░░░░░░░░` 0/3 | P0 / SEC |
| **SEC** — Client and local security | ❌ | `░░░░░░░░░░░░` 0/6 | P0 / hosting and threat model |
| **AUTH** — Identity and session flows | 🟡 | `░░░░░░░░░░░░` 0/4 | P0 / CSRF, transport |
| **DEV** — Installation and device binding | ❌ | `░░░░░░░░░░░░` 0/3 | P0 / AUTH, installation identity |
| **BOOT** — Session bootstrap | ❌ | `░░░░░░░░░░░░` 0/3 | P0 / DEV |
| **ROOM** — Rooms and memberships | 🟡 | `░░░░░░░░░░░░` 0/3 | P1 / BOOT |
| **PRES** — Presence | ❌ | `░░░░░░░░░░░░` 0/3 | P1 / BOOT/ROOM |
| **INV** — Invites and QR admission | ⬜ | `░░░░░░░░░░░░` 0/3 | P1 / ROOM |
| **CALL** — CallSession control plane | ⬜ | `░░░░░░░░░░░░` 0/3 | P1 / ROOM |
| **RT** — Realtime signaling | ⬜ | `░░░░░░░░░░░░` 0/4 | P1 / AUTH/DEV/ROOM |
| **OFF** — Local hotspot without internet | 🟡 | `░░░░░░░░░░░░` 0/5 | P0 design / SEC, product decision |
| **AUD** — Audio transport and media runtime | 🟡 | `░░░░░░░░░░░░` 0/4 | P1 / OFF/CALL, chosen media |
| **PERF** — Energy, CPU/GPU, RAM/ROM efficiency | ⬜ | `░░░░░░░░░░░░` 0/5 | Cross-cutting / baseline first |
| **OBS** — Runtime observability | ⬜ | `░░░░░░░░░░░░` 0/3 | Cross-cutting / security |
| **LOG** — Logging and support | 🟡 | `░░░░░░░░░░░░` 0/3 | Cross-cutting / security |
| **TEST** — Integration, regression and physical-device gates | 🟡 | `░░░░░░░░░░░░` 0/5 | Cross-cutting / all features |
| **CI** — Build/release pipeline | ⬜ | `░░░░░░░░░░░░` 0/3 | Before release |
| **PROD** — Production and operations | ⬜ | `░░░░░░░░░░░░` 0/4 | Last gate / all |

## Mission, architecture and acceptance

- **Client deployment:** one APK / one Android application process (with a foreground service only when permitted and necessary); **not mobile microservices**. Start with clear `:app` package and interface boundaries; selectively extract `:core:network`, `:core:security`, `:core:model`, `:core:local-transport`, and later `:feature:*` Gradle library modules when tests and dependency graph justify it. Module boundaries help maintenance and testability; **modules alone do not reduce battery/CPU/RAM usage**.
- **ServerRelay:** cloud **authoritative** identity, device/session binding, rooms, membership, invite, presence, call and signaling control plane. Backend roadmap still contains unextracted Maven placeholders; a Maven module is not automatically an independently deployed service. Mobile must use public versioned APIs, not backend Java module internals.
- **Offline/hotspot:** proposed independent **ephemeral local-mode** control and direct device-to-device media; requires separate authentication, admission, lifecycle and encryption design. Local state cannot impersonate server-issued account/room authorization. Whether cloud-created rooms work offline remains **UNDECIDED**.
- **Media path:** keep audio peer-to-peer/LAN, separate from the backend REST/WSS control plane. Existing UDP PCM is only a prototype; don't route production audio through the quarantined server raw-audio WebSocket.
- **Acceptance:** per-step code + tests + measured real-device evidence + owner sign-off before ✅; CI success from either project does not imply a cross-project E2E green gate. No integration verified in this planning pass.

## Stages and steps

Each row is deliberately brief: **status + step + what exists / what closure means**. Full investigative detail and decisions are in the linked companion files.

### ANL — Baseline and project understanding  (`████████░░░░` 2/3)

- ✅ **ANL-00** — Archive inventory verified: one Android :app module, 125 main Kotlin files, 27 JVM test files; backend Maven modules and controllers inspected.
- ✅ **ANL-01** — Server ↔ Android contract discrepancy register and architecture recommendation documented.
- 🟡 **ANL-02** — Product/operations/security/offline requirements and owner sign-off are outstanding.

### ARCH — Mobile architecture and ownership  (`░░░░░░░░░░░░` 0/3)

- 🟡 **ARCH-00** — Recommended: single Android application with internal modular boundaries; owner decision pending.
- ⬜ **ARCH-01** — Define stable core/feature interfaces and dependency rules; extract Gradle modules selectively.
- ⬜ **ARCH-02** — Define online/cloud vs offline/hotspot state machines and feature ownership.

### ENV — Environments and network routing  (`░░░░░░░░░░░░` 0/3)

- ❌ **ENV-00** — Replace hardcoded cleartext LAN BASE_URL and global cleartext allowance; current configuration is unsuitable for release.
- ⬜ **ENV-01** — Define debug/staging/release endpoints, test doubles, emulator and physical-device access.
- ⬜ **ENV-02** — Define internet/LAN/captive-portal/network-switch selection, reachability, and TLS behavior.

### SEC — Client and local security  (`░░░░░░░░░░░░` 0/6)

- ❌ **SEC-00** — Implement backend-compatible CSRF preparation and same-origin cookie + masked-header pairing for public auth.
- ⬜ **SEC-01** — Protect credential and installation identity storage; exclude sensitive state from backup/transfer.
- ⬜ **SEC-02** — Bound/redact logs; disable sensitive HTTP BODY logging even in shared debug builds.
- ⬜ **SEC-03** — Define LAN pairing, discovery trust, unauthorized peer handling and media encryption/integrity.
- ⬜ **SEC-04** — Build threat model: token theft, room takeover, spoof/replay, rogue hotspot, downgrade, revocation.
- ⬜ **SEC-05** — Security acceptance tests, abuse/rate limiting and Android permission review.

### AUTH — Identity and session flows  (`░░░░░░░░░░░░` 0/4)

- 🟡 **AUTH-00** — Retrofit login/logout/refresh and token authenticator exist; real backend proof and compatibility not accepted.
- ⬜ **AUTH-01** — Registration/login/refresh/logout/current-user journeys match server CSRF/session contract.
- ⬜ **AUTH-02** — Re-login, concurrent refresh, revoke, invalid credentials and process restore reconciled.
- ⬜ **AUTH-03** — Guest/offline identity and account-switch policy frozen before implementation.

### DEV — Installation and device binding  (`░░░░░░░░░░░░` 0/3)

- ❌ **DEV-00** — Device registration path /register and required installationId must match backend contract.
- ⬜ **DEV-01** — Persistent per-installation identity vs server deviceId separation, restore/reinstall/account-switch policy.
- ⬜ **DEV-02** — Register/bind, refresh continuity, unlink/revoke, device-limit errors and migration accepted.

### BOOT — Session bootstrap  (`░░░░░░░░░░░░` 0/3)

- ❌ **BOOT-00** — Replace legacy deviceId bootstrap input with required installationId; align remaining request metadata.
- 🟡 **BOOT-01** — Startup coordinator and bootstrap repository exist; full server response and lifecycle not accepted.
- ⬜ **BOOT-02** — Bootstrap parsing, recovery, restart, identity mismatches and server regression accepted.

### ROOM — Rooms and memberships  (`░░░░░░░░░░░░` 0/3)

- 🟡 **ROOM-00** — Basic room CRUD/join/activate client paths present; production contract verification pending.
- ⬜ **ROOM-01** — Membership, private-room admission, active room, invite exceptions, roles and revocation wired.
- ⬜ **ROOM-02** — Reconciliation after reconnect, app background, user switch and offline transition accepted.

### PRES — Presence  (`░░░░░░░░░░░░` 0/3)

- ❌ **PRES-00** — Replace legacy state enum response with server availability/activity/preference contract.
- 🟡 **PRES-01** — Heartbeat, lifecycle coordinator and room poller exist; measure and gate network load.
- ⬜ **PRES-02** — Implement foreground/activity policy, server projections and event-driven reconciliation.

### INV — Invites and QR admission  (`░░░░░░░░░░░░` 0/3)

- ⬜ **INV-00** — Server-side invite/QR creation, revoke, expiry and idempotency contract mapped to mobile.
- ⬜ **INV-01** — Join via invite/deep link/QR with authorization and duplicate/expired token UX.
- ⬜ **INV-02** — Offline invite/pairing distinctions and cross-mode tests accepted.

### CALL — CallSession control plane  (`░░░░░░░░░░░░` 0/3)

- ⬜ **CALL-00** — Call create/join/leave/end/current REST V1 mapping and app state machine.
- ⬜ **CALL-01** — Call authorization, transitions, race/lost-request and device lifecycle recovery.
- ⬜ **CALL-02** — Call control vs media independence and reconciliation tests accepted.

### RT — Realtime signaling  (`░░░░░░░░░░░░` 0/4)

- ⬜ **RT-00** — Native authenticated WSS client with bearer + X-AudioShare-Device-Id handshake.
- ⬜ **RT-01** — Envelope/event subscription, snapshot reconciliation, deduplication and version checks.
- ⬜ **RT-02** — Reconnect backoff, expiry/revocation, HTTP 429/503 Retry-After, resubscribe and cancellation.
- ⬜ **RT-03** — Foreground/background WebSocket lifecycle, battery limits and real server acceptance.

### OFF — Local hotspot without internet  (`░░░░░░░░░░░░` 0/5)

- 🟡 **OFF-00** — UDP discovery/receiver/sender prototype exists; it is not an authenticated offline protocol.
- ⬜ **OFF-01** — Choose offline identity, pairing/trust, hosting and room semantics; cloud independence frozen.
- ⬜ **OFF-02** — Define discovery and hotspot/LAN failover, IP churn, host departure and partition handling.
- ⬜ **OFF-03** — Offline-safe local media protocol, key lifecycle and resource quotas accepted.
- ⬜ **OFF-04** — Cloud-return synchronization rules; never silently reconcile untrusted local session as server truth.

### AUD — Audio transport and media runtime  (`░░░░░░░░░░░░` 0/4)

- 🟡 **AUD-00** — PCM capture/playback, UDP transport and foreground service prototype exist; not production accepted.
- ⬜ **AUD-01** — Decide codec, bitrate, quality, latency, speaker topology, jitter/reorder, congestion and FEC.
- ⬜ **AUD-02** — Bound allocation, buffers, sockets, thread count and media cleanup across lifecycle states.
- ⬜ **AUD-03** — Encrypted/authenticated audio and real-device latency/dropout/load acceptance.

### PERF — Energy, CPU/GPU, RAM/ROM efficiency  (`░░░░░░░░░░░░` 0/5)

- ⬜ **PERF-00** — Establish idle, browsing, hotspot, streaming and background performance baselines on real phones.
- ⬜ **PERF-01** — Presence polling / WSS / discovery cadence policy with exponential backoff, batching, no busy loops.
- ⬜ **PERF-02** — Audio ring buffer reuse, bounded allocation, efficient codecs and network-energy profile.
- ⬜ **PERF-03** — Memory leak, heap-growth, disk-cache/retention, cancellation, service/socket teardown gates.
- ⬜ **PERF-04** — Define per-device performance budgets and soak tests; avoid claiming universal fixed RAM/CPU ceilings.

### OBS — Runtime observability  (`░░░░░░░░░░░░` 0/3)

- ⬜ **OBS-00** — Privacy-safe local operational events and crash diagnostics schema.
- ⬜ **OBS-01** — Measure call quality, reconnects, startup, battery/CPU/memory/network with opt-in telemetry policy.
- ⬜ **OBS-02** — Owner-approved health/error reporting, bounded retention and redacted support bundles.

### LOG — Logging and support  (`░░░░░░░░░░░░` 0/3)

- 🟡 **LOG-00** — AppLogger abstraction exists; review effective platform logs and sensitive data handling.
- ⬜ **LOG-01** — Unified typed events, scoped correlation IDs and debug/release filtering.
- ⬜ **LOG-02** — Support troubleshooting/export policy without credentials/audio/content leakage.

### TEST — Integration, regression and physical-device gates  (`░░░░░░░░░░░░` 0/5)

- 🟡 **TEST-00** — JVM/MockWebServer suite exists; fixtures may encode the old incompatible backend wire contract.
- ⬜ **TEST-01** — Contract-derived fixtures/OpenAPI checks + real ServeRelay docker integration journeys.
- ⬜ **TEST-02** — Emulator and multi-phone hotspot, switching, revoked session, airplane mode, DNS/TLS tests.
- ⬜ **TEST-03** — Battery/CPU/RAM/ROM soak, thermal, cold start, repeated connect/disconnect and leak checks.
- ⬜ **TEST-04** — Separate unit, integration, instrumentation and release gates; record evidence before ✅.

### CI — Build/release pipeline  (`░░░░░░░░░░░░` 0/3)

- ⬜ **CI-00** — Reproducible Gradle wrapper/dependency/toolchain and security checks.
- ⬜ **CI-01** — CI matrix, Android lint/static analysis, tests, SBOM/dependency audit and secrets scan.
- ⬜ **CI-02** — Signing, distribution, staged release, rollback and mobile↔server compatibility gate.

### PROD — Production and operations  (`░░░░░░░░░░░░` 0/4)

- ⬜ **PROD-00** — Choose provider, domains, TLS/WSS edge, staging/prod split and backend database/monitoring.
- ⬜ **PROD-01** — Production privacy/consent, retention, telemetry and user support policy approved.
- ⬜ **PROD-02** — Android 24–36 supported-device matrix; Android 17 local-network permission migration plan.
- ⬜ **PROD-03** — End-to-end online/offline/reconnect release acceptance and rollback rehearsal.

## Sequencing and acceptance boundaries

**Gate 0: owner decisions** → **Gate 1: release-safe transport + CSRF + token/installation identity** → **Gate 2: real login → `/devices/register` → `/session/bootstrap`** → **Gate 3: rooms/presence/invites/calls + WSS** → **Gate 4: audited offline/hotspot and media** → **Gate 5: real-device energy/leak/security tests, CI and release**. Media/energy instrumentation is designed from Gate 0, not deferred until the end; development streams may run in parallel after shared contracts freeze. See [dependency tree](SVREMOBILE_ROADMAP_DEPENDENCIES_TREE.md).

**Quality gates (proposed, measurable thresholds must be approved):** startup and idle vs active-stream baseline, average/peak CPU, mAh/hour and thermal impact, PSS/Java/native heap after 30/120 minute soak, disk growth by active-day, bounded audio queues/packet sizes and reconnect counts; 1/2/3+ phones with airplane, hotspot, captive LAN, OS kill, app background, network switch, and token expiry. Avoid arbitrary numerical promises before recording baseline phone models and expected quality.

**Build status:** static inspection only. Android SDK/device-based build, Gradle tests, real ServeRelay integration, hardware measurements and release acceptance were **not** executed for this documentation patch. Verify before push on the owner's toolchain. No server source was modified.

## Official platform references

- Android modularization: https://developer.android.com/topic/modularization
- Android network security: https://developer.android.com/privacy-and-security/security-config
- Foreground-service types/restrictions: https://developer.android.com/develop/background-work/services/fgs/service-types
- Local-network access (Android 17/target 37): https://developer.android.com/privacy-and-security/local-network-permission
- Memory profiling: https://developer.android.com/studio/profile/capture-heap-dump
