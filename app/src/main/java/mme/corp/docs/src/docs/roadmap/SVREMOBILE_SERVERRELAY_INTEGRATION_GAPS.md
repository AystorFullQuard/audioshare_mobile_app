# SvReMobile ↔ ServerRelay — architecture and contract gap register

**Review:** SvReMobile 26 October 2026 **14:23 EET** snapshot (working tree, HEAD `bdda9f3`) + provided ServerRelay archive. GAP-00 network/config code added and Kotlin-only policy smoke tested, not Android/device accepted. Other findings are primarily static. Parent roadmap: [SVREMOBILE_ROADMAP.md](SVREMOBILE_ROADMAP.md).

## Current architecture (observed, not assumed)

| Layer | Mobile snapshot | Server snapshot | Implication |
|---|---|---|---|
| Packaging | Kotlin + Jetpack Compose, single `:app`, `minSdk=24`, `targetSdk=36`; no other Gradle modules | Spring Boot/Java 21 parent with `boot`, `dto`, `exceptions`, `db`, `security`, `observability` and placeholder feature modules; Boot still owns most domain classes | Do **not** equate Android module count to backend service count. |
| Dependency management | Manual `AppContainer`, repositories/DTOs, Retrofit + OkHttp/Gson, DataStore/Flow, coroutines | HTTP/JSON REST V1 and Spring Security; versioned docs and OpenAPI | Put endpoint/contract mapping behind repositories; don't share Java DTO binaries with Android. |
| Online core | Auth, Device, Bootstrap, Room, Presence API client + unit/mock tests; no Invite/Call/realtime WebSocket clients observed | Server authoritative identity, bound AuthSession+Device, Rooms/Invite/Call/Presence, `/ws/realtime` | Align frozen contract first, then add missing client features. |
| LAN/media | UDP broadcast discovery and PCM AudioRecord/AudioTrack/MediaProjection sender/receiver foreground service | Raw-audio `/ws/rooms/{id}/audio` is dev/local-only; release uses `/ws/realtime` for control signaling | Independent audited hotspot peer transport needed, not HTTP service discovery. |
| Existing testing | 27 JVM test files, including MockWebServer `ServeRelayCompatibilityFlowTest`; AndroidTest code exists | Backend contract/integration tests + Postman assets | MockWebServer fixtures are assertions about **fixtures**; they are not proof the *current server* agrees. |

## Cross-cutting foundation first — GAP-00

**🟡 Implemented, acceptance pending:** single `ApiEndpointPolicy` validates the build-configured backend origin, rejects public HTTP even in debug, denies cleartext at the release Android network-security layer, and disables unredacted HTTP BODY logging in the shared HTTP client. Debug HTTP remains permitted by the debug manifest resource only and the policy restricts its configured destination to localhost/private addresses; Android's debug cleartext override is broad at the platform layer, so **never distribute a debug APK to untrusted users**. OkHttp redirects are disabled to prevent endpoint bypass. New JVM tests cover URL policy; Android SDK/device tests have not run. Release builds must supply `-PserverRelayReleaseBaseUrl=https://your-domain/`. This closes **part of the original GAP-07**, not cloud TLS setup or the other gaps.

**Still open:** `AuthRepository`, `SessionManager`, and `LoginViewModel` currently contain application-level logs of token fragments, IDs, username/password metadata and raw server error bodies. Removing the OkHttp BODY interceptor does **not** sanitize those logs. This requires an explicit `LOG/SEC` remediation with regression coverage.

No backend endpoint, device schema, CSRF, cloud policy or UDP media behavior has been silently changed by GAP-00.

**GAP stage count:** GAP-00 plus GAP-01..21 = 22 tracked items; only GAP-20 is ✅ (a scope decision, not implementation).

## Confirmed high-priority mismatches / hazards

| ID | Evidence in mobile | Authoritative server evidence | Consequence and target |
|---|---|---|---|
| **GAP-01 — device endpoint** ❌ | `data/api/DevicesApi.kt`: `POST /api/v1/devices` | `boot/.../controller/device/DevicesController.java`: `POST /api/v1/devices/register` | Current registration resolves to wrong path. Fix DTO and endpoint together, test 201 + response. |
| **GAP-02 — installation identity** ❌ | `RegisterDeviceRequest.kt` has no `installationId`; `DeviceIdStore` persists server-issued `deviceId` only | `dto/.../device/RegisterDeviceRq.java` requires `installationId`; frozen joint contract requires per-installation identity and session binding | Create stable app-installation ID, separate from user-scoped server device ID; migration rules for user switch/clear-data. |
| **GAP-03 — bootstrap request** ❌ | `SessionBootstrapRequest.kt` sends `deviceId` and fields `manufacturer/model/platform` | `dto/.../bootstrap/SessionBootstrapRq.java` requires `installationId`, optional display/device name, version, locale/timezone/capabilities; no `deviceId` | Client request lacks required installation ID; cannot assume extra fields are accepted forever. Frozen server bootstrap never registers a device. |
| **GAP-04 — bootstrap response** 🟡 | `SessionBootstrapResponse.kt` stores only rooms + presence; `SessionPresenceResponse` expects `state` | `dto/.../bootstrap/SessionBootstrapRs.java`: session/user/configuration/capabilities/devices/rooms/presence/server; Presence uses availability/activity/preference | Nullable legacy response model discards control-plane state and may parse incorrect presence. Audit exact wire response. |
| **GAP-05 — Presence schema** ❌ | `PresenceHeartbeatRequest.kt` includes legacy `state`; response expects `state` enum | `PresenceHeartbeatRq`: `deviceId`; `PresenceHeartbeatRs`: `availability`, `activity`, `preference`, `currentRoomId`, time | Gson missing required `state` can cause invalid model behavior; explicit compatible mapping and regression needed. |
| **GAP-06 — CSRF on public auth** ❌ | `AuthApi` exposes login/refresh; `ApiClient`/refresh client show no CSRF preparation/cookie + header pairing | `GET /api/v1/auth/csrf`, then `POST` login/register/refresh without bearer uses masked `X-XSRF-TOKEN` and `XSRF-TOKEN` cookie (SEC05_CSRF_CLIENT_CONTRACT) | Current authentication is not prepared for backend's protected public-auth POSTs; 403 on normal login/refresh. Add isolated no-bearer preparation path with atomic pair. |
| **GAP-07 — insecure transport** 🟡 | **Original finding:** hardcoded `192.168.100.4`, global cleartext and debug `BODY`; **now partially mitigated** by GAP-00 release-safe network/config foundation | Server production transport requires HTTPS/WSS and proxy/TLS enforcement (`TRANSPORT_SECURITY_V1.md`) | Production token exposure and unsafe rollout; separate development-only exceptions, default TLS in release, redact all sensitive payloads. |
| **GAP-08 — token at rest / backup** 🟡 | `SessionManager.kt` stores access/refresh tokens in Preferences DataStore; manifest `allowBackup=true`; backup/extraction XML are templates | Refresh token revocation and auth-session binding protect server-side use but not stolen client credentials | Threat-model encrypted credential storage, KeyStore access and exclusion of tokens/install identity from auto-backup/transfer; migration tests. Encryption not assumed from DataStore. |
| **GAP-09 — realtime** ⬜ | No `WebSocket` signaling client observed | `/ws/realtime` uses bearer header, `X-AudioShare-Device-Id`, event envelope, reconnect/resubscription, revocation/429/503 protection | Implement native WSS only after auth/device state; avoid raw-audio WS endpoint. |
| **GAP-10 — invite/call APIs** ⬜ | No dedicated Invite/Call client interfaces observed | `InviteQrController`, `RoomInviteManagementController`, `CallController` provide backend APIs | Align QR/invite idempotency, call lifecycle, abuse/security; do not start with media without control-plane authority. |
| **GAP-11 — offline security** 🟡 | `DiscoveryProtocol` UDP message broadcasts receiver IP/port; `UdpAudioSender` emits sequence/timestamp + PCM, receiver accepts datagrams without peer auth/decryption | Server authorizes Room/Invite/Call only when reachable; its WSS is not a LAN controller | Hotspot-without-internet requires explicit local pairing/trust/keying, discovery spoof protection, verified peers, anti-replay and authorization. Existing UDP is a **prototype**, not secure offline mode. |
| **GAP-12 — power/memory & lifecycle** 🟡 | Presence room-member polling and heartbeats, per-session coroutines, UDP receiver thread, audio allocations; foreground service manages media runtime | Server heartbeat/event reliability policy and Android OS lifecycle constraints | Profile actual devices, bound all loops, queues/socket ownership and memory; prefer event-based updates with fallback polling and lifecycle-aware cadence. Do not infer a leak without a heap/soak capture. |
| **GAP-13 — mediaProjection and Android** 🟡 | Manifest service has `mediaProjection|mediaPlayback`, media projection + audio runtime; no `microphone` service type in this service | Android 14+ foreground service type/while-in-use restrictions; Android 17 requires `ACCESS_LOCAL_NETWORK` when targeting 37 | Decide whether microphone playback/capture is in foreground service; review service type/permission pathways with test matrix. `targetSdk=36` does not yet require `ACCESS_LOCAL_NETWORK`; roadmap migration must anticipate 37. |
| **GAP-14 — backend migration and doc age** 🟡 | Client mocks describe an older V1 shape | Backend main product roadmap revision 16 Oct; migration roadmap has earlier snapshots and Stage A/Stage B status; current archive source is 26 Oct | Compare *current actual controllers + DTOs + OpenAPI* rather than treat old roadmap revisions as definitive deployment status. Do not infer that placeholder Maven modules are deployed services. |

### Example current endpoint direction (from static code)

```text
Android public login  ──GET /api/v1/auth/csrf──→ same backend origin
                      └─POST /api/v1/auth/login (cookie + masked proof)─→ session
Android installation ──POST /api/v1/devices/register {installationId,...}─→ deviceId + session binding
Android bootstrap    ──POST /api/v1/session/bootstrap {installationId,...}─→ authoritative session snapshot
Android runtime      ├─REST rooms/presence/invites/calls
                      └─WSS /ws/realtime (Authorization bearer + X-AudioShare-Device-Id)
Audio peers          ──secure negotiated LAN/hotspot media transport──→ peers (NOT via backend REST)
```

**Important distinction:** server `deviceId` and client `installationId` have different roles. The device is server-generated, the installation identifier is locally created/stable. The server's frozen Device/Bootstrap joint contract is the integration starting checkpoint.

## Recommended mobile architecture decision

**Prefer a modular Android app (modular-monolith deployment), not separate mobile microservices.** One `:app` composition root and one foreground-service ownership boundary; separation by cohesive responsibility using clean/Kotlin interfaces, bounded concurrency and repository APIs. Extract Gradle modules when their interfaces stabilize and build/test independence is useful. Suggested evolution:

```text
:app  [Compose screens, app navigation, permissions, DI composition]
 ├─ :feature:auth          (after stable core contract)
 ├─ :feature:rooms         (after auth+boot)
 ├─ :feature:call          (after CallSession contract)
 ├─ :feature:offline       (after local trust/identity freeze)
 ├─ :core:network         (HTTP/WSS clients, error mapping, environments)
 ├─ :core:security        (token/identity storage and local key policy)
 ├─ :core:model           (domain interfaces, types; never Retrofit-owned)
 └─ :core:local-transport (discovery, secured media, audio service interfaces)
```

This is a **target responsibility diagram, not modules already present or a command to extract everything immediately**. Begin with packages and automated dependency rules, then split into 2–4 Gradle modules with passing tests, only growing when useful. Avoid a singleton store per feature, unnecessary Android IPC/processes, and sharing server Java DTO dependency jars. No operational benefit from containerized/microservice clients on a phone.

**Efficiency:** Kotlin/Compose architecture alone does not guarantee low load. Design to eliminate idle polling, stop scanning when not visible/active, reuse audio buffers, cap network retries, avoid holding Wi-Fi/CPU locks outside active streaming, monitor thermal and radio utilization, and close sockets/services on every termination path. Benchmark only on agreed target phones and streaming topology. Release should never permit unbounded memory/disk retention.

## External references for implementation review

- Android modularization: https://developer.android.com/topic/modularization
- Network security: https://developer.android.com/privacy-and-security/security-config
- Foreground service type guide: https://developer.android.com/develop/background-work/services/fgs/service-types
- Local-network permission (Android 17): https://developer.android.com/privacy-and-security/local-network-permission
- Memory heap investigation: https://developer.android.com/studio/profile/capture-heap-dump

## Addendum — accepted requirements and follow-on design gaps (owner responses 26 October 2026, including R01–R04)

**Authoritative decision record:** [SVREMOBILE_ARCHITECTURE_SECURITY_DECISIONS.md](SVREMOBILE_ARCHITECTURE_SECURITY_DECISIONS.md). Accepted product direction is not evidence of corresponding code implementation.

| ID | Status | Requirement / risk | Implementation dependency |
|---|---|---|---|
| **GAP-15 — offline host grant implementation** | 🟡 product choice resolved; engineering open | **R01 accepted:** no first-ever offline host. Creator must previously have signed into ServerRelay on same installation and satisfy a bounded device-bound offline entitlement. Exact TTL, logout/revocation, account switch and local proof mechanism remain security-design tasks. | AUTH-03, OFF-01, SEC-03 |
| **GAP-16 — offline local trust/policy** | ⬜ | Server ACLs and revoked refresh tokens cannot be queried offline. Use local encrypted room authority, QR/PIN + host approval + trusted-key records; separate cloud-only policy. | OFF-02..04, SYNC-02 |
| **GAP-17 — media modes & video** | ⬜ | **R04 accepted:** first 1:1 interactive audio, next host+two-listener one-way audio share; only host-approved guests in share mode. Video calls/camera-on upgrades later. Existing PCM transport is not accepted secure low-latency A/V; compare WebRTC/Opus/DTLS-SRTP and alternatives. | CALL-00..02, AUD-01..03, VID-00..03 |
| **GAP-18 — cloud reconnection** | ⬜ | Automatic token/session/policy refresh cannot occur when no server route exists. Network/captive LAN/multi-network selection and deferred reconciliation must not block peer media or spin loops. | ENV-02, SYNC-00..03, PERF-01 |
| **GAP-19 — Google Play release constraints** | ⬜ | `targetSdk=36` now; Play release will need microphone/camera/FGS permissions/declarations, user-visible background audio notification, production HTTPS, test tracks, 3-phone engineering acceptance (models/OS pending), potentially additional Play Console testers, and Android 17 local-network permission plan on target SDK 37+. | VID-02, SEC-05, CI-02, PROD-01..03 |
| **GAP-20 — deferred cross-Internet media** | ✅ product scope resolved; code deferred | **R02 accepted:** nearby hotspot/LAN media for first release; calls between different remote internet networks are not in v1. No TURN/SFU commitment until a separate future milestone. | CALL-00, AUD-01, VID-01, PROD-00 |
| **GAP-21 — background audio and role caps** | ⬜ | **R03/R04 accepted:** support audio with screen off/background only while OS/service policy allows; first authenticated 1:1 audio, then host+two listeners for one-way share, guest entry after manual host approval only in share mode. Must validate permissions, notification, service teardown, thermal/battery and session leakage across three phones (models/OS pending). | SEC-05, CALL-01, OFF-03, AUD-02, PERF-00, TEST-02 |

**Initial hosting:** local Docker Compose for ServerRelay and reachable private test endpoint; use TLS for any public/staging endpoint and isolate development cleartext from release. Do not treat GitHub student credits as an operational dependency. Review [student tooling](SVREMOBILE_STUDENT_DEVELOPER_PACK.md).

**Security invariant:** no recording of live media in the initial scope. Guest approval is scoped only to one-way audio share, is enforced by local host authority, and cannot be translated automatically into ServerRelay membership. Previous online authentication is a prerequisite for *offline hosting*, never perpetual cloud authorization. Backend `CallController` currently exposes a `CallSnapshot` lifecycle (`/api/v1/rooms/{roomId}/calls`, `/api/v1/calls/{callId}/*`); this does not in itself implement codec negotiation/video calling or offline guest signaling.

Android references: https://developer.android.com/develop/connectivity/wifi/localonlyhotspot ; https://developer.android.com/develop/connectivity/wifi/wifi-direct ; https://developer.android.com/develop/connectivity/wifi/nsd-wifi-direct ; https://developer.android.com/privacy-and-security/local-network-permission ; https://developer.android.com/develop/background-work/services/fgs/service-types
