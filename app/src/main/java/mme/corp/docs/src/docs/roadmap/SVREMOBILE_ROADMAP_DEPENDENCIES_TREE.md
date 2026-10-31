# SvReMobile — implementation dependencies and acceptance tree

**Reference:** [main roadmap](SVREMOBILE_ROADMAP.md) · [GAP register](SVREMOBILE_SERVERRELAY_INTEGRATION_GAPS.md) · [endpoint contract](../contract/SVREMOBILE_SERVERRELAY_ENDPOINTS.md).

Legend: ✅ accepted implemented scope · 🟡 partly implemented, new changes pending verification · ⬜ planned / not delivered · ❌ confirmed blocker. The owner reports prior unit and Android suites passing (0 failures); the 31 October patch still needs its own run.

## GAP status tree — every item explicitly listed

```text
GAP ██████░░░░░░ 11/23 accepted (47.8%); new GAP-09–14 work awaits new test/live gates
├─ FOUNDATION / SERVER CONTRACT
│  ├─ GAP-00 ✅ centralized endpoint origin/HTTPS debug allowlist
│  ├─ GAP-01 ✅ device register route with server-assigned ID
│  ├─ GAP-02 ✅ stable installation ID / backup exclusion
│  ├─ GAP-03 ✅ bootstrap request contract
│  ├─ GAP-04 ✅ bootstrap response model
│  ├─ GAP-05 ✅ server-owned presence mapping
│  ├─ GAP-06 ✅ CSRF cookie/header + JWT refresh
│  ├─ GAP-07 ✅ release HTTPS and origin-locked debug HTTP
│  ├─ GAP-08 ✅ AndroidKeyStore token storage and test fix
│  └─ GAP-22 ✅ client-safe entity foundation
├─ ONLINE REALTIME / CALL CONTROL
│  ├─ GAP-09 🟡 authenticated WSS; rejoin resubscribe + snapshot
│  ├─ GAP-10 🟡 call lifecycle and one-use invite controls in UI
│  └─ GAP-14 🟡 OpenAPI–Retrofit contract parity checker 25/37
├─ LOCAL OFFLINE / PERFORMANCE / ANDROID
│  ├─ GAP-11 🟡 manual-code ECDH & AES-GCM anti-replay groundwork
│  ├─ GAP-12 🟡 bounded lifecycle/retry/work-budget policy
│  └─ GAP-13 🟡 Android microphone/projection/playback FGS rules
└─ FUTURE ENTITLEMENT / RELEASE
   ├─ GAP-15 ⬜ prior-online-login offline host grant
   ├─ GAP-16 ⬜ trusted local peer/host approval & offline ACL
   ├─ GAP-17 ⬜ 1:1 audio then host+2 and video later
   ├─ GAP-18 ⬜ cloud return token/policy reconciliation
   ├─ GAP-19 ⬜ Google Play release/privacy/permissions
   ├─ GAP-20 ✅ nearby LAN-only v1, internet remote deferred
   └─ GAP-21 ⬜ background audio and role/guest caps
```

**Unimplemented portions of GAP-11 are still blocking media** even though ECDH/AEAD can now be exercised. There is no secure local room grant, QR scanner, trust store or authorized socket/media integration. GAP-14 route parity does not prove DTO-level or deployment parity.

## Other stage dependency tree

```text
ANL ✅ 3/3 planning inventory, backend contracts and accepted requirements
├─ ARCH 🟡 0/3 architecture boundaries
│  └─ ENV 🟡 0/3 debug LAN + TLS environment
│     ├─ SEC 🟡 2/6 CSRF + encrypted storage accepted; offline threats unresolved
│     │  └─ AUTH 🟡 0/4 → DEV 🟡 0/3 → BOOT 🟡 0/3
│     │     ├─ ROOM 🟡 0/3 → INV 🟡 0/3 → CALL 🟡 0/3
│     │     ├─ PRES 🟡 1/3 server-owned presence mapping accepted
│     │     └─ RT 🟡 0/4 WSS works only after authenticated device binding
│     └─ OFF 🟡 0/5 offline authorization & trusted peer admission still blocked
│        ├─ AUD 🟡 0/4 1:1 audio → host+2; no enabled insecure UDP
│        │  └─ VID ⬜ 0/4 later camera / video
│        └─ SYNC ⬜ 0/4 cloud policy/token checks only when reachable
├─ PERF ⬜ 0/5 real battery, CPU, RAM, ROM and thermal budgets needed
├─ OBS ⬜ 0/3 → LOG 🟡 0/3 redacted local diagnostics
├─ TEST 🟡 1/5 owner-reported green existing suite; new patch unverified
│  └─ CI ⬜ 0/3 regression, contract, release gates
└─ PROD ⬜ 0/4 Docker-first, TLS/WSS and Google Play later
```

## Per-GAP implementation dependencies

| GAP | Status | Can be done now | In parallel with | Blocked by for full acceptance |
|---|---|---|---|---|
| GAP-00 | ✅ | Accepted for prior tested scope | TEST, PERF, docs | GAP-07; SEC-00 |
| GAP-01 | ✅ | Accepted for prior tested scope | TEST, PERF, docs | GAP-02, AUTH-00 |
| GAP-02 | ✅ | Accepted for prior tested scope | TEST, PERF, docs | GAP-01, SEC-01 |
| GAP-03 | ✅ | Accepted for prior tested scope | TEST, PERF, docs | GAP-01/02 |
| GAP-04 | ✅ | Accepted for prior tested scope | TEST, PERF, docs | GAP-03 |
| GAP-05 | ✅ | Accepted for prior tested scope | TEST, PERF, docs | GAP-04 |
| GAP-06 | ✅ | Accepted for prior tested scope | TEST, PERF, docs | GAP-00 |
| GAP-07 | ✅ | Accepted for prior tested scope | TEST, PERF, docs | GAP-00 |
| GAP-08 | ✅ | Accepted for prior tested scope | TEST, PERF, docs | GAP-06 |
| GAP-09 | 🟡 | Implemented partially; verify & extend | TEST, PERF, docs | GAP-01/06, RT-02, live WS E2E |
| GAP-10 | 🟡 | Implemented partially; verify & extend | TEST, PERF, docs | GAP-04/06/09, server E2E |
| GAP-11 | 🟡 | Implemented partially; verify & extend | TEST, PERF, docs | GAP-15/16, OFF-03, secure transport integration |
| GAP-12 | 🟡 | Implemented partially; verify & extend | TEST, PERF, docs | GAP-09/11, 3-phone energy/memory evidence |
| GAP-13 | 🟡 | Implemented partially; verify & extend | TEST, PERF, docs | GAP-11/12, explicit consent, device tests |
| GAP-14 | 🟡 | Implemented partially; verify & extend | TEST, PERF, docs | GAP-09/10, full DTO/live contract tests |
| GAP-15 | ⬜ | Design/prototype now | SEC, OFF, SYNC, CI | AUTH-03, SEC-03, expiry/revocation semantics |
| GAP-16 | ⬜ | Design/prototype now | SEC, OFF, SYNC, CI | GAP-11/15, host control |
| GAP-17 | ⬜ | Design/prototype now | SEC, OFF, SYNC, CI | GAP-11/13/16, AUD/VID acceptance |
| GAP-18 | ⬜ | Design/prototype now | SEC, OFF, SYNC, CI | ENV-02, SYNC-00..03 |
| GAP-19 | ⬜ | Design/prototype now | SEC, OFF, SYNC, CI | GAP-13, PROD/CI, device evidence |
| GAP-20 | ✅ | Accepted for prior tested scope | SEC, OFF, SYNC, CI | owner scope decision |
| GAP-21 | ⬜ | Design/prototype now | SEC, OFF, SYNC, CI | GAP-13/16/17, device tests |
| GAP-22 | ✅ | Accepted for prior tested scope | SEC, OFF, SYNC, CI | prior entity/JVM owner acceptance |

## Milestones and practical gates

1. **Existing online foundation:** owner-accepted client login/CSRF, registration/bootstrap, KeyStore and avatar/profile. Stage ANL completed. Keep all protection against token/cookie leakage.
2. **GAP-09/10/14:** server-running WSS and invite/call E2E, expired JWT and registration revocation, authoritative snapshots after reconnect, malformed envelopes, null/404 call responses. Automated path check covers **25 of 37** HTTP operations and must run against the actual backend OpenAPI on changes.
3. **GAP-11/13/15/16/17/21:** implement offline authorization grant and host-trusted admission, secure discovery/transport and consent before connecting real microphone, projection or UDP. Pairing primitives alone are insufficient. Android 14+ foreground-service rules and Android 17 local network migration must be tested.
4. **GAP-12/18/19:** real three-device tests for Doze, screen lock, hotspot transitions, CPU/thermal/heap/disk growth and cloud/session reconciliation. Release Play/TLS is a later gate.

Do not interpret a completed **product-scope decision** (GAP-20) as completed cross-network media functionality.
