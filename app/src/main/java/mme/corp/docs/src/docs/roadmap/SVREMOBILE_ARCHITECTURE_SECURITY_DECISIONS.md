# SvReMobile — Architecture and Security Decision Record (pre-implementation)

**Record ID:** `ADR-MOBILE-2026-10-26-01`  
**Status:** Product requirements **accepted by project owner** on 26 October 2026 (Q01–Q08, R01–R04); detailed implementation and security design **not yet accepted**.  
**Current implementation:** 27 Oct mobile source; GAP-00 shared network policy, GAP-01 backend registration route and GAP-02 persisted per-installation ID; JVM/device acceptance pending.  
**Boundary:** Server-issued `deviceId` remains user-scoped; generated `installationId` is stable per app installation, and neither is an offline authorization grant.  
**Links:** [roadmap](SVREMOBILE_ROADMAP.md) · [dependency table](SVREMOBILE_ROADMAP_DEPENDENCIES_TREE.md) · [integration gaps](SVREMOBILE_SERVERRELAY_INTEGRATION_GAPS.md).

## Accepted scope and invariants

| ID | Final product decision | Security/operational constraint | Tracked implementation |
|---|---|---|---|
| **ARCH-DEC-01** | One installable Kotlin/Compose Android app: **modular monolith**, no client microservices; use explicit domain interfaces and extract Gradle modules only when warranted. | No extra processes/polling/IPC as a substitute for modularity; monitor CPU/RAM/battery. | ARCH-00..02, PERF |
| **ARCH-DEC-02** | ServerRelay owns **online** identity, device/session registration, policy and online room/call control. Phone-hosted LAN room owns **offline** peer admission and local events. | Never treat an offline guest or local room as a ServerRelay identity, role, ACL or token; local calls must not depend on cloud health. | AUTH, BOOT, ROOM, OFF, SYNC |
| **R01** | **At least one successful ServerRelay login on this app installation** before *creating* any offline room. No first-install, never-signed-in offline room creators. | This is necessary but insufficient: hosting requires a valid **time-bounded, installation-bound local offline grant**. Never claim online revocation/policy was checked without network. Offline grant expiry/logout/revocation and migration are unresolved technical design choices. | AUTH-03, SEC-01/03/04, OFF-01/03, SYNC-02 |
| **R02** | V1 calls/media operate only between peers reachable on nearby **LAN, hotspot or supported local peer link**, even when internet is absent. | Cross-internet remote calls, NAT traversal, TURN/SFU relay are *not* required for v1. Online account bootstrap may use cloud when reachable, but peer media remains LAN-bound. | ENV, CALL, OFF, AUD, VID |
| **R03** | Active **audio** continues in background or when screen locked **where Android permits**. | User-visible control/notification, correct foreground-service types and permission flow, clean teardown and battery monitoring. No assertion that cameras always work in background. | AUD-02, VID-02, PERF-00/03, TEST-02 |
| **R04** | Delivery order: **1:1 authenticated interactive audio**; then **one host → two listeners** for one-way audio share. | Only one-way share may accept **unauthenticated guests** in the first release; every guest requires deliberate host approval and pairing. Guests cannot create rooms, host or join interactive audio/video as unauthenticated participants. | CALL-00/01, INV-02, OFF-03, AUD-01/03 |
| **SEC-DEC-01** | Four required local controls: **QR/PIN pairing, explicit host approval, revocable remembered trusted devices, encrypted/authenticated media**. | QR/PIN is an introduction/verification aid, never a sole secret for media; authenticated key agreement, replay protection, downgrade prevention and key rotation require threat review. | SEC-03..05, OFF-02..04 |
| **SYNC-DEC-01** | Local communication remains independent of ServerRelay connectivity. When server becomes reachable, automatically reconcile policy, session and token state. | Offline cannot obtain fresh server tokens or revocations. Connectivity callbacks, bounded backoff and serialized refresh; cloud rejection removes cloud rights without automatically ending separately authorized LAN calls. | ENV-02, SYNC-00..03 |
| **MEDIA-DEC-01** | Audio latency and stability first, later optimize quality without regression; video calling and add-camera-during-audio remain planned future milestones. | Prototype raw PCM/UDP not production security; measure codec, jitter, buffers, thermal, camera/mic permissions, CPU/GPU/battery and RAM/disk. No initial call recording. | AUD, VID, PERF, TEST |
| **OPS-DEC-01** | Initial ServerRelay runs locally or via **Docker Compose**; cloud vendor deferred; app planned for **Google Play**. | Separate release TLS/WSS from debug LAN; no permanent cleartext or sensitive HTTP payload logging. Three physical Android devices exist, exact models/versions deferred until hardware tests. | ENV, SEC, TEST, CI, PROD |
| **PRIV-DEC-01** | Start with **local-only bounded, redacted diagnostics**. | No raw media recording, hidden upload, tokens/keys in logs, unbounded RAM/ROM accumulation; future remote crash/telemetry requires separate opt-in/policy decision. | OBS, LOG, PERF, PROD |

## Permission and identity boundaries

```text
[ServerRelay — reachable only] online login → per-installation identity / cloud session / device binding
                              └─ grant issuance or validation for limited offline hosting (protocol TBD)
[Phone host — local only]     cached device-bound offline host grant → create LOCAL room
                              ├─ QR/PIN + verified key agreement + explicit host approval
                              ├─ authenticated registered peer → 1:1 audio
                              └─ manually approved guest → ONE-WAY SHARE ONLY (max two listeners)
[Media]                       authenticated encrypted LAN transport; no server relay required
[Connectivity restored]       check ServerRelay policy/session/token with bounded retries
                              └─ never silently elevate local identities into cloud access
```

**Important:** a previously successful login cannot imply the user is still logged in now. If the grant is missing, locally expired, reset, user-signed-out or otherwise invalid, offline room *creation* is denied. Rules for pre-existing active LAN calls when grant expires or logs out require a documented threat-model decision before code; do not silently invent a perpetual exception. Use explicit local grant verification; do not store a reusable raw server password to enable offline login.

## Release sequence and performance expectations

1. Documented product decisions (complete) and contract gap analysis (complete); security specification/threat model (pending).
2. ENV → SEC → AUTH → DEV → BOOT with live ServerRelay contract tests (all pending).
3. Local discovery/approval, 1:1 secure low-latency audio, then host+two-listener share + authorized guests (all pending).
4. Background audio and connectivity switching test with screen lock, thermal/battery/heap/disk soak on three Android devices (all pending).
5. Video/camera upgrade, Play policy, CI, release and cloud hosting choice in later tracked stages (pending).

Media idle mode should not retain active capture locks or continuous discovery; media queues, thread pools, logs, retries and disk cache must have explicit resource ceilings. Benchmark energy/CPU/GPU/RAM/ROM before setting quantitative device-specific targets.

## Deliberately unresolved technical design (not reopened owner product decisions)

- **OFF-GRANT-01:** expiry/refresh TTL, signed grant issuer and cryptographic binding, secure storage and rollback protection; behavior for offline logout, account switch, reinstall, clock tampering, cancellation and locally known revocation.
- **PAIR-01:** local trusted-peer rejoin expiration, explicit removal, rekey on host/peer changes, QR/PIN verification and anti-replay/DoS/rate limit policy.
- **BG-01:** foreground-service permissions, user-visible notification and exact behavior across Android variants/ OEM background restrictions. Device models + Android versions to be supplied before hardware gate, not required for project planning.
- **MEDIA-01:** secure codec/transport choice and measured latency, jitter, thermal and battery budgets; secure guest media-key handling.
- **CLOUD-01:** later production hosting provider, staging URL and Play Console publication gates; **remote cross-Internet calling stays explicitly outside v1**.

**Status discipline:** owner product decisions are not code acceptance; `ANL-00..02` stay ✅ (4 of 104 steps (including GAP-20 product scope)). GAP-00/01/02 and GAP-22 code are 🟡 pending Android Gradle tests and device verification. No live server/Android test claims.
