# SvReMobile — staged implementation dependency tree

**Source:** [SVREMOBILE_ROADMAP.md](SVREMOBILE_ROADMAP.md). **Legend:** ✅ verified planning, 🟡 partially present/decision pending, ⬜ planned, ❌ confirmed blocker. These are **not** executable dependencies and the graph is not a claim of delivered features.

```text
GAP 🟡 1/22 scope/status items closed; GAP-00 code present, Android acceptance pending
├─ GAP-00 🟡 central endpoint, build policy, no release cleartext / payload logger
├─ GAP-01..19,21 🟡/❌/⬜ server compatibility, security and testing issues
└─ GAP-20 ✅ remote-internet media deferred (scope decision, not code)
ANL ✅ static inventory + server contract gap register
└─ ANL-02 ✅ Q01–Q08 + R01–R04 product decisions recorded (code still unimplemented)
   ├─ ARCH 🟡 one Android app, module boundary choice
   │  ├─ ENV-00 🟡 centralized per-variant endpoint policy; Android test and TLS endpoint pending
   │  │  └─ SEC-00 ❌ public auth CSRF proof missing
   │  │     └─ AUTH 🟡 align login / refresh / stored session
   │  │        └─ DEV-00 ❌ endpoint + installationId mismatch
   │  │           └─ BOOT-00 ❌ bootstrap installationId mismatch
   │  │              ├─ ROOM 🟡 → INV ⬜ → CALL ⬜
   │  │              ├─ PRES ❌ → realtime-backed member updates
   │  │              └─ RT ⬜ → WebSocket auth, subscriptions, reconnect
   │  └─ SEC-01..05 ⬜ client credential and local network security
   ├─ OFF-01 ⬜ prior-login offline host grant + 4 accepted security controls; TTL/logout semantics to design
   │  └─ SEC-03 ⬜ local pairing and encryption
   │     └─ OFF-02..04 ⬜ local discovery, trusted rooms and cloud-independent events
   │        ├─ AUD ⬜ 1:1 authenticated mic call → host+2 one-way share (guest approval only here)
   │        │  └─ VID ⬜ later nearby video call / live audio-to-camera upgrade
   │        └─ SYNC ⬜ reachability, policy/session refresh on return (never when unreachable)
   ├─ BG/AUDIO policy ⬜ active audio with screen locked only where Android permits; notification + FGS cleanup
   ├─ PERF ⬜ baseline before implementation; gate all high-frequency work
   ├─ OBS/LOG ⬜ bounded, redacted **local-only** diagnostics first
   ├─ TEST/CI ⬜ tests and gates continuously, not only at launch
   └─ PROD ⬜ Docker Compose now; later cloud, Google Play testing/rollout, TLS, support
```

## Dependency table

| Work / dependency | Can be done now | Can run in parallel with | Blocked by (for acceptance) |
|---|---|---|---|
| GAP-00 — network/config foundation | Implemented, verification pending | LOG/SEC audit, TEST, docs | Android Gradle tests, production HTTPS URL |
| GAP-06 — CSRF/auth sessions | Yes | GAP-01..04 DTO design, OFF threat model | Reachable server + CSRF cookie/header acceptance |
| GAP-01..04 — installation, device, bootstrap | Yes, contract design | GAP-06 implementation, tests | Auth for successful E2E bootstrap |
| GAP-05 — presence model | Yes | GAP-01..04, OBS/PERF | Bootstrap and server response contract |
| GAP-09..10 — Invite/Call/Realtime | Interface work now | OFF transport experiments | Working auth/device/room control plane |
| GAP-11,15..18 — offline media and sync | Threat model/prototype now | Backend GAP workstreams, PERF | Offline grant, key agreement and pairing designs |
| GAP-08,12..13,19,21 — quality/release gates | Test harness now | Every workstream | Three phones, live backend, accepted media modes |

The dependency matrix permits independent development, **not** premature completion claims.

## Gates and sequencing

| Gate | Must be explicitly decided or verified | Output | Status |
|---|---|---|---|
| **0 — Product/security** | Q01–Q08 and R01–R04 accepted: prior-login host, LAN-only v1, background audio where legal, 1:1 then host+2/guest sharing. Phone models deferred; complete SEC threat model before media coding. | ✅ owner decision register; 🟡 security design/threat model | 🟡 |
| **1 — Infrastructure/auth** | HTTPS/WSS or approved debug-only LAN exception, CSRF, token storage/session cleanup. | Real login/refresh/logout against matching ServerRelay build. | ❌ |
| **2 — Bind & bootstrap** | Local `installationId`, `/devices/register`, frozen `/session/bootstrap` schema and error handling. | Validated new install→auth→bind→bootstrap and token rotation/revocation. | ❌ |
| **3 — Online features** | Rooms, Presence, Invite, CallSession and WSS event/reconnect. | Verified two-phone room and control-plane lifecycle. | 🟡 |
| **4 — Offline media** | Trusted peers, independent local control, hotspot/airplane, media keys, camera-ready A/V architecture and reconnect. | Three-phone no-internet audio, local trust & cloud return validation, with video capability tracked separately. | 🟡 |
| **5 — Ship** | Stable power/memory/disk/privacy, video gates, CI and Play Console internal/closed-to-prod rollout. | Owner-accepted Android release and backend compatibility record. | ⬜ |

**Parallelism:** CI/test harness, performance telemetry, architectural dependency rules and local threat model may be prepared during Gates 0–2. Never postpone media/security measurement until production. Never mark online authorization as valid for disconnected peers without an approved local security model.

## Scope after owner response

- **Cloud initially:** ServerRelay in local Docker Compose, physical devices on reachable dev network for REST/WSS tests; no mandatory VPS/cloud purchase.
- **Offline:** hotspot/LAN discovery and local call events run without ServerRelay; creator requires prior online login on same installation plus bounded local grant. QR/PIN, manual host approval, remembered trust and authenticated encrypted media are mandatory. Grant expiry and logout design remains pending, **not R01**.
- **Transitions:** `ConnectivityManager` network callbacks start bounded real-backend reachability checks; policy, session/bootstrap and token refresh are online-only. LAN media never depends on success of those cloud requests.
- **Media:** v1 nearby/hotspot/LAN only: 1:1 low-latency interactive authenticated audio first, then one-way host+two listeners with manually approved guests; `VID` later supports video calls and camera upgrade. Cross-network Internet media, TURN and SFU deliberately deferred (R02 resolved).
- **Background:** allow ongoing audio with screen locked/app background when Android permits, with user-visible service notification, appropriate permissions and cleanup; do not silently run camera in background.
- **Hardware:** three different Android devices are available; models/OS versions arrive before TEST-02, not required to record architecture decisions.
- **Release:** Google Play App Bundle/internal + closed testing with three different Android devices, foreground service/camera/mic/local network policy verification.
