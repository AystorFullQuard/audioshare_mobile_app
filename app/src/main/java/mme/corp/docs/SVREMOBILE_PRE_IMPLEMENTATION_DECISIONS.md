# SvReMobile — owner decision gate before coding

**Status:** 🟡 unanswered. Answer the **P0** questions first; the lower-priority questions can be resolved in their owning stages. This file is deliberately a decision record, not a detailed implementation journal. Return to the [roadmap](SVREMOBILE_ROADMAP.md) when decisions are approved.

## P0 — required before integration implementation

| # | Decision required | Why it changes architecture | Safe preliminary recommendation |
|---|---|---|---|
| **Q01 — What exactly must work without internet?** | Does hotspot-only mode allow guest-host discovery/audio only, or persistent cloud rooms, private invites, account login, accountless use, and reconnection to the same room? Does it also mean no cellular connection on every device? | Drives local identity/session, authorization, state reconciliation and UI semantics. | Standalone ephemeral **Local Room** mode with locally paired peers; online accounts/rooms remain server-authoritative. |
| **Q02 — Trusted peers and local security** | Host approval each time? PIN/QR scan? Previously paired devices automatically rejoin? Prevent unknown hotspot guests from hearing audio? Encryption mandatory? | Discovery is currently spoofable and UDP PCM unauthenticated; no offline backend can verify cloud account/session revocation. | QR/short-code pairing + authenticated ephemeral key exchange, encrypted media, explicit host approval and no silent privilege carryover. |
| **Q03 — Hosting, network and domains** | Development machine vs VPS/cloud vs managed hosting? Country/region, budget/month, domain/TLS availability, one or multiple instances? PostgreSQL/Redis/backup/monitoring scope? | Changes DNS/TLS/WSS edge, certificate trust, CI runners, CORS/CSRF origin and backend scaling plan. | Docker Compose + managed/hosted DB for staging first; production HTTPS/WSS reverse proxy and isolated secrets; Kubernetes only when operationally justified. |
| **Q04 — Android deployment/testing** | Google Play vs internal APK/closed test? Which Android versions/device models should we support? Physical phones and hotspot-capable devices available? | Determines signing, foreground/background service limitations, permissions, instrumentation matrix and rollout. | Real devices (at least 2 different OEMs) + emulator; debug and staging flavors. |
| **Q05 — Core audio product** | Microphone sharing, system audio capture, both? One host to 1 receiver or 5–20 listeners? Target latency/quality and duration? Background and screen-off permitted? | Codec, packet/security overhead, foreground-service types, battery budget, network design and CPU/RAM limits. | One-to-many receiver target explicitly specified; start with a measurable 2-phone scenario and expand under tests. |
| **Q06 — Online/local interaction** | When internet fails during a live call, should audio continue over hotspot/LAN? Reattach cloud room automatically? What happens to disconnected guests, muted peers and invites? | Requires two separate authority/state machines and deterministic failover/restore. | Keep LAN media if already securely established; pause cloud-only controls; reconcile with server only after reauthentication. |
| **Q07 — Accounts and authentication policy** | Email/password only or social login/2FA? Is guest operation mandatory? Offline users identifiable by QR keys or local pseudonyms? Account/device quotas? | Changes credential storage, threat model, CSRF bootstrap and backend requirements. | Respect ServerRelay existing auth contract first; no offline cloud-login emulation. |
| **Q08 — Privacy/security expectations** | End-to-end media encryption vs encrypted transport; logs/analytics opt-in, retention; ability to record audio or retain call metadata? | Key management, consent, legal/privacy policy, debuggability and telemetry. | Encrypt LAN payloads, never log tokens, QR secrets, PCM or identifiers unnecessarily; no recordings by default. |

## P1 — choose before relevant stages

| # | Decision required | Safe initial proposal |
|---|---|---|
| **Q09 — Device identity on reinstall/backup** | Stable `installationId` per app installation; regenerate on cleared data/reinstall, never infer device ID from MAC/IMEI. Define account switching. |
| **Q10 — Performance targets and test budgets** | List two baseline phone models and measure idle/background/30-min+ stream battery drain, thermal, CPU, PSS/heap and disk growth; set acceptance thresholds from measurements. |
| **Q11 — Local transport/media codec** | Compare Opus-like compressed media against PCM under real hotspot tests; choose low-allocation bounded buffers and jitter-control based on latency/quality targets. |
| **Q12 — Backend release coordination** | Pin tested backend API contract release/commit and coordinated mobile staging builds; don't assume last server roadmap revision equals deployed server. |
| **Q13 — Modularization timing** | Start with bounded packages and extract foundational Gradle modules with tests; no per-feature process or client microservices. |
| **Q14 — Business/operating expectations** | Expected users, concurrency, target geography, ongoing hosting budget, release cadence and crash/support ownership. |
| **Q15 — Diagnostics** | Decide offline-only vs hosted crash/metric pipeline, retention/access, privacy redaction, and ability for users to export a safe support bundle. |
| **Q16 — Android 17 LAN privacy** | When moving beyond `targetSdk=36`, adopt `ACCESS_LOCAL_NETWORK` (target SDK 37+) or a system-mediated discovery/connection option, as required by platform rules. |

**Pending owner decisions do not grant approval to start implementation.** First code stage after Q01–Q08: ENV/SEC/AUTH/DEV/BOOT. Revisit this record and update the roadmap statuses only alongside tested changes and owner acceptance.
