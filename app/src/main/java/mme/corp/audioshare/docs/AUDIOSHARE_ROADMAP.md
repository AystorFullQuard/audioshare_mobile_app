# AudioShare — Android Client / Local Media Roadmap

**Type:** living, branch-agnostic project roadmap  
**Revision:** 2026-08-26  
**Strategy:** consume frozen ServeRelay contracts while preserving AudioShare's local-first/offline capability.

## Status legend

- ✅ **Complete** — required behavior/artifact exists and passed acceptance.
- 🟡 **In progress / partially complete** — implementation exists, but compatibility, design, or acceptance is open.
- ⬜ **Planned / not implemented**.
- ❌ **Confirmed blocker / broken contract**.

A parent stage is ✅ only when every mandatory child behavior for the current scope is ✅.

---

# 1. Product mission

AudioShare is one Android application with two intentionally different operating modes.

## Cloud-backed mode

- ✅ User authentication foundation exists.
- ✅ Session persistence/automatic access-token refresh foundation exists.
- 🟡 Device/bootstrap contract is currently incompatible with the newly refactored ServeRelay.
- ✅ Presence baseline exists.
- ✅ Rooms baseline exists.
- ⬜ PRIVATE Invite code/link/QR client.
- ⬜ CallSession client.
- ⬜ Realtime signaling client.
- ⬜ Remote media transport.

## Local-only / Offline Hotspot mode

- ✅ Audio capture/playback foundation exists.
- ✅ UDP sender/receiver foundation exists.
- ✅ UDP discovery prototype/foundation exists.
- ⬜ Explicit `LocalSession` runtime.
- ⬜ Local admission security.
- ⬜ Offline code/QR join.
- ⬜ Session-bound/authenticated/encrypted local media.
- ⬜ Real no-Internet hotspot acceptance.

## Authority boundary

```text
Cloud Room
→ ServeRelay authority

Offline LocalSession
→ host-device authority
```

- ⬜ AudioShare never pretends a fully offline LocalSession is a persisted ServeRelay Room.
- ⬜ Returning Internet never silently converts a LocalSession into a cloud Room.
- ⬜ UI clearly communicates whether the current session is cloud-backed or local-only.

---

# 2. Client architecture rules

## Required dependency direction

```text
UI / Compose
→ ViewModel
→ coordinator
→ repository
→ API / transport
```

## Architecture invariants

- ✅ Existing Auth/Session stack is reused.
- ✅ Existing Presence stack is reused.
- ✅ Existing `RoomRepository` is reused.
- ✅ Existing `RoomSessionCoordinator`/`RoomSessionState` are reused.
- ✅ Existing local audio/UDP foundation is preserved.
- ⬜ No second Retrofit for normal ServeRelay domain calls.
- ⬜ No second OkHttp authenticated stack.
- ⬜ No second `SessionManager`.
- ⬜ No parallel Room state machine.
- ⬜ Invite admission flows through existing Room lifecycle.
- ⬜ CallSession receives its own control-plane state rather than being mixed into Room state.
- ⬜ Media runtime is separated from Room/CallSession control state.
- ⬜ LocalSession receives its own authority/state without pretending to be cloud Membership.

## ARCH-AS-00 — Package/module cleanup

Target feature-oriented structure, incrementally:

```text
auth/
session/
device/
bootstrap/
presence/
room/
invite/
local/
call/
signaling/
media/
platform/
```

- ⬜ New features are added to feature/domain packages, not generic dumping-ground packages.
- ⬜ Existing code is moved only when a stage requires it; no giant package-only refactor.
- ⬜ Domain types do not depend on Compose/UI.
- ⬜ Repositories do not depend on Activity/Compose lifecycle.
- ⬜ Coordinators expose stable coroutine/Flow contracts.
- ⬜ Architecture dependency tests are added where valuable.

---

# 3. Current accepted foundation

## AUTH/SESSION baseline

- ✅ Login UI/client foundation.
- ✅ Session persistence.
- ✅ Access token storage.
- ✅ Refresh token storage.
- ✅ Auth interceptor.
- ✅ OkHttp authenticator baseline.
- ✅ Single-flight access-token refresh baseline.
- ✅ Original HTTP request retry baseline.
- ✅ Refresh rejection/session cleanup baseline.
- ✅ Cancellation tests around token refresh.
- ⬜ Production secret-storage/logging hardening.

## Presence baseline

- ✅ Presence API/repository.
- ✅ Heartbeat coordinator.
- ✅ Immediate heartbeat.
- ✅ Periodic heartbeat.
- ✅ Foreground/background runtime.
- ✅ ONLINE/OFFLINE/IN_ROOM baseline.
- ✅ Room lifecycle integration baseline.
- ✅ Reconciliation/polling work from corrective refactor baseline.
- ⬜ Final migration to ServeRelay Availability/Activity/Preference contract when frozen.

## Rooms baseline

- ✅ Rooms API/repository.
- ✅ RoomSessionCoordinator.
- ✅ RoomSessionReducer.
- ✅ Create.
- ✅ LOCAL_DISCOVERY join.
- ✅ Activate/Open.
- ✅ Deactivate/Back.
- ✅ Leave.
- ✅ Archive.
- ✅ Rooms UI.
- ✅ Room details UI.
- ✅ Members UI.
- ✅ stale-response/generation guards.
- ✅ duplicate-operation protection.
- ✅ Compose/navigation regression baseline.
- ⬜ Revalidate after final ServeRelay Device/Bootstrap/Presence freezes.

## Local media baseline

- ✅ Audio sender/receiver foundations.
- ✅ Foreground audio service foundation.
- ✅ UDP audio sender.
- ✅ UDP audio receiver.
- ✅ UDP discovery client.
- ✅ UDP discovery responder.
- ✅ Discovery protocol V1 foundation.
- ❌ Current UDP media is not production-secure: packet header is sequence/timestamp + raw PCM with no authentication/encryption.
- ❌ Current UDP discovery is not authorization.
- ⬜ LocalSession/admission/security must wrap existing transport.

---

# 4. ServeRelay contract-consumption policy

AudioShare should migrate against explicitly frozen backend domains.

## Required client process

- ⬜ Each backend contract freeze has an AudioShare compatibility stage.
- ⬜ Exact endpoint/JSON/error behavior is locked in MockWebServer tests.
- ⬜ AudioShare does not infer undocumented server behavior.
- ⬜ Client compatibility tests reference capabilities/contract semantics rather than backend implementation classes.
- ⬜ Breaking ServeRelay change triggers one planned migration stage rather than scattered ad-hoc fixes.
- ⬜ Existing working feature flows are not rewritten unless required by the new contract.

## Planned compatibility gates

```text
ServeRelay DEVICE/BOOTSTRAP freeze
→ AudioShare DEV-AS migration

ServeRelay ROOM/INVITE freeze
→ AudioShare Invite work

ServeRelay PRESENCE freeze
→ AudioShare Presence migration

ServeRelay CALLSESSION freeze
→ AudioShare CallSession

ServeRelay SIGNALING freeze
→ AudioShare WebSocket/signaling
```

---

# 5. Device installation identity / bootstrap migration

This is the first compatibility stage after the current ServeRelay device refactor.

## Target identity model

```text
installationId
→ generated by AudioShare
→ one per app installation
→ stable across users/logins on that install
→ stored internally
→ not hardware identity
→ not authentication proof

deviceId
→ returned by ServeRelay
→ UserDevice server ID
→ scoped to authenticated user/device row
```

## DEV-AS-00 — InstallationIdStore

- ⬜ Add app-install-scoped `InstallationIdStore`.
- ⬜ Generate UUID/GUID exactly once for a fresh install/data set.
- ⬜ Persist in app-internal storage.
- ⬜ Same `installationId` survives logout/login.
- ⬜ Same `installationId` is used for different accounts on the same app install.
- ⬜ Clearing app data/reinstall produces a new installation ID.
- ⬜ Do not derive from IMEI/MAC/serial/Android hardware identifier.
- ⬜ Do not use installationId as authorization proof.
- ⬜ Persistence/reset tests exist.

## DEV-AS-01 — Device registration endpoint migration

Current confirmed mismatch:

- ❌ Android currently calls `POST /api/v1/devices`.
- ❌ Updated ServeRelay expects `POST /api/v1/devices/register`.
- ❌ Android registration DTO currently lacks `installationId`.

Required:

- ⬜ `DevicesApi` uses `/api/v1/devices/register`.
- ⬜ Request includes installationId.
- ⬜ Request includes supported device metadata only.
- ⬜ Response maps server `deviceId`.
- ⬜ `deviceId` is persisted in existing user-scoped device store.
- ⬜ `DEVICE_IDENTITY_MISMATCH` mapped.
- ⬜ `DEVICE_LIMIT_REACHED` mapped.
- ⬜ Cancellation propagates.
- ⬜ MockWebServer asserts exact path/body.

## DEV-AS-02 — AuthSession binding startup

Target sequence for every newly issued login/register session:

```text
authenticated session
→ load/create installationId
→ POST /devices/register
→ session bound by ServeRelay
→ persist returned deviceId
→ bootstrap(installationId)
```

Required:

- ⬜ Newly logged-in session always performs idempotent device registration/binding before bootstrap.
- ⬜ Existing cached `deviceId` does not bypass binding of a new AuthSession.
- ⬜ Registering same install/account returns/reuses same server device.
- ⬜ Refreshing access token inside the same server AuthSession does not unnecessarily create/bind another device.
- ⬜ Logout/login binds the new session again.
- ⬜ Partial registration/bootstrap failure produces recoverable startup state.

## DEV-AS-03 — Bootstrap DTO migration

Current confirmed mismatch:

- ❌ Android bootstrap sends `deviceId`.
- ❌ Updated ServeRelay bootstrap expects `installationId`.

Required:

- ⬜ Bootstrap request sends installationId.
- ⬜ Bootstrap result current device maps back to server `deviceId`.
- ⬜ Presence/Room repositories continue using server deviceId where server endpoints require it.
- ⬜ Bootstrap no longer selects device based on client manufacturer/model metadata.
- ⬜ Bootstrap validation error mapping updated.
- ⬜ Bootstrap repository tests updated.

## DEV-AS-04 — Unlink/stale recovery

- ⬜ Current-device unlink causes session-expired/auth recovery according to backend contract.
- ⬜ Old cached deviceId cannot bypass server rejection.
- ⬜ Next valid login can re-register/reactivate installation according to frozen backend policy.
- ⬜ One-shot stale-device recovery is adapted or removed if no longer necessary.
- ⬜ Device limit errors have user-facing handling.
- ⬜ No infinite register/bootstrap retry loop.

## DEV-AS-05 — Compatibility acceptance

Automated:

- ⬜ InstallationIdStore tests.
- ⬜ DeviceRepository tests.
- ⬜ DeviceRegistrationCoordinator tests.
- ⬜ BootstrapRepository tests.
- ⬜ BootstrapStartupCoordinator tests.
- ⬜ ServeRelayCompatibilityFlowTest migrated to new contract.
- ⬜ Full Android unit suite.
- ⬜ Android instrumentation/lint/assemble.

Live:

- ⬜ Fresh app + fresh user.
- ⬜ Fresh app + existing user.
- ⬜ Logout/login same installation.
- ⬜ Access-token refresh.
- ⬜ Process/app restart.
- ⬜ Unlink current device.
- ⬜ Login after unlink.
- ⬜ Two accounts on same installation.
- ⬜ Device limit.
- ⬜ Bootstrap → Presence → Rooms.

Only after all required items are green:

- ⬜ Mark `AudioShare ↔ ServeRelay Device/Bootstrap compatible`.

---

# 6. Android security master plan

## SEC-AS-00 — Threat model

- ⬜ Classify password, access/refresh token, session ID, installationId, deviceId, Invite credentials, local-session keys, Room/Presence data and audio.
- ⬜ Trust boundaries documented: app process, storage, Android intents, clipboard, local LAN, Internet, ServeRelay, crash/analytics.
- ⬜ Threats include stolen token, malicious deep link/QR, another hotspot client, packet injection/replay, rooted/debuggable device risk.
- ⬜ MVP accepted risks vs production blockers documented.

## SEC-AS-01 — Logging

Current confirmed gaps:

- ❌ Debug HTTP client uses BODY logging.
- ❌ AuthRepository logs access-token prefix.
- ❌ AuthRepository logs refresh-token prefix.
- 🟡 SessionManager logs identifiers/token metadata that require privacy review.

Required before Invite:

- ⬜ Password never appears in Logcat.
- ⬜ Authorization header never appears in Logcat.
- ⬜ Access token never appears, including fragments.
- ⬜ Refresh token never appears, including fragments.
- ⬜ Raw Invite code never appears.
- ⬜ Invite URL credential never appears.
- ⬜ LocalSession key/join secret never appears.
- ⬜ Raw sensitive error body is redacted/avoided.
- ⬜ Privacy-safe logger abstraction used for lifecycle/network metadata.
- ⬜ Redaction/absence tests where practical.
- ⬜ Manual Logcat inspection gate for auth/refresh/bootstrap/Invite.

## SEC-AS-02 — Sensitive storage

Current confirmed gap:

- ❌ `allowBackup=true` is enabled while backup/data-extraction rule files are template/TODO.

Required:

- ⬜ Inventory DataStore/session preferences.
- ⬜ Explicit backup rules exclude or intentionally protect auth/session secrets.
- ⬜ Device-transfer behavior is decided.
- ⬜ Access/refresh token storage threat model reviewed.
- ⬜ Evaluate Android Keystore-backed key protection for long-lived secret material.
- ⬜ Raw Invite credential is transient and not persisted in ordinary app state.
- ⬜ LocalSession ephemeral key is erased on teardown.
- ⬜ Account switch/logout clears user-scoped secrets/state.

## SEC-AS-03 — Network security

Current confirmed gap:

- ❌ Network security config globally permits cleartext traffic.

Required:

- ⬜ Debug/local LAN cleartext policy isolated from release cloud policy.
- ⬜ Production ServeRelay uses HTTPS.
- ⬜ Production signaling uses WSS.
- ⬜ Release does not broadly permit cleartext cloud traffic.
- ⬜ No trust-all certificate manager.
- ⬜ No unsafe hostname verifier.
- ⬜ Certificate pinning only if justified operationally/threat-model-wise.
- ⬜ Invite credential is not leaked via unsafe telemetry/proxy logging.

## SEC-AS-04 — Android platform

- ⬜ Exported components reviewed.
- ⬜ Deep-link intents treated as untrusted input.
- ⬜ QR payload treated as untrusted input.
- ⬜ Permissions are minimal.
- ⬜ Clipboard behavior for Invite secrets reviewed.
- ⬜ Foreground-service declarations match actual media behavior.
- ⬜ Notification content privacy reviewed.
- ⬜ Debug-only endpoints/addresses not present in release behavior.
- ⬜ Release signing/key process documented.

## SEC-AS-05 — Dependency/supply chain

- ⬜ Dependency inventory.
- ⬜ Vulnerability scan in CI.
- ⬜ No unnecessary camera/QR dependency.
- ⬜ Google Code Scanner/ML Kit decision documented.
- ⬜ WebRTC dependency/source decision reviewed before integration.
- ⬜ Dependency update regression policy.
- ⬜ SBOM/release dependency report before production.

---

# 7. Presence migration to final backend model

Do this only after ServeRelay `PRESENCE CONTRACT FROZEN`.

## PRES-AS-00 — DTO/domain

Target:

```text
availability: ONLINE / OFFLINE
activity: NONE / IN_ROOM / STREAMING / WATCHING
preference: AUTO / DO_NOT_DISTURB / INVISIBLE (if V1)
```

- ⬜ Update bootstrap DTO.
- ⬜ Update heartbeat DTO.
- ⬜ Update Presence domain model.
- ⬜ Preserve backward compatibility only for explicitly supported server rollout window.
- ⬜ Remove legacy assumptions that membership implies activity.

## PRES-AS-01 — Runtime

- ⬜ Heartbeat controls liveness.
- ⬜ Room Activate controls IN_ROOM activity.
- ⬜ Call/media runtime controls STREAMING/WATCHING.
- ⬜ Back/deactivate returns activity to NONE/ONLINE projection.
- ⬜ Background/foreground restoration follows frozen server contract.
- ⬜ Terminal Presence error triggers reconciliation, not permanent silent shutdown.

## PRES-AS-02 — UI/privacy

- ⬜ Member list renders server projection.
- ⬜ User active in another Room appears ONLINE in current Room.
- ⬜ OFFLINE Membership remains visible when Membership is ACTIVE.
- ⬜ DND/Invisible behavior only implemented if backend contract exists.
- ⬜ UI never infers another PRIVATE Room identity.

## PRES-AS-03 — Tests

- ⬜ repository mappings.
- ⬜ heartbeat coordinator.
- ⬜ lifecycle manager.
- ⬜ foreground/background.
- ⬜ Room activation/deactivation.
- ⬜ other-Room privacy projection.
- ⬜ long-session token refresh.

---

# 8. Rooms final compatibility

Rooms are functionally implemented; this stage validates them against frozen backend V1 contracts.

## ROOM-AS-00 — API compatibility

- ✅ Create/list/get/members API baseline.
- ✅ LOCAL_DISCOVERY join baseline.
- ✅ Activate/deactivate baseline.
- ✅ Leave/archive baseline.
- ⬜ Exact final endpoints/DTOs match frozen ServeRelay.
- ⬜ Device/session requirements match final backend.
- ⬜ Error-code mappings match final backend.
- ⬜ Compatibility MockWebServer tests updated.

## ROOM-AS-01 — Coordinator invariants

- ✅ Create changes Membership only.
- ✅ Join changes Membership only.
- ✅ Open uses Activate.
- ✅ Back uses Deactivate.
- ✅ Back does not Leave.
- ✅ Multiple ACTIVE Memberships.
- ✅ One active Room context.
- ✅ stale-response protection.
- ✅ duplicate-operation protection.
- ⬜ Re-run after final Presence model.

## ROOM-AS-02 — Final Room acceptance

- ⬜ two-device create/join/open/back.
- ⬜ switch Rooms.
- ⬜ background/foreground.
- ⬜ leave/rejoin.
- ⬜ archive.
- ⬜ PRIVATE outsider privacy.
- ⬜ token refresh during long scenario.
- ⬜ no duplicate requests/navigation/pollers.

---

# 9. PRIVATE Invite client

Start after ServeRelay `INVITE CONTRACT FROZEN` and SEC-AS-01 logging gate.

## INV-AS-00 — Invite API/repository

- ⬜ Create Invite request/response DTO.
- ⬜ Join-by-code request/response DTO.
- ⬜ `RoomsApi.createInvite`.
- ⬜ `RoomsApi.joinByInviteCode`.
- ⬜ Extend existing `RoomRepository`.
- ⬜ Use server deviceId/session contract exactly as frozen.
- ⬜ Reuse existing Retrofit/Auth/ApiCallExecutor.
- ⬜ Preserve cancellation.
- ⬜ Exact path/body/status tests.
- ⬜ Error mapping tests.
- ⬜ No secret logging.

## INV-AS-01 — Coordinator admission

- ⬜ `joinByInviteCode` enters existing RoomSessionCoordinator flow.
- ⬜ Successful admission updates Membership/My Rooms.
- ⬜ Successful admission leaves Presence ONLINE.
- ⬜ Invite admission does not Activate/Open.
- ⬜ Duplicate submit does not create duplicate request/membership.
- ⬜ generation/lease protection applied.
- ⬜ Cancellation leaves state consistent.

## INV-AS-02 — Manual code UX

```text
Rooms
└── Join private room
    ├── Enter code
    └── Scan QR
```

- ⬜ Entry placement finalized.
- ⬜ Code input trimmed.
- ⬜ Blank code rejected locally.
- ⬜ Loading state.
- ⬜ Duplicate submit disabled.
- ⬜ IME/keyboard behavior.
- ⬜ No success navigation before server success.
- ⬜ Success returns/shows My Rooms.
- ⬜ Open remains explicit.
- ⬜ `INVITE_NOT_FOUND` UX.
- ⬜ `INVITE_EXPIRED` UX.
- ⬜ `INVITE_REVOKED` UX if V1.
- ⬜ `INVITE_USAGE_LIMIT_REACHED` UX.
- ⬜ `ROOM_ARCHIVED` UX.
- ⬜ validation/network retry UX.
- ⬜ ViewModel/Compose tests.

## INV-AS-03 — OWNER creation/sharing

```text
PRIVATE Room
└── Invite people
    ├── Expiration
    ├── Max uses
    └── Create Invite
        ├── QR
        ├── Copy code
        └── Share link
```

- ⬜ OWNER-only action visibility.
- ⬜ Default TTL UI.
- ⬜ Default maxUses UI.
- ⬜ Advanced setting bounds follow server.
- ⬜ Raw code shown only after successful creation.
- ⬜ Result screen/card.
- ⬜ Copy code.
- ⬜ Android Sharesheet.
- ⬜ QR presentation.
- ⬜ Create-another behavior.
- ⬜ Raw credential kept only in transient secret state.

## INV-AS-04 — Invite link/App Links

One cloud routing function:

```text
Invite URL
→ InviteLinkParser
→ inviteCode
→ same joinByInviteCode()
```

- ⬜ Versionable HTTPS URL format.
- ⬜ Owned production domain.
- ⬜ Parser validates scheme/host/path/version.
- ⬜ Parser rejects malformed/unsupported values.
- ⬜ Credential never logged.
- ⬜ Logged-out flow preserves pending Invite safely.
- ⬜ Cold-start routing.
- ⬜ Warm-app routing.
- ⬜ `android:autoVerify=true`.
- ⬜ `/.well-known/assetlinks.json`.
- ⬜ Release signing fingerprint.
- ⬜ App Link verification tests.
- ⬜ Development custom scheme, if used, remains non-production.

## INV-AS-05 — QR

Cloud QR encodes the same Invite link.

### Generate

- ⬜ Select QR renderer dependency.
- ⬜ Generate client-side.
- ⬜ No ServeRelay QR endpoint.
- ⬜ Code/copy/share remains accessible fallback.
- ⬜ QR rendering tested on compact/large screens.

### Scan

Preferred MVP: Google Code Scanner.

- ⬜ Integrate scanner.
- ⬜ App does not need CAMERA permission for MVP scanner.
- ⬜ Only supported QR payloads accepted.
- ⬜ Payload goes through same InviteLinkParser.
- ⬜ User confirmation before network admission.
- ⬜ Duplicate-scan protection.
- ⬜ Cancellation handling.
- ⬜ Same errors as manual/link flow.
- ⬜ If branded in-app camera is later required, evaluate ML Kit separately.

## INV-AS-06 — Invite acceptance

Automated:

- ⬜ repository.
- ⬜ coordinator.
- ⬜ ViewModel.
- ⬜ Compose.
- ⬜ parser.
- ⬜ pending-login routing.
- ⬜ QR scanner-result mapping.

Two-device:

- ⬜ OWNER creates PRIVATE.
- ⬜ outsider cannot see/join directly.
- ⬜ manual code admission.
- ⬜ link admission.
- ⬜ QR admission.
- ⬜ MEMBER/ACTIVE after admission.
- ⬜ Presence ONLINE before explicit Open.
- ⬜ Open → Activate → IN_ROOM.
- ⬜ repeated Open no Invite.
- ⬜ Leave follows frozen re-admission contract.
- ⬜ expiration/maxUses.
- ⬜ archive.
- ⬜ no secret in Logcat.

---

# 10. Offline LocalSession / hotspot

This is a first-class product track independent of ServeRelay availability.

## LOCAL-ADR-00 — LocalSession architecture

Target invariant:

```text
LocalSession != ServeRelay Room
```

- ⬜ Define LocalSession ID.
- ⬜ Define host authority.
- ⬜ Define participant identity.
- ⬜ Define HOST/SENDER/RECEIVER roles.
- ⬜ Define lifecycle STARTING/ACTIVE/ENDED as needed.
- ⬜ Define local join/admission protocol.
- ⬜ Define protocol version.
- ⬜ Define relationship to shared media abstractions.
- ⬜ Define behavior when Internet appears/disappears.
- ⬜ Define whether host requires cached cloud account or can operate purely local.
- ⬜ Define receiver account requirement.

Recommended receiver rule:

```text
No Internet
→ receiver may join with ephemeral local identity
→ no live ServeRelay login required
```

## LOCAL-01 — Network/hotspot

- ⬜ Existing Wi-Fi LAN supported.
- ⬜ User-created Android hotspot supported.
- ⬜ Existing UDP broadcast discovery characterized on common hotspots.
- ⬜ Evaluate Android LocalOnlyHotspot for supported versions.
- ⬜ Permission matrix by Android version.
- ⬜ No assumption of upstream Internet.
- ⬜ Detect network/hotspot loss.
- ⬜ Correct behavior with cellular + Wi-Fi simultaneously.
- ⬜ Background/foreground handling.
- ⬜ Host IP/route changes handled.

## LOCAL-02 — Discovery

Current prototype:

```text
AUDIOSHARE_DISCOVER_V1
→ receiver name/IP/audioPort
```

Required:

- ✅ Discovery client exists.
- ✅ Discovery responder exists.
- ✅ Basic protocol version marker exists.
- ❌ Discovery response currently has no authentication.
- ⬜ Discovery result is only a candidate host/session.
- ⬜ NSD/mDNS is evaluated against existing broadcast approach.
- ⬜ Duplicate/disappearing hosts handled.
- ⬜ Discovery timeout/error UX.
- ⬜ Manual join fallback.
- ⬜ QR join fallback.

## LOCAL-03 — Typed local/cloud join payload

Use one scanner UI, different authority:

```text
JoinPayloadParser
→ CloudInvite
OR
→ LocalSessionInvite
```

- ⬜ Payload has explicit type/version.
- ⬜ Cloud payload routes to ServeRelay Invite.
- ⬜ Local payload routes to host-local admission.
- ⬜ Parser rejects ambiguous payload.
- ⬜ Local payload includes session identifier.
- ⬜ Local payload includes ephemeral bootstrap/join secret where required.
- ⬜ Local payload may include discovery hint, not trusted media endpoint without verification.
- ⬜ Malformed/oversized payload tests.

## LOCAL-04 — Authenticated local handshake

- ⬜ Cryptographically random session secret.
- ⬜ Join challenge/response or equivalent authenticated bootstrap.
- ⬜ Discovery alone cannot authorize participant.
- ⬜ Participant is bound to LocalSession.
- ⬜ Stale join credentials rejected.
- ⬜ Replay of admission message rejected.
- ⬜ Host can reject/close participant.
- ⬜ Session keys erased on end.
- ⬜ No static shared app-wide transport secret.

## LOCAL-05 — Harden existing UDP media

Current packet:

```text
sequence:int
timestamp:long
PCM bytes
```

Required before production:

- ⬜ Packet identifies LocalSession/media stream.
- ⬜ Packet authenticates sender/session.
- ⬜ Encryption decision documented and implemented if required by threat model (recommended).
- ⬜ Unique nonce/sequence strategy.
- ⬜ Replay window.
- ⬜ Cross-session packet rejection.
- ⬜ Malformed/short packet rejected without crash.
- ⬜ Oversized packet rejected.
- ⬜ Unauthorized host on same LAN cannot inject accepted audio.
- ⬜ Unauthorized receiver cannot join/decrypt media.
- ⬜ Jitter/out-of-order strategy.
- ⬜ Socket/thread shutdown is deterministic.
- ⬜ Packet buffers avoid unnecessary allocations where performance requires.

## LOCAL-06 — Local media UX

- ⬜ Start Local Session action.
- ⬜ Host status screen.
- ⬜ Receiver discovery list.
- ⬜ Join by discovery.
- ⬜ Join by code.
- ⬜ Join by QR.
- ⬜ Participant count/state.
- ⬜ End session.
- ⬜ Hotspot/network lost UI.
- ⬜ Reconnecting local UI.
- ⬜ Cloud unavailable is not shown as fatal while LocalSession is healthy.
- ⬜ Clear label that LocalSession is ephemeral/local.

## LOCAL-07 — No-Internet acceptance

Environment:

```text
ServeRelay unreachable
Internet OFF
```

Device A:

- ⬜ create/use hotspot.
- ⬜ start LocalSession.
- ⬜ advertise discovery.
- ⬜ expose local code/QR.
- ⬜ send audio.

Device B:

- ⬜ connect to same hotspot.
- ⬜ discover or scan code/QR.
- ⬜ authenticate locally.
- ⬜ receive audio.

Negative/reliability:

- ⬜ multiple receivers.
- ⬜ third unauthorized client.
- ⬜ injection attempt.
- ⬜ replay attempt.
- ⬜ hotspot stop/restart.
- ⬜ app background/foreground.
- ⬜ screen rotation/recreation.
- ⬜ host network change.
- ⬜ no ServeRelay request required for live local media.

---

# 11. CallSession client

Begin after ServeRelay `CALLSESSION CONTRACT FROZEN`.

## CALL-AS-00 — API/repository

- ⬜ CallSession DTOs.
- ⬜ start/join/leave/end/get/current API.
- ⬜ repository.
- ⬜ exact path/body/error tests.
- ⬜ cancellation.
- ⬜ token/device/session integration.

## CALL-AS-01 — Coordinator/state

- ⬜ `CallSessionCoordinator`.
- ⬜ `CallSessionState`.
- ⬜ reducer/transition model.
- ⬜ one current active Call context.
- ⬜ start.
- ⬜ join.
- ⬜ leave.
- ⬜ end.
- ⬜ participant snapshots.
- ⬜ generation/stale-response protection.
- ⬜ duplicate-action guards.
- ⬜ Room archive/leave cleanup.
- ⬜ logout cleanup.

## CALL-AS-02 — Basic UI

- ⬜ Start Call for allowed host.
- ⬜ Join active Call.
- ⬜ Participant list.
- ⬜ Leave.
- ⬜ End.
- ⬜ loading/error states.
- ⬜ restoration/reconnect state.

---

# 12. WebSocket signaling client

Begin after ServeRelay `SIGNALING V1 CONTRACT FROZEN`.

## WS-AS-00 — Connection runtime

- ⬜ authenticated connect.
- ⬜ connection state Flow.
- ⬜ disconnect.
- ⬜ ping/pong handling.
- ⬜ bounded reconnect/backoff.
- ⬜ lifecycle-aware process behavior.
- ⬜ logout disconnect.
- ⬜ access/session expiration recovery.

## WS-AS-01 — Subscription runtime

- ⬜ Room subscription.
- ⬜ Call subscription.
- ⬜ resubscribe after reconnect.
- ⬜ unsubscribe on leave/archive/call end.
- ⬜ no duplicate subscription jobs.

## WS-AS-02 — Event processing

- ⬜ event envelope parser.
- ⬜ version validation.
- ⬜ duplicate-event suppression/idempotency.
- ⬜ ordering/reconciliation strategy.
- ⬜ CALL_STARTED.
- ⬜ PARTICIPANT_JOINED.
- ⬜ PARTICIPANT_LEFT.
- ⬜ CALL_ENDED.
- ⬜ ROOM_ARCHIVED.
- ⬜ Membership/Presence updates.
- ⬜ unknown event safe handling.

## WS-AS-03 — Snapshot + event reconciliation

- ⬜ Initial REST snapshot before/with subscription.
- ⬜ Reconnect refreshes snapshot where needed.
- ⬜ Event gap cannot permanently corrupt client state.
- ⬜ Late event cannot overwrite newer snapshot/generation.

---

# 13. Media-session architecture

## MEDIA-AS-00 — Common abstraction

Target:

```text
Cloud:
Room → CallSession → MediaSession

Local:
LocalSession → MediaSession
```

- ⬜ MediaSession has stable lifecycle.
- ⬜ Media role HOST/SENDER/RECEIVER independent of UI screen implementation.
- ⬜ Media transport does not own Room/Call membership.
- ⬜ Control-plane can end media.
- ⬜ Media failure does not silently mutate cloud Membership.
- ⬜ Foreground service owns active media runtime as required.

## MEDIA-AS-01 — Presence binding

Cloud only:

- ⬜ sender active → STREAMING.
- ⬜ receiver active → WATCHING.
- ⬜ media stops but Room remains open → IN_ROOM.
- ⬜ Room closes/deactivates → NONE/ONLINE projection.
- ⬜ Local-only activity does not fake ServeRelay Presence unless product explicitly defines a cloud projection.

---

# 14. Remote media transport ADR and WebRTC candidate

## MEDIA-ADR-01 — Decision

- ⬜ latency target.
- ⬜ participant topology.
- ⬜ codec requirements.
- ⬜ NAT traversal.
- ⬜ encryption.
- ⬜ TURN cost.
- ⬜ reconnect/network transition.
- ⬜ battery impact.
- ⬜ instrumentation/metrics.
- ⬜ WebRTC selected/rejected with rationale.

Expected direction:

```text
LAN/offline → hardened UDP
Remote → WebRTC
ServeRelay → signaling only
```

## WEBRTC-AS-00 — Integration if selected

- ⬜ dependency selected.
- ⬜ PeerConnection lifecycle.
- ⬜ SDP offer/answer.
- ⬜ ICE candidates.
- ⬜ STUN config.
- ⬜ TURN credentials/config.
- ⬜ codec negotiation.
- ⬜ participant mapping.
- ⬜ media track lifecycle.
- ⬜ reconnect.
- ⬜ Wi-Fi ↔ cellular transition.
- ⬜ security verification.
- ⬜ stats/latency/packet-loss telemetry.
- ⬜ remote two-device E2E.
- ⬜ remote multi-network E2E.

---

# 15. Audio/media UX

## AUDIO-UX-00 — Playback/capture correctness

- ✅ capture foundation.
- ✅ playback foundation.
- ⬜ sample rate/channel/encoding contract centralized.
- ⬜ underrun/overrun behavior measured.
- ⬜ audio focus handled.
- ⬜ route changes handled.
- ⬜ speaker/earpiece policy.
- ⬜ wired headset.
- ⬜ Bluetooth.
- ⬜ interruption/call handling.
- ⬜ microphone permission denial UX.

## AUDIO-UX-01 — Latency/jitter

- ⬜ latency measurement.
- ⬜ jitter buffer strategy.
- ⬜ packet loss strategy.
- ⬜ out-of-order packet strategy.
- ⬜ startup buffering.
- ⬜ synchronization policy when multiple receivers matter.
- ⬜ performance/battery profiling.

## AUDIO-UX-02 — Controls

- ⬜ sender start/stop.
- ⬜ receiver play/stop.
- ⬜ mute.
- ⬜ volume.
- ⬜ role/status indicator.
- ⬜ network/quality indicator.
- ⬜ recoverable error state.

---

# 16. Notifications

## NOTIF-AS-00 — Runtime

- ⬜ notification permission strategy.
- ⬜ foreground media notification.
- ⬜ call started/available notification if product requires.
- ⬜ Invite delivery notification only if backend/product later adds it.
- ⬜ deep-link actions route safely.
- ⬜ notification does not expose PRIVATE sensitive metadata on lock screen without policy.
- ⬜ duplicate notification suppression.
- ⬜ logout cleanup.

---

# 17. Cloud connectivity/reliability

This is separate from fully offline LocalSession mode.

## NET-AS-00 — Connectivity state

- ⬜ Connectivity observer.
- ⬜ Cloud reachable/unreachable state.
- ⬜ Local network state separate from Internet state.
- ⬜ UI does not equate "no Internet" with "no local LAN".

## NET-AS-01 — Retry policy

- ✅ Token retry baseline exists.
- ⬜ Domain-specific retry policy.
- ⬜ Exponential backoff with jitter where appropriate.
- ⬜ No retries for deterministic validation/authz errors.
- ⬜ No retry storms from multiple coordinators.
- ⬜ Manual retry.
- ⬜ Cancellation stops retry.

## NET-AS-02 — Reconciliation

- ⬜ Bootstrap/session reconciliation.
- ⬜ Device reconciliation.
- ⬜ Presence reconciliation.
- ⬜ Room reconciliation.
- ⬜ Invite partial-success handling.
- ⬜ CallSession reconciliation.
- ⬜ WebSocket snapshot/event reconciliation.
- ⬜ Process restart recovery.

---

# 18. Android testing strategy

## TEST-AS-00 — Unit

- ✅ Repository unit/MockWebServer foundation.
- ✅ Room coordinator/reducer tests.
- ✅ Presence coordinator/lifecycle tests.
- ✅ Token authenticator tests.
- ⬜ New InstallationId tests.
- ⬜ Invite tests.
- ⬜ LocalSession protocol/security tests.
- ⬜ CallSession tests.
- ⬜ signaling tests.
- ⬜ media-session state tests.

## TEST-AS-01 — Compose/ViewModel/navigation

- ✅ Rooms baseline.
- ⬜ Invite screens.
- ⬜ App Link routing.
- ⬜ QR result flow.
- ⬜ LocalSession screens.
- ⬜ CallSession screens.
- ⬜ media lifecycle screens.
- ⬜ configuration-change duplicate-action tests.

## TEST-AS-02 — Instrumentation/device

- ✅ current Android instrumentation baseline.
- ⬜ login/device/bootstrap on real server.
- ⬜ Invite QR scan.
- ⬜ App Link verification.
- ⬜ foreground/background media.
- ⬜ hotspot/LAN local media.
- ⬜ notification flows.
- ⬜ Bluetooth/audio routes where feasible.

## TEST-AS-03 — Cross-project

- ⬜ Device/Bootstrap compatibility.
- ⬜ Rooms final.
- ⬜ Invite.
- ⬜ Presence final.
- ⬜ CallSession.
- ⬜ signaling.
- ⬜ remote media.

## Standard gate

```bat
gradlew.bat :app:testDebugUnitTest --rerun-tasks --console=plain
gradlew.bat :app:assembleDebugAndroidTest --rerun-tasks --console=plain
gradlew.bat :app:connectedDebugAndroidTest --rerun-tasks --console=plain
gradlew.bat :app:lintDebug :app:assembleDebug --rerun-tasks --console=plain

git diff --check
git status --short
```

Local-network/media stages require real devices; emulator-only success is insufficient.

---

# 19. Performance and battery

## PERF-AS-00 — Networking

- ⬜ Heartbeat interval is release-safe.
- ⬜ Temporary member polling removed/reduced after realtime events exist.
- ⬜ No duplicate collectors/network loops.
- ⬜ Retry loops stop in background where appropriate.
- ⬜ Large logs disabled in release.

## PERF-AS-01 — Media

- ⬜ CPU during capture/send measured.
- ⬜ CPU during receive/playback measured.
- ⬜ Memory/buffer allocation measured.
- ⬜ Battery drain on 30/60 minute local session measured.
- ⬜ Battery drain on remote session measured.
- ⬜ Network bytes/second measured.
- ⬜ Hotspot host thermal behavior measured.

---

# 20. Production Android readiness

## PROD-AS-00 — Build/release

- ⬜ Release build type finalized.
- ⬜ R8/ProGuard rules.
- ⬜ Signing setup.
- ⬜ CI artifact generation.
- ⬜ Versioning strategy.
- ⬜ Play/internal testing pipeline.
- ⬜ Debug server IP/config excluded from release.

## PROD-AS-01 — Privacy/security

- ⬜ SEC-AS master plan green.
- ⬜ privacy policy/data inventory.
- ⬜ permissions justified.
- ⬜ backup rules finalized.
- ⬜ crash/analytics redaction tested.
- ⬜ dependency scan green.
- ⬜ release cleartext disabled for cloud endpoints.

## PROD-AS-02 — Accessibility/UX

- ⬜ content descriptions.
- ⬜ touch target sizes.
- ⬜ screen reader flows.
- ⬜ error states understandable.
- ⬜ dynamic text/layout checks.
- ⬜ offline/local/cloud state unambiguous.

## PROD-AS-03 — Full acceptance

Cloud:

- ⬜ fresh install/register/login.
- ⬜ device/bootstrap.
- ⬜ Presence/Rooms.
- ⬜ PRIVATE Invite code/link/QR.
- ⬜ CallSession/signaling.
- ⬜ remote media if V1.

Offline:

- ⬜ no-Internet hotspot.
- ⬜ local discovery/code/QR.
- ⬜ multi-receiver media.
- ⬜ local security negative tests.

Reliability:

- ⬜ access-token expiry.
- ⬜ process restart.
- ⬜ background/foreground.
- ⬜ Wi-Fi/cellular transition.
- ⬜ server outage.
- ⬜ hotspot loss.

Release:

- ⬜ lint.
- ⬜ unit/instrumentation.
- ⬜ battery/performance.
- ⬜ security review.
- ⬜ staged rollout plan.
- ⬜ rollback plan.

---

# 21. High-level dashboard

```text
✅ Auth/session client baseline
✅ Presence baseline
✅ Rooms baseline
✅ Local audio/UDP/discovery prototype baseline

❌ Current Device/Bootstrap contract incompatible with updated ServeRelay
⬜ Android security hardening
⬜ Final Presence migration
⬜ Final Rooms compatibility
⬜ PRIVATE Invite code/link/QR
⬜ Offline LocalSession/hotspot core
⬜ CallSession client
⬜ WebSocket signaling client
⬜ MediaSession binding
⬜ Hardened local UDP
⬜ Remote WebRTC candidate
⬜ Audio/media UX hardening
⬜ Notifications
⬜ Cloud reliability
⬜ Production Android readiness
```

---

# 22. Current execution order

AudioShare should not chase every intermediate backend refactor. Recommended order:

```text
Wait for ServeRelay Device/Bootstrap freeze
→ DEV-AS-00..05 compatibility migration
→ SEC-AS-01 logging gate
→ wait/consume Invite freeze
→ INV-AS
→ wait/consume final Presence freeze
→ PRES-AS + Room final regression
→ Offline LocalSession track can proceed where independent
→ wait/consume CallSession freeze
→ CALL-AS
→ wait/consume signaling freeze
→ WS-AS
→ media binding
→ hardened local UDP
→ remote media/WebRTC
→ media UX/notifications/reliability
→ production acceptance
```

Offline LocalSession work may progress independently from later ServeRelay CallSession/signaling
as long as shared media abstractions are designed so the two paths can converge cleanly.

---

# 23. Official engineering references

- Android app-scoped identifiers:
  https://developer.android.com/identity/user-data-ids
- Android Network Security Configuration:
  https://developer.android.com/privacy-and-security/security-config
- Android Keystore:
  https://developer.android.com/privacy-and-security/keystore
- Android App Links:
  https://developer.android.com/training/app-links/about
- App Link verification:
  https://developer.android.com/training/app-links/verify-applinks
- Google Code Scanner:
  https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner
- Android Sharesheet:
  https://developer.android.com/training/sharing/send
- Android LocalOnlyHotspot:
  https://developer.android.com/develop/connectivity/wifi/localonlyhotspot
- Android NSD:
  https://developer.android.com/reference/android/net/nsd/NsdManager
- OWASP MASVS:
  https://mas.owasp.org/MASVS/
