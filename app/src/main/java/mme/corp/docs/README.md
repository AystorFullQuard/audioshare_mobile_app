# SvReMobile technical documentation

[Main implementation roadmap](src/main/docs/roadmap/SVREMOBILE_ROADMAP.md) ·
[Dependency tree and readiness table](src/main/docs/roadmap/SVREMOBILE_ROADMAP_DEPENDENCIES_TREE.md) ·
[Integration GAP register](src/main/docs/roadmap/SVREMOBILE_SERVERRELAY_INTEGRATION_GAPS.md) ·
[Architecture and security decisions](src/main/docs/roadmap/SVREMOBILE_ARCHITECTURE_SECURITY_DECISIONS.md) ·
[Client entity contract](src/main/docs/contract/SVREMOBILE_SERVERRELAY_ENTITY_CONTRACT.md) ·
[ServerRelay Swagger endpoints + JWT/CSRF implementation](src/main/docs/contract/SVREMOBILE_SERVERRELAY_ENDPOINTS.md)

## Test endpoints

Debug emulator: `./gradlew :app:assembleDebug` (defaults to `http://10.0.2.2:8090/`).
Physical phone on **trusted** LAN: `./gradlew :app:assembleDebug -PserverRelayDebugBaseUrl=http://192.168.1.123:8090/` (replace with host IP).
Release: `./gradlew :app:assembleRelease -PserverRelayReleaseBaseUrl=https://your-real-tls-host/`.

Device registration now posts `installationId` to `/api/v1/devices/register`. It does not replace the server-issued `deviceId`. Both identities and local auth credentials are excluded from cloud/device-transfer backup. Bootstrap now sends the *same* `installationId` and reads the ServerRelay session/user/configuration/capabilities/devices/rooms/presence/server response. Startup registration rebinds each current login session even if the device ID is cached. GAP-05 presence and GAP-06 shared CSRF/JWT wiring are implemented; their new Android tests and real ServerRelay E2E remain pending. Owner-reported earlier tests passed for GAP-00–04/22. Gradle tests require the cached/installable Gradle distribution and Android SDK.
