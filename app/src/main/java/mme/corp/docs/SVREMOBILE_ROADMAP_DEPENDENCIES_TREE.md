# SvReMobile — staged implementation dependency tree

**Source:** [SVREMOBILE_ROADMAP.md](SVREMOBILE_ROADMAP.md). **Legend:** ✅ verified planning, 🟡 partially present/decision pending, ⬜ planned, ❌ confirmed blocker. These are **not** executable dependencies and the graph is not a claim of delivered features.

```text
ANL ✅ static inventory + source contract gap register
└─ ANL-02 🟡 owner decisions / requirements freeze
   ├─ ARCH 🟡 one Android app, module boundary choice
   │  ├─ ENV-00 ❌ unsafe fixed cleartext endpoint
   │  │  └─ SEC-00 ❌ public auth CSRF proof missing
   │  │     └─ AUTH 🟡 align login / refresh / stored session
   │  │        └─ DEV-00 ❌ endpoint + installationId mismatch
   │  │           └─ BOOT-00 ❌ bootstrap installationId mismatch
   │  │              ├─ ROOM 🟡 → INV ⬜ → CALL ⬜
   │  │              ├─ PRES ❌ → realtime-backed member updates
   │  │              └─ RT ⬜ → WebSocket auth, subscriptions, reconnect
   │  └─ SEC-01..05 ⬜ client credential and local network security
   ├─ OFF-01 ⬜ offline semantic/identity decision
   │  └─ SEC-03 ⬜ local pairing and encryption
   │     └─ OFF-02..04 ⬜ UDP discovery, failover and cloud-return policy
   │        └─ AUD ⬜ secure, efficient local streaming
   ├─ PERF ⬜ baseline before implementation; gate all high-frequency work
   ├─ OBS/LOG ⬜ privacy-safe telemetry and operational evidence
   ├─ TEST/CI ⬜ tests and gates continuously, not only at launch
   └─ PROD ⬜ cloud, rollout, TLS, release and user-support acceptance
```

## Gates and sequencing

| Gate | Must be explicitly decided or verified | Output | Status |
|---|---|---|---|
| **0 — Product/security** | Offline semantics, trust, devices, server availability, audio topology and target OS. | Signed-off decision register; severity-ranked threat model. | 🟡 |
| **1 — Infrastructure/auth** | HTTPS/WSS or approved debug-only LAN exception, CSRF, token storage/session cleanup. | Real login/refresh/logout against matching ServerRelay build. | ❌ |
| **2 — Bind & bootstrap** | Local `installationId`, `/devices/register`, frozen `/session/bootstrap` schema and error handling. | Validated new install→auth→bind→bootstrap and token rotation/revocation. | ❌ |
| **3 — Online features** | Rooms, Presence, Invite, CallSession and WSS event/reconnect. | Verified two-phone room and control-plane lifecycle. | 🟡 |
| **4 — Offline media** | Trusted peers, hotspot/airplane operation, media keys, Android service constraints, failover. | Real-phone no-internet media and isolation/security tests. | 🟡 |
| **5 — Ship** | Stable power, memory, disk, privacy, CI, staging/production and rollback. | Owner-accepted Android release and backend compatibility record. | ⬜ |

**Parallelism:** CI/test harness, performance telemetry, architectural dependency rules and local threat model may be prepared during Gates 0–2. Never postpone media/security measurement until production. Never mark online authorization as valid for disconnected peers without an approved local security model.
