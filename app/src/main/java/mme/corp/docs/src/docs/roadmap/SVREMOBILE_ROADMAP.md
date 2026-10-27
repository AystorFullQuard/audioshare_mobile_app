# SvReMobile — Android / ServerRelay integration roadmap

**Source baseline:** SvReMobile archive supplied for **27 October 2026**, ServerRelay archive supplied for **26 October 2026**. The older 26 October 14:23 baseline statement is superseded. R01–R04 decisions remain accepted.
**Current integration work (GAP-00–02):** shared endpoint policy (GAP-00), correct ServerRelay registration route and required stable installation ID (GAP-01/02). Source and contract tests updated; Gradle/Android/device acceptance pending.
**Backend contract authority:** actual `ServerRelay/boot/src/main/java/sv/re/controller/`, `ServerRelay/dto/src/main/java/`, `ServerRelay/docs/src/main/docs/bootstrap/DEVICE_BOOTSTRAP_JOINT_CONTRACT_V1.md`, `security/SEC05_CSRF_CLIENT_CONTRACT.md`, and `realtime/` documents. **Do not use old mobile mock fixtures as proof of compatibility.**
**Cross-references:** [integration GAP register](SVREMOBILE_SERVERRELAY_INTEGRATION_GAPS.md) · [dependency table](SVREMOBILE_ROADMAP_DEPENDENCIES_TREE.md) · [architecture and security decisions](SVREMOBILE_ARCHITECTURE_SECURITY_DECISIONS.md) · [entity contract](../contract/SVREMOBILE_SERVERRELAY_ENTITY_CONTRACT.md).

## Status legend

| Symbol | Meaning | Rule |
|---|---|---|
| ✅ | Verified / accepted for the *specific described scope* | Only static analysis artifacts receive ✅ in this planning revision; later code steps require acceptance evidence and owner approval. |
| 🟡 | Partially present, under review, or decision pending | Existing classes do **not** demonstrate interoperability or release acceptance. |
| ⬜ | Planned | No accepted implementation evidence for this step. |
| ❌ | Confirmed blocker/incompatible candidate behavior | Static code-versus-contract mismatch or release-insecure configuration has been identified. |

Stage ✅ requires every mandatory child ✅. **Status bars count ✅ steps only, not estimated effort, and do not claim tests were run.** Current stage closure is intentionally conservative.

## Overall completion

**Verified planning/code-acceptance items:** `4/104` (3 ANL planning steps + GAP-20 product-scope decision; **0 integration implementation steps verified/accepted**; GAP-00, GAP-01, GAP-02 and GAP-22 implemented pending Android verification). The remaining security design tasks, implementation and physical-device gates are **not** complete.

| Stage | Status | Bar — verified steps | Priority / dependency |
|---|---|---|---|
| **GAP** — Cross-project integration blockers | 🟡 | `█░░░░░░░░░░░` 1/23 | P0 / registration, then auth/bootstrap acceptance |
| **ANL** — Baseline and project understanding | ✅ | `████████████` 3/3 | Gate 0 product answers recorded; security design continues |
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
| **VID** — Video calls and in-call camera | ⬜ | `░░░░░░░░░░░░` 0/4 | P2 implementation / CALL, secure media |
| **SYNC** — Connectivity, policy and session recovery | ⬜ | `░░░░░░░░░░░░` 0/4 | P0 design / AUTH, OFF, ENV |
| **PERF** — Energy, CPU/GPU, RAM/ROM efficiency | ⬜ | `░░░░░░░░░░░░` 0/5 | Cross-cutting / baseline first |
| **OBS** — Runtime observability | ⬜ | `░░░░░░░░░░░░` 0/3 | Cross-cutting / security |
| **LOG** — Logging and support | 🟡 | `░░░░░░░░░░░░` 0/3 | Cross-cutting / security |
| **TEST** — Integration, regression and physical-device gates | 🟡 | `░░░░░░░░░░░░` 0/5 | Cross-cutting / all features |
| **CI** — Build/release pipeline | ⬜ | `░░░░░░░░░░░░` 0/3 | Before release |
| **PROD** — Production and operations | ⬜ | `░░░░░░░░░░░░` 0/4 | Last gate / all |

## Dependency and parallel work table

| Stage / workstream | Can be done now? | Parallel with | Blocked by |
|---|---|---|---|
| **GAP-22** — safe mobile entity snapshots | **Implemented; verify** | GAP-06 auth DTOs; DEV/BOOT DTO alignment; offline design | Android Gradle tests; mapping/E2E validation later |
| **GAP-00 / ENV / SEC** shared network safety | **Implemented; verify** | Docs, tests, logging audit | Gradle tests, production TLS setup |
| **GAP-01/02 / DEV** device registration + install identity | **Implemented; verify** | GAP-06 auth, GAP-03 bootstrap DTO | Gradle/JUnit + Docker authenticated registration; server-issued ID/session binding |
| **GAP-06 / AUTH** CSRF/login/refresh | **Yes** | Device DTO work, offline threat model | Server CSRF policy + Docker E2E acceptance |
| **GAP-03/04 / BOOT** bootstrap request/response | **Yes, contract work** | CSRF, security, tests | Auth login to validate E2E |
| **GAP-05 / PRES / ROOM** presence model | **Yes** | DEV/BOOT DTOs, OBS/PERF | Bootstrap for end-to-end proof |
| **GAP-09..10 / INV / CALL / RT** | **Design/interfaces now** | Offline pairing/security | AUTH + DEV + BOOT + ROOM accepted |
| **GAP-11,15..18 / OFF / AUD / SYNC** | **Threat model/prototype now** | Online contract work, perf baselines | Approved grant, pairing and crypto design |
| **GAP-08,12..13,19,21 / TEST / PERF / CI** | **Harness and audit now** | All workstreams | Android SDK/devices; finished flows for sign-off |

Full stage dependencies: [dependency tree](SVREMOBILE_ROADMAP_DEPENDENCIES_TREE.md). **Parallel** means design/coding can proceed independently; no dependent acceptance is implied.

## Mission, architecture and acceptance

- **Client deployment:** one APK / one Android application process (with a foreground service only when permitted and necessary); **not mobile microservices**. Start with clear `:app` package and interface boundaries; selectively extract `:core:network`, `:core:security`, `:core:model`, `:core:local-transport`, and later `:feature:*` Gradle library modules when tests and dependency graph justify it. Module boundaries help maintenance and testability; **modules alone do not reduce battery/CPU/RAM usage**.
- **ServerRelay:** cloud **authoritative** identity, device/session binding, rooms, membership, invite, presence, call and signaling control plane. Backend roadmap still contains unextracted Maven placeholders; a Maven module is not automatically an independently deployed service. Mobile must use public versioned APIs, not backend Java module internals.
- **Offline/hotspot:** **accepted requirement:** two or more phones discover and exchange calls/one-way audio on local hotspot/LAN **with no internet and no cloud calls**. QR/PIN pairing, explicit host approval, remembered trusted devices and mandatory authenticated encryption are required. **Only an installation with a previously successful ServerRelay login may host/create offline rooms; historical login alone is not permanent authorization.** Offline capability uses bounded, device-bound local grants and distinct local room authority. No first-ever offline hosting, no implicit server privilege, and no automatic migration from local rooms to cloud rooms. Grant TTL, account sign-out/revocation and trusted rejoin behavior require a security-design decision before implementation.
- **Media path:** first accept **1:1 low-latency interactive audio calls**, then **one-way host→two listeners audio share**, with unauthenticated guests admitted *only to one-way audio share* after explicit manual host approval. Video calls and **camera upgrade during audio calls** remain a separate later stage. Benchmark and select an encrypted media stack (WebRTC/Opus/DTLS-SRTP is a candidate, not an approved dependency); no production audio through the backend raw-audio WebSocket. **V1 media is nearby hotspot/LAN only**, not remote cross-internet/NAT calls; TURN/SFU is deferred.
- **Local-cloud transition:** offline media and decisions continue while cloud is unreachable; connectivity callbacks may prompt bounded, authenticated policy/session checks and token renewal **only after real server reachability**. Apply offline cached/local policy when no cloud is reachable; never imply a revoked account has been verified offline. Reconnection cannot silently trust a local room as an online room or drop the working local call. **Background audio:** continue active calls with screen off/app background when Android permissions and foreground-service restrictions allow, with an active user-visible notification, predictable cleanup and battery limits; do not assume background camera capture is permitted. **Development & distribution:** initial backend on local Docker Compose; three physical Android devices (model/OS details pending) for tests; Google Play distribution later. **Privacy:** local, redacted, bounded diagnostics first, no recording or uploading media.
- **Acceptance:** per-step code + tests + measured real-device evidence + owner sign-off before ✅; CI success from either project does not imply a cross-project E2E green gate. No integration verified in this planning pass.

## Stages and steps

Each row is deliberately brief: **status + step + what exists / what closure means**. Full investigative detail and decisions are in the linked companion files.

### GAP — Integration blockers (`█░░░░░░░░░░░` 1/23)

**GAP-00 🟡** network policy · **GAP-01 🟡** registration route · **GAP-02 🟡** installation identity · **GAP-22 🟡** entity models · **GAP-03–19/21** open · **GAP-20 ✅** scope accepted. [GAP register](SVREMOBILE_SERVERRELAY_INTEGRATION_GAPS.md).


### ANL — Baseline and project understanding  (`████████████` 3/3)

- ✅ **ANL-00** — Archive inventory verified: one Android :app module, 125 main Kotlin files, 27 JVM test files; backend Maven modules and controllers inspected.
- ✅ **ANL-01** — Server ↔ Android contract discrepancy register and architecture recommendation documented.
- ✅ **ANL-02** — Eight initial answers plus R01–R04 explicitly confirmed: prior server login for offline host, nearby LAN-first, background audio where permitted, 1:1 then host+two listeners with manually approved share-mode guests. Phone models/OS versions are deferred to hardware TEST gate; this is planning acceptance only.

### ARCH — Mobile architecture and ownership  (`░░░░░░░░░░░░` 0/3)

- 🟡 **ARCH-00** — Design recommendation recorded: one modular Android app, not mobile microservices; Gradle extraction timing still subject to approval.
- ⬜ **ARCH-01** — Define stable core/feature interfaces and dependency rules; extract Gradle modules selectively.
- ⬜ **ARCH-02** — Define online/cloud vs offline/hotspot state machines and feature ownership.

### ENV — Environments and network routing  (`░░░░░░░░░░░░` 0/3)

- 🟡 **ENV-00** — GAP-00 now provides per-variant URL validation and release cleartext denial; verify tests/build, TLS endpoint and debug-device routing.
- 🟡 **ENV-01** — Docker Compose route can be set with `-PserverRelayDebugBaseUrl=http://<private-LAN-IP>:8090/`; three-device execution and staging TLS pending.
- ⬜ **ENV-02** — Define internet/LAN/captive-portal/network-switch selection, reachability, and TLS behavior.

### SEC — Client and local security  (`░░░░░░░░░░░░` 0/6)

- ❌ **SEC-00** — Implement backend-compatible CSRF preparation and same-origin cookie + masked-header pairing for public auth.
- ⬜ **SEC-01** — Protect credential and installation identity storage; exclude sensitive state from backup/transfer.
- 🟡 **SEC-02** — GAP-00 removes OkHttp BODY logging; legacy application-layer authentication logs can still expose token prefixes, account IDs and error bodies (LOG security work is still open).
- ⬜ **SEC-03** — Design QR/PIN identity binding, explicit host approval, remembered-device rejoin policy and authenticated media encryption/anti-replay.
- ⬜ **SEC-04** — Build threat model: token theft, room takeover, spoof/replay, rogue hotspot, downgrade, revocation.
- ⬜ **SEC-05** — Security acceptance tests, abuse/rate limiting and Android permission review.

### AUTH — Identity and session flows  (`░░░░░░░░░░░░` 0/4)

- 🟡 **AUTH-00** — Retrofit login/logout/refresh and token authenticator exist; real backend proof and compatibility not accepted.
- ⬜ **AUTH-01** — Registration/login/refresh/logout/current-user journeys match server CSRF/session contract.
- ⬜ **AUTH-02** — Re-login, concurrent refresh, revoke, invalid credentials and process restore reconciled.
- ⬜ **AUTH-03** — Enforce prior successful account login on *this installation* before offline room creation; implement locally bounded, device-bound offline host grant and its sign-out/expiry/revocation policies. Only manually approved share-mode guests may join without account; no guest can host.

### DEV — Installation and device binding  (`░░░░░░░░░░░░` 0/3)

- 🟡 **DEV-00** — `/api/v1/devices/register` and stable `installationId` match backend request; Android/Docker contract tests pending.
- ⬜ **DEV-01** — Persistent per-installation identity vs server deviceId separation, restore/reinstall/account-switch policy.
- ⬜ **DEV-02** — Register/bind, refresh continuity, unlink/revoke, device-limit errors and migration accepted.

### BOOT — Session bootstrap  (`░░░░░░░░░░░░` 0/3)

- ❌ **BOOT-00** — Replace legacy deviceId bootstrap input with required installationId; align remaining request metadata.
- 🟡 **BOOT-01** — Startup coordinator and bootstrap repository exist; full server response and lifecycle not accepted.
- ⬜ **BOOT-02** — Bootstrap parsing, recovery, restart, identity mismatches and server regression accepted.

### ROOM — Rooms and memberships  (`░░░░░░░░░░░░` 0/3)

- 🟡 **ROOM-00** — Basic room CRUD/join/activate client paths present; production contract verification pending.
- ⬜ **ROOM-01** — Membership, private admission, roles and revocation wired; share-mode guest is locally host-approved only, never equivalent to authenticated backend membership.
- ⬜ **ROOM-02** — Reconciliation after reconnect, app background, user switch and offline transition accepted.

### PRES — Presence  (`░░░░░░░░░░░░` 0/3)

- ❌ **PRES-00** — Replace legacy state enum response with server availability/activity/preference contract.
- 🟡 **PRES-01** — Heartbeat, lifecycle coordinator and room poller exist; measure and gate network load.
- ⬜ **PRES-02** — Implement foreground/activity policy, server projections and event-driven reconciliation.

### INV — Invites and QR admission  (`░░░░░░░░░░░░` 0/3)

- ⬜ **INV-00** — Server-side invite/QR creation, revoke, expiry and idempotency contract mapped to mobile.
- ⬜ **INV-01** — Join via invite/deep link/QR with authorization and duplicate/expired token UX.
- ⬜ **INV-02** — Manual host approval for guests **only in one-way audio share** for v1; no guest interactive call, video access or room creation. Separate local QR/PIN pairing tokens from backend invites and roles.

### CALL — CallSession control plane  (`░░░░░░░░░░░░` 0/3)

- ⬜ **CALL-00** — Map online call create/join/leave/end REST V1 and independent LAN control; V1 nearby media only; model 1:1 audio, one-way host+two listeners, later video/camera upgrade as separate modes.
- ⬜ **CALL-01** — Per-mode role checks: account-bound host, 1:1 authenticated interactive audio, manually host-approved guests only on one-way share; lifecycle and call transitions.
- ⬜ **CALL-02** — Call control vs media independence and reconciliation tests accepted.

### RT — Realtime signaling  (`░░░░░░░░░░░░` 0/4)

- ⬜ **RT-00** — Native authenticated WSS client with bearer + X-AudioShare-Device-Id handshake.
- ⬜ **RT-01** — Envelope/event subscription, snapshot reconciliation, deduplication and version checks.
- ⬜ **RT-02** — Reconnect backoff, expiry/revocation, HTTP 429/503 Retry-After, resubscribe and cancellation.
- ⬜ **RT-03** — Foreground/background WebSocket lifecycle, battery limits and real server acceptance.

### OFF — Local hotspot without internet  (`░░░░░░░░░░░░` 0/5)

- 🟡 **OFF-00** — UDP discovery/receiver/sender prototype exists; it is not an authenticated offline protocol.
- ⬜ **OFF-01** — Implement cloud-free local rooms/discovery restricted to previously authenticated installations with **bounded offline host grants**, four mandatory security controls and independent local room IDs; specify logout/account-switch/expired-grant behavior.
- ⬜ **OFF-02** — Build discovery over same-LAN hotspot (NSD) with Wi-Fi Direct/local-only-hotspot fallback where supported; handle IP churn, host loss and partitions.
- ⬜ **OFF-03** — Independently authorized local control/events, forward-secret encrypted media, anti-replay, key rotation, peer quotas, trust invalidation, host+two-listener cap and guest restrictions.
- ⬜ **OFF-04** — Cloud-return checks policy/session/refresh automatically when reachable; local events do not become cloud events without explicitly approved mapping.

### AUD — Audio transport and media runtime  (`░░░░░░░░░░░░` 0/4)

- 🟡 **AUD-00** — PCM capture/playback, UDP transport and foreground service prototype exist; not production accepted.
- ⬜ **AUD-01** — Benchmark secure Opus/WebRTC vs bespoke media for nearby 1:1 low-latency interactive audio, then host→two-listener share; bound jitter, buffers, feedback, CPU, thermal and power usage.
- ⬜ **AUD-02** — Bound allocation, buffers, sockets, thread count and media cleanup across lifecycle states.
- ⬜ **AUD-03** — Test authenticated 1:1 audio then one-way host+two listeners/guest admission on three Android devices with internet off, and while online state changes.

### VID — Video calls and in-call camera  (`░░░░░░░░░░░░` 0/4)

- ⬜ **VID-00** — Later milestone (not v1 audio acceptance): define nearby video call and camera-on upgrade during an active audio call, with role/permissions and signaling state machine.
- ⬜ **VID-01** — Select efficient camera capture/encoder/media transport with encrypted A/V synchronization and audio priority.
- ⬜ **VID-02** — Camera/mic privacy, background/screen-off restrictions, foreground-service types, pause/mute/restart and network/thermal adaptation.
- ⬜ **VID-03** — Physical-device low-latency audio preservation during video toggle, 30/120-minute thermal/battery/heap tests.

### SYNC — Connectivity, policy and session recovery  (`░░░░░░░░░░░░` 0/4)

- ⬜ **SYNC-00** — Online/offline/transition state machine; LAN transport stays bound locally while validating actual internet/server reachability.
- ⬜ **SYNC-01** — On reachable-server transition, serialize authenticated policy/session/bootstrap checks and token refresh, respecting Retry-After and backoff.
- ⬜ **SYNC-02** — No background busy polling: connectivity callbacks + lifecycle-aware refresh scheduling; prior-login offline authorization uses explicit, bounded device-bound grants, fail-closed cloud privileges and trusted peer state.
- ⬜ **SYNC-03** — Reconnect and conflict resolution never interrupts local calls or treats a locally admitted guest as cloud-authorized; revoke cloud abilities when rejected.

### PERF — Energy, CPU/GPU, RAM/ROM efficiency  (`░░░░░░░░░░░░` 0/5)

- ⬜ **PERF-00** — Baseline idle, hotspot, 1:1 interactive audio, one-way host+two listeners, screen-off/background audio and later camera on three phones; device models and Android versions still to be supplied.
- ⬜ **PERF-01** — Presence polling / WSS / discovery cadence policy with exponential backoff, batching, no busy loops.
- ⬜ **PERF-02** — Audio ring buffer reuse, bounded allocation, efficient codecs and network-energy profile.
- ⬜ **PERF-03** — Memory leak, heap-growth, disk-cache/retention, cancellation, service/socket teardown gates.
- ⬜ **PERF-04** — Define per-device performance budgets and soak tests; avoid claiming universal fixed RAM/CPU ceilings.

### OBS — Runtime observability  (`░░░░░░░░░░░░` 0/3)

- ⬜ **OBS-00** — Local-only redacted crash/event metrics first; bounded retention, no raw media, tokens, QR secret or identifying device data.
- ⬜ **OBS-01** — Measure quality/reconnect/startup/battery/CPU/RAM/disk locally; remote/opt-in telemetry only after a separate future privacy decision.
- ⬜ **OBS-02** — On-device diagnostics display/export with user action, storage cap and verified redaction; no automatic upload at this stage.

### LOG — Logging and support  (`░░░░░░░░░░░░` 0/3)

- 🟡 **LOG-00** — AppLogger abstraction exists; review effective platform logs and sensitive data handling.
- ⬜ **LOG-01** — Unified typed events, scoped correlation IDs and debug/release filtering.
- ⬜ **LOG-02** — Support troubleshooting/export policy without credentials/audio/content leakage.

### TEST — Integration, regression and physical-device gates  (`░░░░░░░░░░░░` 0/5)

- 🟡 **TEST-00** — JVM/MockWebServer suite exists; fixtures may encode the old incompatible backend wire contract.
- ⬜ **TEST-01** — Contract-derived fixtures/OpenAPI checks + real ServeRelay docker integration journeys.
- ⬜ **TEST-02** — Three-phone (models and Android versions TBD) hotspot/no-internet/airplane/no-cellular/guest-admission/host+2/background locked-screen/revocation/network-switching/DNS/TLS matrix.
- ⬜ **TEST-03** — Battery/CPU/RAM/ROM soak, thermal, cold start, repeated connect/disconnect and leak checks.
- ⬜ **TEST-04** — Separate unit, integration, instrumentation and release gates; record evidence before ✅.

### CI — Build/release pipeline  (`░░░░░░░░░░░░` 0/3)

- ⬜ **CI-00** — Reproducible Gradle wrapper/dependency/toolchain and security checks.
- ⬜ **CI-01** — CI matrix, Android lint/static analysis, tests, SBOM/dependency audit and secrets scan.
- ⬜ **CI-02** — Play Console internal/closed testing, Android App Bundle signing, test tracks, staged release and mobile↔server compatibility gate.

### PROD — Production and operations  (`░░░░░░░░░░░░` 0/4)

- ⬜ **PROD-00** — Run ServerRelay initially via local Docker Compose; later select TLS/WSS production provider/region/domain, data/backup/monitoring.
- ⬜ **PROD-01** — Google Play microphone/camera/FGS/data-safety disclosure and privacy decisions approved; no recording in initial product.
- ⬜ **PROD-02** — Android 24–36 supported-device matrix; Android 17 local-network permission migration plan.
- ⬜ **PROD-03** — Google Play internal track → closed track → production, three-phone engineering gates and rollback; if a new personal Play developer account, recruit at least 12 opted-in closed testers for 14 consecutive days before production access.

## Sequencing and acceptance boundaries

**Gate 0: all 12 owner product answers recorded (Q01–Q08, R01–R04); remaining detailed offline-grant/threat-model security design tracked in SEC** → **Gate 1: release-safe transport + CSRF + token/installation identity** → **Gate 2: real login → `/devices/register` → `/session/bootstrap`** → **Gate 3: rooms/presence/invites/calls + WSS** → **Gate 4: audited offline/hotspot, secure low-latency audio, SYNC transitions** → **Gate 5: video upgrades and real-device energy/leak/security tests, CI and release**. Media/energy instrumentation is designed from Gate 0, not deferred until the end; development streams may run in parallel after shared contracts freeze. See [dependency tree](SVREMOBILE_ROADMAP_DEPENDENCIES_TREE.md).

**Quality gates (proposed, measurable thresholds must be approved):** startup and idle vs active-stream baseline, average/peak CPU, mAh/hour and thermal impact, PSS/Java/native heap after 30/120 minute soak, disk growth by active-day, bounded audio queues/packet sizes and reconnect counts; 1/2/3+ phones with airplane, hotspot, captive LAN, OS kill, app background, network switch, and token expiry. Avoid arbitrary numerical promises before recording baseline phone models and expected quality.

**Verification:** policy Kotlin syntax/smoke tests and documentation/source checks run locally where possible; full Android Gradle build, instrumentation, live ServerRelay and physical-device acceptance remain **unverified**. No ServerRelay source changed.

## Official platform references

- Android modularization: https://developer.android.com/topic/modularization
- Android network security: https://developer.android.com/privacy-and-security/security-config
- Foreground-service types/restrictions: https://developer.android.com/develop/background-work/services/fgs/service-types
- Local-network access (Android 17/target 37): https://developer.android.com/privacy-and-security/local-network-permission
- Memory profiling: https://developer.android.com/studio/profile/capture-heap-dump

## Product direction decisions from owner (2026-10-26)

| Decision | Outcome | Remaining qualification |
|---|---|---|
| Local discovery and media | **Accepted:** hotspot/LAN works fully without cloud/internet | Prior successful online login on this installation required to host; grant lifetime/sign-out policy still to design |
| Pairing/security | **Accepted:** QR/PIN + host approval + remembered devices + media encryption | Rejoin duration/revocation/key schedule requires SEC design |
| Hosting | **Accepted for dev:** local/Docker Compose | Production provider deferred |
| Distribution/tests | **Accepted:** Google Play later; 3 physical Android devices now | Device models/Android versions needed for matrix |
| Media | **Accepted:** 1:1 audio → one-way host+two listeners with approved share-mode guests; video/camera later | Internet-remote calls explicitly deferred |
| Cloud return | **Accepted:** local events independent; resume policy/session/token checks when server reachable | Offline authorization and conflict mapping require SEC/SYNC design |
| Host login/guests | **Accepted:** prior-login host/creator; manually approved guests permitted **only** on one-way audio share | Local host-grant expiry/logout and guest rejoin rules to design |
| Privacy | **Accepted initial scope:** bounded local diagnostics only | Later opt-in remote telemetry is separate decision |
| Background media | **Accepted:** maintain active audio with screen locked/app background when Android permits | User-visible notification, foreground service and OEM/device evidence needed |
| Device test matrix | **Accepted:** three different Android devices | Exact phone model/OS versions to be supplied before TEST-02 |

**Security truthfulness:** no network == no new server token, server policy or server revocation confirmation. Prior online login is a confirmed *eligibility prerequisite*, not perpetual access: bounded device-bound offline entitlement and local trust enforcement still require a reviewed technical contract. Check on reconnect; when rejected, disable **cloud** privileges without breaking unrelated trusted LAN media. Offline identities and offline call-control state must be explicitly distinct from server room/account representations.

**Platform review:** Android LocalOnlyHotspot and Wi-Fi Direct/NSD enable internet-independent discovery subject to device/API/permission constraints. Android 17/target SDK 37+ requires explicit local network access or system-mediated picker; camera/microphone foreground service restrictions and Google Play declarations require physical-device evidence.

**Play Console planning warning:** Google currently requires Android API 36+ for new mobile app submissions (since 31 August 2026); `targetSdk=36` meets this particular threshold. For *personal* Play developer accounts created after 13 November 2023, production eligibility additionally requires at least 12 closed-test opt-ins for 14 continuous days, not merely three test phones. See https://support.google.com/googleplay/android-developer/answer/11926878 and https://support.google.com/googleplay/android-developer/answer/14151465 .
