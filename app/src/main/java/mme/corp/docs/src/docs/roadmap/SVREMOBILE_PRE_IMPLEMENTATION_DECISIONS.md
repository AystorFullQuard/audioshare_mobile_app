# SvReMobile — accepted owner answers and remaining decision gates

**Initial analysis:** 26 October 2026 13:07 EET archives. **Current mobile source:** 14:23 EET snapshot. **Updated with owner answers:** 26 October 2026, including R01–R04. **Status:** ✅ twelve product choices documented; 🟡 security detail and hardware matrix pending; **GAP-00 implementation present; not Android-tested**. [Main roadmap](SVREMOBILE_ROADMAP.md) · [Dependency tree](SVREMOBILE_ROADMAP_DEPENDENCIES_TREE.md) · [decision record](SVREMOBILE_ARCHITECTURE_SECURITY_DECISIONS.md).

## Accepted product requirements — record, not shipped features

| ID | Owner answer | Status of product decision | Implementation status |
|---|---|---|---|
| **Q01 Offline-first** | Two devices discover each other and communicate without internet, cloud, or active Internet connection | ✅ requirement defined; offline host requires prior server login (R01) | ⬜ |
| **Q02 Security** | QR/PIN pairing, explicit host approval, remembered trusted devices, mandatory authenticated encryption | ✅ all four requested; technical policies open | ⬜ |
| **Q03 Hosting** | Start local or Docker; decide cloud provider later; evaluate GitHub Student Developer Pack | ✅ development direction | ⬜ |
| **Q04 Distribution** | Google Play production; 3 distinct real Android devices for testing | ✅ launch channel/test resources direction | ⬜ |
| **Q05 Media** | Prioritize low-latency **audio calls**; support **video calls / add camera while audio active**, plus **one-way audio share**; optimize quality later without regressing latency | ✅ product mode direction | ⬜ |
| **Q06 Connectivity** | Offline events/call control independent of cloud; preserve LAN session when internet changes; automatically check policies/session/token when online | ✅ expected behavior; unreachable-server handling constrained by physics | ⬜ |
| **Q07 Account/guest** | Host/creator must have authenticated online at least once; unauthenticated guests need explicit manual host approval and are permitted only on one-way audio share | ✅ product rule, R01/R04 resolved; bounded host grant rules pending | ⬜ |
| **Q08 Privacy** | Begin with small, local diagnostics; later expand if needed | ✅ initial diagnostics preference | ⬜ |

✅ above marks a **product answer** only; it does **not** mark corresponding code step complete. The roadmap's 4/103 counts three ANL planning artifacts and the GAP-20 scope decision; no integration step has passed Android/device acceptance.

## Mandatory technical distinctions

- **First-ever offline sign-in — resolved R01:** cloud accounts cannot be verified for the first time without server reachability. The host MUST have authenticated to ServerRelay **at least once on this app installation** and hold a still-valid locally enforceable offline host grant. First-launch offline room creation and local-only host identity are **out of scope**. Prior authentication alone is not an indefinite grant; TTL, logout, reinstalls, device/account switch and local revocation are SEC/AUTH design work.
- **No cloud while disconnected:** policy check, token refresh and session refresh **cannot execute** while the server is unreachable. Use connectivity callbacks and controlled retries **when reachable**. A server policy rejected/revoked in cloud cannot be learned in airplane mode. Explicit local trust expiration and cached grant maximum age must be designed; fail closed for **cloud privileges**, without needlessly ending separately authorized local calls.
- **Separate room identities:** a local hotspot room and a ServerRelay room have distinct IDs and authority. Offline guests must not inherit server membership/roles. Cloud return reconciles the account's server state; it does not upload or grant an offline call as a cloud call without a separate contract.
- **Security of 'PIN':** human-readable PIN/QR is only a bootstrap aid. Pairing needs authenticated ephemeral key agreement, verified peer identity and replay protection. Remembrance requires a revocable device trust record.
- **Media direction:** implement control plane/mic low-latency A/V-capable architecture first; one-way share must not accidentally send a microphone track. Evaluate WebRTC/Opus/DTLS-SRTP against custom secure LAN media; choose after device/battery/security tests, never implement raw PCM UDP as shipping security.
- **Network paths:** hotspot/LAN media must use the correct local `Network`; internet detection requires validated backend reachability and authenticated checks. The Wi-Fi hotspot may have no Internet while mobile data provides a separate network; avoid process-wide network binding that breaks one of them.
- **Testing / background calls — resolved R03:** audio should continue during screen lock/background *where Android permits*, with appropriately declared foreground service, active notification, user control and robust cleanup; this does not authorize hidden capture or unrestricted background camera. Three owned phones are sufficient to start engineering tests, **models/OS versions will be supplied later** before TEST-02. Play Console internal → closed testing → staged production; the 3 phones do not replace any applicable account-level tester requirement.
- **Logs/privacy:** locally bounded, redacted diagnostics; default no recording, packet dumps, automatic upload, token logging, QR secret logging or media-content retention.

## R01–R04 — owner responses confirmed (26 October 2026)

| ID | Confirmed choice | Implementation stage(s) | Product decision status | Code status |
|---|---|---|---|---|
| **R01 — offline hosting** | Successful ServerRelay login **at least once on the same installation** required before creating offline rooms; bounded device-bound offline authorization to be designed. No cold-start local-only hosts. | AUTH-03, SEC-03, OFF-01, SYNC-02 | ✅ | ⬜ |
| **R02 — v1 geography** | Nearby hotspot/LAN **only** for local audio/video media. Cross-internet calls, STUN/TURN/SFU and remote media relay deferred until separately scoped. | CALL-00, AUD-01, VID-01, PROD-00 | ✅ | ⬜ |
| **R03 — locked/background** | Keep active audio alive when screen locked/app background **only if Android allows**, with user-visible foreground service controls; no assumption of background video capture. Phone models/OS versions supplied later. | AUD-02, TEST-02, PERF-00, VID-02 | ✅ | ⬜ |
| **R04 — users and roles** | Start **1:1 interactive audio**; next **one host + two listeners for one-way share**. Manual host approval required for guests, who may participate **only in one-way audio share** initially, never host or use interactive/video mode anonymously. | INV-02, CALL-00/01, OFF-03, AUD-01/03 | ✅ | ⬜ |

## Pre-implementation technical design gates (product choices closed)

- **SEC-03 / AUTH-03:** specify offline grant signing/storage, maximum lifetime, host logout/rotation/reinstall behavior, cached revocation and explicit fail-closed boundaries for cloud functions. *Offline grant validity is not synonymous with live cloud login.*
- **SEC-04:** complete local threat model for pairing, spoofing, relay, replay, trusted-peer rejoin, host approval and encryption. QR/PIN must not be the sole media key.
- **ENV-00 / SEC-00:** GAP-00 introduces HTTPS-only release configuration and debug LAN endpoint validation; Android verification and ServerRelay CSRF compatibility remain required before production.
- **AUD / OFF:** decide media protocol and local discovery only after security/performance comparison; no unauthenticated UDP as release transport.
- **TEST-02:** record the three phone models and Android versions when available; do **not** hold up documentation or interface-design work just for device names.
- **PROD:** cloud provider and precise cost, remote media infrastructure, analytics/telemetry remain separate later choices, not blockers for local Docker-backed design.


## P1 / defer until the relevant stage

- Account reset and device re-registration; grant lifetime/host-rejoin policies; retention limits of trusted-device list.
- Offline-to-online UX: manual mode switch vs automatic control-plane reconnect; conflicting room events and offline room naming/migration.
- Exact latency/bitrate/jitter/battery/RAM targets after recording baselines on the three phones, not arbitrary pre-test thresholds.
- Production cloud provider, budget, region, domain and disaster recovery; GitHub Student offers are optional and change over time.
- WebRTC vs bespoke codec transport decision based on actual codec availability, licensing, reliable reconnection and security audit.
- Telemetry/analytics/crash upload opt-in, consent, retention and Play Data safety after initial local-only stage.

**Hold:** R01–R04 product choices are resolved and documented, but this is **not a request to implement**. Detailed SEC-03/04 offline authorization + threat model must be reviewed before LAN pairing/media work; when implementation is separately requested, sequence ENV → SEC → AUTH → DEV → BOOT. A disconnected phone cannot claim an online token or cloud policy was just verified.
