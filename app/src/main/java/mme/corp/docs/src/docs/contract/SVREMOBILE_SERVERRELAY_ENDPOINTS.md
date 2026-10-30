# ServerRelay ↔ SvReMobile — HTTP endpoint register

**Source of truth:** `ServerRelay/docs/src/main/docs/api/openapi.yaml` (OpenAPI 3.1) cross-checked with `ServerRelay/boot/.../controller/` and the Android Retrofit interfaces. **Scope:** HTTP v1; WebSocket/UDP transport contracts tracked separately.

**Implementation status meanings:** `✅ Endpoint declared` = route exists in the mobile Retrofit interface (not proof of successful end-to-end response or production acceptance); `🟡` = newly aligned behavior, E2E pending; `⬜` = not wired in the mobile app. **No backend endpoint is assumed implemented merely because it exists in Swagger.**

## Local phone login and room-list flow (development)

1. Launch **SvRe Home** as a guest; tapping the top-right avatar opens Login for guests and **My Profile** for authenticated users (login/bootstrap user details and Sign out). `POST /api/v1/auth/login` reuses `AuthRepository` → `AuthApi` and receives the backend `{ session, user }` JSON. The shared OkHttp client first obtains `GET /api/v1/auth/csrf` and pairs the cookie with `X-XSRF-TOKEN` on the login POST.
2. A successful login encrypts session tokens with AndroidKeyStore and returns to Home. Home calls the existing `BootstrapStartupCoordinator` (device registration, session bootstrap, room restoration and presence heartbeat), without a second repository or an extra polling loop. `My Rooms` displays the authorized rooms returned by the actual server, never placeholders.
3. Subsequent protected requests use the access token as `Authorization: Bearer <JWT>` via `AuthInterceptor`. A 401 may trigger the bounded token refresh path. Do not manually set JWT in UI fields.
4. For a physical device, configure your PC **private IPv4 in two marked debug-only places**: `gradle.properties` (`serverRelayDebugBaseUrl`) and `app/src/debug/res/xml/network_security_config.xml` (matching `<domain>`). Keep HTTP LAN debugging on a trusted network only; release is HTTPS-only. Emulator default is `http://10.0.2.2:8090/`.

**Test gate:** On a real device confirm CSRF GET → login POST → device registration POST → bootstrap POST → heartbeat POST → My Rooms. Passing JVM tests alone does not prove this chain. The requested `error.txt` was not available with this baseline; no diagnosis of its contents is asserted.

## Authentication and transport

- **Protected REST:** send `Authorization: Bearer <access JWT>` (exact header, never query string or body token). `AuthInterceptor` adds it centrally, `TokenAuthenticator` retries at most once after a successful no-bearer refresh. Do not include the JWT for public login/register/refresh/CSRF preflight.
- **CSRF for unsafe methods:** `GET /api/v1/auth/csrf` first; retain the host-only `XSRF-TOKEN` cookie (HttpOnly), and send the *masked* value returned as `token` in `X-XSRF-TOKEN` header on POST/PUT/PATCH/DELETE. CSRF does **not** authenticate a user; protected writes still need the Bearer header. Uses one volatile origin-bound cookie jar shared by auth and refresh clients. On a CSRF 403, invalidate cached proof and do not automatically replay the write.
- **URLs:** backend default `http://localhost:8090` for host-side tests; emulator uses `http://10.0.2.2:8090/`; a real Android device needs a reachable Docker/host LAN IP. Release URL is HTTPS-only. Do not put refresh/access tokens in logs or URL parameters.
- **Acceptance:** owner reports all JVM and Android tests passing and local ServerRelay login successful. This covers the previously implemented client authentication/storage policy, not new signaling/room control E2E or production TLS. Authentication → registration → bootstrap → presence must be exercised against the *same* ServerRelay instance for final gate closure.

## OpenAPI operation matrix

| Method and endpoint | Authentication | CSRF (unsafe verbs) | Request → response model / HTTP codes | Android implementation | Status |
|---|---|---|---|---|---|
| `GET /actuator/health` | No | — | `—` → `ActuatorHealth` / 200, 503 | — | ⬜ Not in mobile scope yet |
| `GET /actuator/health/liveness` | No | — | `—` → `HealthProbe` / 200, 503 | — | ⬜ Not in mobile scope yet |
| `GET /actuator/health/readiness` | No | — | `—` → `HealthProbe` / 200, 503 | — | ⬜ Not in mobile scope yet |
| `GET /api/v1/auth/csrf` | No | — | `—` → `CsrfTokenResponse` / 200, 400, 401, 406, 413, 415 | network/retrofit/CsrfProtection.kt | 🟡 Auto (CSRF GET) |
| `POST /api/v1/auth/register` | No | Cookie + X-XSRF-TOKEN | `RegisterRequest` → `LoginResponse` / 403, 200, 400, 409, 429, 503, 406, 413, 415 | — | ⬜ Not in mobile scope yet |
| `POST /api/v1/auth/login` | No | Cookie + X-XSRF-TOKEN | `LoginRequest` → `LoginResponse` / 403, 200, 400, 401, 429, 503, 406, 413, 415 | AuthApi.kt | ✅ Endpoint declared |
| `POST /api/v1/auth/refresh` | No | Cookie + X-XSRF-TOKEN | `RefreshTokenRequest` → `LoginResponse` / 403, 200, 400, 401, 406, 413, 415 | AuthApi.kt | ✅ Endpoint declared |
| `GET /api/v1/auth/me` | Bearer JWT | — | `—` → `CurrentUser` / 200, 401, 400, 406, 413, 415 | — | ⬜ Not in mobile scope yet |
| `POST /api/v1/auth/logout` | Bearer JWT | Cookie + X-XSRF-TOKEN | `—` → `—` / 204, 401, 400, 406, 413, 415 | AuthApi.kt | ✅ Endpoint declared |
| `POST /api/v1/auth/dev-register` | No | Cookie + X-XSRF-TOKEN | `—` → `LoginResponse` / 403, 200, 404, 400, 406, 413, 415 | — | ⬜ Not in mobile scope yet |
| `POST /api/v1/auth/dev-login` | No | Cookie + X-XSRF-TOKEN | `JSON` → `LoginResponse` / 403, 200, 400, 404, 406, 413, 415 | — | ⬜ Not in mobile scope yet |
| `POST /api/v1/devices/register` | Bearer JWT | Cookie + X-XSRF-TOKEN | `RegisterDeviceRequest` → `DeviceResult` / 201, 400, 401, 403, 409, 429, 503, 406, 413, 415 | DevicesApi.kt | ✅ Endpoint declared |
| `GET /api/v1/devices` | Bearer JWT | — | `—` → `—` / 200, 401, 400, 406, 413, 415 | — | ⬜ Not in mobile scope yet |
| `DELETE /api/v1/devices/{deviceId}` | Bearer JWT | Cookie + X-XSRF-TOKEN | `—` → `—` / 204, 401, 403, 404, 400, 406, 413, 415 | — | ⬜ Not in mobile scope yet |
| `POST /api/v1/session/bootstrap` | Bearer JWT | Cookie + X-XSRF-TOKEN | `SessionBootstrapRequest` → `SessionBootstrapResponse` / 200, 400, 401, 403, 406, 413, 415 | BootstrapApi.kt | ✅ Endpoint declared |
| `GET /api/v1/rooms` | Bearer JWT | — | `—` → `—` / 200, 401, 400, 406, 413, 415 | RoomsApi.kt | ✅ Endpoint declared |
| `POST /api/v1/rooms` | Bearer JWT | Cookie + X-XSRF-TOKEN | `CreateRoomRequest` → `RoomResponse` / 201, 400, 401, 403, 406, 413, 415 | RoomsApi.kt | ✅ Endpoint declared |
| `POST /api/v1/rooms/join-by-code` | Bearer JWT | Cookie + X-XSRF-TOKEN | `JoinRoomByCodeRequest` → `RoomResponse` / 200, 400, 401, 404, 409, 429, 503, 406, 413, 415 | — | ⬜ Not in mobile scope yet |
| `GET /api/v1/rooms/{roomId}` | Bearer JWT | — | `—` → `RoomResponse` / 200, 401, 403, 404, 400, 406, 413, 415 | RoomsApi.kt | ✅ Endpoint declared |
| `GET /api/v1/rooms/{roomId}/members` | Bearer JWT | — | `—` → `—` / 200, 401, 403, 404, 400, 406, 413, 415 | RoomsApi.kt | ✅ Endpoint declared |
| `POST /api/v1/rooms/{roomId}/join` | Bearer JWT | Cookie + X-XSRF-TOKEN | `RoomDeviceActionRequest` → `RoomResponse` / 200, 400, 401, 403, 404, 409, 406, 413, 415 | RoomsApi.kt | ✅ Endpoint declared |
| `POST /api/v1/rooms/{roomId}/activate` | Bearer JWT | Cookie + X-XSRF-TOKEN | `RoomDeviceActionRequest` → `RoomResponse` / 200, 400, 401, 403, 404, 409, 406, 413, 415 | RoomsApi.kt | ✅ Endpoint declared |
| `POST /api/v1/rooms/{roomId}/deactivate` | Bearer JWT | Cookie + X-XSRF-TOKEN | `RoomDeviceActionRequest` → `—` / 204, 400, 401, 403, 406, 413, 415 | RoomsApi.kt | ✅ Endpoint declared |
| `POST /api/v1/rooms/{roomId}/leave` | Bearer JWT | Cookie + X-XSRF-TOKEN | `RoomDeviceActionRequest` → `RoomResponse` / 200, 400, 401, 403, 404, 409, 406, 413, 415 | RoomsApi.kt | ✅ Endpoint declared |
| `POST /api/v1/rooms/{roomId}/archive` | Bearer JWT | Cookie + X-XSRF-TOKEN | `RoomDeviceActionRequest` → `RoomResponse` / 200, 400, 401, 403, 404, 409, 406, 413, 415 | RoomsApi.kt | ✅ Endpoint declared |
| `POST /api/v1/rooms/{roomId}/invites` | Bearer JWT | Cookie + X-XSRF-TOKEN | `CreateRoomInviteRequest` → `RoomInviteResponse` / 201, 400, 401, 403, 404, 409, 406, 413, 415 | InvitesApi.kt | ✅ Endpoint declared (E2E pending) |
| `GET /api/v1/rooms/{roomId}/invites` | Bearer JWT | — | `—` → `—` / 200, 401, 403, 404, 400, 406, 413, 415 | InvitesApi.kt | ✅ Endpoint declared (E2E pending) |
| `POST /api/v1/rooms/{roomId}/invites/qr` | Bearer JWT | Cookie + X-XSRF-TOKEN | `CreateRoomInviteRequest` → `—` / 201, 400, 401, 403, 404, 409, 429, 503, 406, 413, 415 | InvitesApi.kt | ✅ Endpoint declared (E2E pending) |
| `DELETE /api/v1/rooms/{roomId}/invites/{inviteId}` | Bearer JWT | Cookie + X-XSRF-TOKEN | `—` → `—` / 204, 401, 403, 404, 400, 406, 413, 415 | InvitesApi.kt | ✅ Endpoint declared (E2E pending) |
| `POST /api/v1/presence/heartbeat` | Bearer JWT | Cookie + X-XSRF-TOKEN | `PresenceHeartbeatRequest` → `PresenceHeartbeatResponse` / 200, 400, 401, 403, 406, 413, 415 | PresenceApi.kt | ✅ Client contract (GAP-05; Docker E2E open) |
| `PUT /api/v1/presence/preference` | Bearer JWT | Cookie + X-XSRF-TOKEN | `PresencePreferenceRequest` → `PresencePreferenceResponse` / 200, 400, 401, 406, 413, 415 | — | ⬜ Not in mobile scope yet |
| `POST /api/v1/rooms/{roomId}/calls` | Bearer JWT | Cookie + X-XSRF-TOKEN | `—` → `CallSnapshot` / 201, 401, 403, 404, 409, 400, 406, 413, 415 | CallsApi.kt | ✅ Endpoint declared (E2E pending) |
| `GET /api/v1/rooms/{roomId}/calls/current` | Bearer JWT | — | `—` → `CallSnapshot` / 200, 401, 403, 404, 409, 400, 406, 413, 415 | CallsApi.kt | ✅ Endpoint declared (E2E pending) |
| `GET /api/v1/calls/{callId}` | Bearer JWT | — | `—` → `CallSnapshot` / 200, 401, 403, 404, 409, 400, 406, 413, 415 | CallsApi.kt | ✅ Endpoint declared (E2E pending) |
| `POST /api/v1/calls/{callId}/join` | Bearer JWT | Cookie + X-XSRF-TOKEN | `—` → `CallSnapshot` / 200, 401, 403, 404, 409, 400, 406, 413, 415 | CallsApi.kt | ✅ Endpoint declared (E2E pending) |
| `POST /api/v1/calls/{callId}/leave` | Bearer JWT | Cookie + X-XSRF-TOKEN | `—` → `CallSnapshot` / 200, 401, 403, 404, 409, 400, 406, 413, 415 | CallsApi.kt | ✅ Endpoint declared (E2E pending) |
| `POST /api/v1/calls/{callId}/end` | Bearer JWT | Cookie + X-XSRF-TOKEN | `—` → `CallSnapshot` / 200, 401, 403, 404, 400, 406, 413, 415 | CallsApi.kt | ✅ Endpoint declared (E2E pending) |

## Contract essentials for implemented foundation

| Endpoint | Required request | Relevant expected response / rules |
|---|---|---|
| `GET /api/v1/auth/csrf` | No bearer | JSON `headerName`, `token`; `Set-Cookie: XSRF-TOKEN=...` (HttpOnly) |
| `POST /api/v1/auth/login` | `LoginRq` JSON, cookie + masked header; no bearer | `LoginRs` includes `session` access and refresh JWT and `user` |
| `POST /api/v1/auth/refresh` | `RefreshTokenRq` JSON; CSRF pair; no bearer | New session tokens; standalone refresh client shares cookie jar |
| `POST /api/v1/auth/logout` | Access Bearer JWT + CSRF | `204`/no body; local logout clears session even if offline |
| `POST /api/v1/devices/register` | Bearer + CSRF; `installationId`, device metadata | Server-issued device UUID; binds current auth session |
| `POST /api/v1/session/bootstrap` | Bearer + CSRF; `installationId`, bootstrap metadata | User, session flags, configuration, devices, rooms, presence, server snapshot |
| `POST /api/v1/presence/heartbeat` | Bearer + CSRF; **only** required `deviceId` | `availability`, `activity`, `preference`, `currentRoomId`, `lastSeenAt`; activity is server-owned |

> The OpenAPI records above are the documented surface of the supplied ServerRelay archive; some detailed backend security conditions are stricter than an operation's individual responses. Swagger security metadata alone does not describe mandatory CSRF for protected writes.

### Android runtime security (GAP-07/08)

The Retrofit client is origin-bound before auth interceptors. Debug cleartext permits only explicitly configured development hostnames in `app/src/debug/res/xml/network_security_config.xml`; if using a physical phone, add the **exact private IP** to the debug XML and set `-PserverRelayDebugBaseUrl=http://<LAN-IP>:8090/`. Production requires HTTPS and a valid certificate; never ship debug cleartext policies. All protected requests include the access JWT as `Authorization: Bearer …`, while login/refresh and other modifying requests need the same-origin CSRF proof/cookie as documented above. Session tokens are encrypted by AndroidKeyStore AES-GCM before DataStore writes, including on upgrade from old plaintext preference values. No tokens belong in URLs, logs, backups, screenshots, or diagnostic cards.

### Added client contracts — GAP-09/10 (29 Oct)

- `RealtimeSignalingClient` uses an explicitly opened `ws(s)://<configured-origin>/ws/realtime` connection; handshake sends `Authorization: Bearer <access JWT>` and `X-AudioShare-Device-Id: <server UUID>`. The JSON envelope uses `version: "1"`, an event UUID, UTC timestamp and `{}` payload. Room and call subscriptions are opt-in. WebSocket connection is closed on explicit logout. **Not a media transport:** client now has bounded reconnect, validated server envelopes, idempotent subscriptions and snapshot-required notification (server V1 has no replay); live E2E and lifecycle profiling are still open.
- `InvitesApi`: `POST/GET /api/v1/rooms/{roomId}/invites`; `POST /api/v1/rooms/{roomId}/invites/qr` (PNG); `DELETE /api/v1/rooms/{roomId}/invites/{inviteId}`. Request for creation requires `deviceId` from device binding; raw code appears only in creation response and is never persisted by the repository.
- `CallsApi`: `POST /api/v1/rooms/{roomId}/calls`, `GET /api/v1/rooms/{roomId}/calls/current`, `GET /api/v1/calls/{callId}`, and `POST /api/v1/calls/{callId}/{join|leave|end}`. Model is `CallSnapshot` and participant snapshots. No media negotiation implemented.
- `AuthApi` retains `POST /api/v1/auth/login`, `refresh`, `logout`. Every auth write now obtains a **fresh** `GET /api/v1/auth/csrf` proof and HTTP-only cookie, sends the returned masked proof in `X-XSRF-TOKEN`, and drops *both* in-memory objects after response/failure. JWT is not sent to login/refresh; protected routes carry JWT. CSRF failure is not automatically replayed. No proof/cookie is written to persistent storage.

These are source-aligned endpoint contracts, not proof of successful login or realtime calls against a running ServerRelay Docker instance.

### Debug login diagnostics (29 Oct)

The debug login screen displays the configured backend base URL and a redacted failure category. On Android Emulator use `http://10.0.2.2:8090/` when ServerRelay is reachable on the host; on a physical device set `serverRelayDebugBaseUrl=http://<your-PC-LAN-IPv4>:8090/` in root `gradle.properties`, and permit the same exact host in `app/src/debug/res/xml/network_security_config.xml`. Build the debug variant again; ensure the phone and PC can reach each other and local firewall/Docker port 8090 is exposed. Never use `localhost` on the phone to mean your development PC. Do not place real credentials or JWTs in Gradle files, logs, or issue reports.

Login first GETs `/api/v1/auth/csrf` without a JWT, receives the masked `token` plus `XSRF-TOKEN` cookie, and POSTs `/api/v1/auth/login` with the matching `X-XSRF-TOKEN` header and cookie. The proof is cleared after the auth transaction. A 401 indicates credentials/auth rejection, a 403 indicates access/CSRF rejection, and a connection error indicates reachability; do not treat them as the same failure. This is client-side contract alignment; a real running-ServerRelay login is not yet proven.

### Profile and new safety boundaries (30 Oct)

- Home avatar: **guest → login**, **signed-in → profile**. Profile displays available login/bootstrap user fields, fallback account ID, server-supplied theme and a Sign out button that clears local credentials and realtime subscriptions. `/api/v1/auth/me` is still not implemented; profile uses the existing authenticated response, not a new API.
- New signaling frames are checked for UTF-8 size, known V1 types/version, UUID event ID and optional scope. Inbound is capped, events deduplicated, reconnection is capped at five attempts and room/call subscriptions are replayed only as subscriptions, **not missed events**. On `SNAPSHOT_REQUIRED`, consumers must re-fetch authoritative snapshots. Live signaling is not automatically started.
- Legacy UDP raw audio foreground-service starts are refused: there is **no secure local audio transport yet**, and enabling that unsafe prototype is not considered GAP-11 completion.
- `InviteRepository` / `CallRepository` refuse malformed UUIDs before Retrofit; `CsrfInterceptor` still creates the origin-bound XSRF cookie/proof for every unsafe operation and auth refresh. `Authorization: Bearer <JWT>` remains shared, never passed by individual screens.
