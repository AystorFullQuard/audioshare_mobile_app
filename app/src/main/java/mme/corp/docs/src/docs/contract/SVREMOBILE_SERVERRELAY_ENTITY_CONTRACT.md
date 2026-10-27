# ServerRelay boot/entity → SvReMobile entity contract (27 Oct 2026)

**Purpose:** First entity-only foundation. Source: supplied ServerRelay 26 Oct `boot/src/main/java/sv/re/entity/*.java` and public contracts in `dto/src/main/java/sv/re/dto/`; target: supplied SvReMobile 27 Oct. **Entity-only foundation originally.** Subsequent GAP-01/02 work updated registration endpoint/DTO/coordinator, not entity snapshots.

The new `app/src/main/java/mme/corp/audioshare/data/entity/` contains immutable Kotlin application snapshots. These are **not** Android Room database annotations, backend JPA replicas, API JSON DTOs, or adapters. Future DTO → entity mappers must be explicit, tested and added in their own GAP stage; existing `data/model/room/Room` stays in use until then.

## Server entity → mobile entity mapping

| ServerRelay entity | SvReMobile snapshot | Available public response / deliberate exclusions |
|---|---|---|
| `UserEntity` | `UserEntity` | `CurrentUser` exposes id/email/phone, verification flags; account status/timestamps may be unknown. Never copy `password_hash`, failed-login counters, lockout/suspension internals. |
| `UserProfileEntity` | `UserProfileEntity` | `CurrentUser` exposes username/displayName/avatarURL; bio/locale/timezone are optional future fields. |
| `UserSettingsEntity` | `UserSettingsEntity` | `CurrentUser.settings` exposes theme/discovery/last-seen/notifications/audio-quality; `userId` can come from owning `CurrentUser`. |
| `UserDeviceEntity` | `UserDeviceEntity` | `UserDevice` and bootstrap `DeviceSummary` expose id/name/platform/version/lastSeenAt; `installationId` is distinct client-generated ID, not the server `id`. No push token or last IP in entity. |
| `AuthSessionEntity` | `AuthSessionEntity` (client **summary only**) | Only session flags, optional refresh expiration and linked user/device IDs. No token fields, token hashes, JPA session ID/family ID, rotation history. Actual credentials remain in existing token storage. |
| `RoomEntity` | `RoomEntity` | `RoomRs` supplies persistent metadata and owner IDs, not audio/call state. |
| `RoomMemberEntity` | `RoomMemberEntity` | `RoomMemberRs` supplies user-based membership ID, role/state, times; temporary host/receiver roles must NOT be stored as membership roles. |
| `RoomInviteEntity` | `RoomInviteEntity` | Invite metadata only. `RoomInviteRs` includes a **single-use sensitive inviteCode** on creation; never store its raw code in a general entity, and never expose server `codeHash`. |
| `UserPresenceEntity` | `UserPresenceEntity` | Bootstrap `Presence` has independent `availability`, `activity`, `preference`, optional room ID and timestamps; it is user-scoped, not per-device. |

## Type and relationship rules

- **IDs:** UUIDs represented as `String` at this mobile layer to match existing Kotlin DTOs; IDs refer to separate entities instead of nested recursive object graphs. `device.id` (backend UUID) is not `installationId` (app installation identity).
- **Time:** ISO-compatible strings at this stage; backend exposes `LocalDateTime` for persistence/response metadata and `Instant` for token expiry. Later transport adapters must define parsing and clock/zone behavior, not guess timezones from local dates.
- **Enums:** `UserStatus`, `DeviceStatus`, `DevicePlatform`, `AudioQuality`, `RoomStatus`, `RoomVisibility`, `RoomMemberRole`, `RoomMemberState`, `PresenceAvailability`, `PresenceActivity`, and `PresencePreference` match the 26 Oct backend enum names exactly. Entity enum types intentionally do not depend on Retrofit DTO enums.
- **Unknown fields:** Nullable snapshot fields represent *not supplied*, **not authorized** and not confirmed false. Do not infer account status from missing values.
- **Offline:** neither a `UserEntity` nor `AuthSessionEntity` is a trusted offline grant or proof of live backend authorization. Dedicated device-bound security design remains GAP-15/16.
- **Memory:** immutable small metadata objects; no JPA graphs, contexts, socket references, credentials, raw audio buffers or media session state are held here. This avoids reference cycles, but does not itself prove zero leaks.

## Endpoint / DTO readiness after the entity milestone

| Existing mobile DTO/contract | 26 Oct ServerRelay contract | Future GAP |
|---|---|---|
| Registration request now includes stable per-install `installationId` and server response maps `deviceId/userId`; backend E2E pending | `RegisterDeviceRq` requires `installationId`; `DevicesController` returns `DeviceResult` with `deviceId`/`userId` | GAP-01/02 🟡 |
| Mobile bootstrap request uses `deviceId`, manufacturer/model/platform fields and response contains rooms/presence only | `SessionBootstrapRq` requires `installationId`; `SessionBootstrapRs` contains `session,user,configuration,capabilities,devices,rooms,presence,server` | GAP-04 |
| Mobile `PresenceState` merges online/offline with in-room/streaming | `PresenceAvailability`, `PresenceActivity`, and `PresencePreference` are separate | GAP-05 |
| Mobile login, refresh and CSRF paths/handling require E2E proof | Backend login/CSRF protection and session contracts are authoritative | GAP-06 |

## Acceptance and verification

- 🟡 GAP-22: Kotlin entity-source compilation completed (`kotlinc` 1.9.0, JVM); new JUnit entity contract tests authored but not executed with Android Gradle in this environment. Enum values checked against the backend source; full app and server integration are NOT verified.
- 🟡 GAP-01/02 registration route and payload now align with backend source, but live authenticated API acceptance is not yet proven. Bootstrap, login/CSRF, and presence still require fixes.
- Subsequent work may map DTOs into entities while keeping controllers, repositories and UI behavior stable through regression tests. No new runtime dependency is required for these entities.
