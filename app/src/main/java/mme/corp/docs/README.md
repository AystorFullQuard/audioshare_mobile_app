# SvReMobile technical documentation

[Main implementation roadmap](src/main/docs/roadmap/SVREMOBILE_ROADMAP.md) ·
[Dependency tree and readiness table](src/main/docs/roadmap/SVREMOBILE_ROADMAP_DEPENDENCIES_TREE.md) ·
[Integration GAP register](src/main/docs/roadmap/SVREMOBILE_SERVERRELAY_INTEGRATION_GAPS.md) ·
[Architecture and security decisions](src/main/docs/roadmap/SVREMOBILE_ARCHITECTURE_SECURITY_DECISIONS.md) ·
[Client entity contract](src/main/docs/contract/SVREMOBILE_SERVERRELAY_ENTITY_CONTRACT.md) ·
[ServerRelay Swagger endpoints + JWT/CSRF implementation](src/main/docs/contract/SVREMOBILE_SERVERRELAY_ENDPOINTS.md)

## Test endpoints

Debug emulator: `./gradlew :app:assembleDebug` (defaults to `http://10.0.2.2:8090/`).
Physical phone on **trusted** LAN: explicitly allow the host IP in `app/src/debug/res/xml/network_security_config.xml`, then run `./gradlew :app:assembleDebug -PserverRelayDebugBaseUrl=http://192.168.1.123:8090/` (replace the IP in both places). The default debug XML permits only emulator `10.0.2.2` and `localhost`; no blanket cleartext exception.
Release: `./gradlew :app:assembleRelease -PserverRelayReleaseBaseUrl=https://your-real-tls-host/`.

Device registration now posts `installationId` to `/api/v1/devices/register`. It does not replace the server-issued `deviceId`. Both identities and local auth credentials are excluded from cloud/device-transfer backup. Bootstrap now sends the *same* `installationId` and reads the ServerRelay session/user/configuration/capabilities/devices/rooms/presence/server response. Startup registration rebinds each current login session even if the device ID is cached. GAP-05 presence and GAP-06 CSRF/JWT are accepted based on owner-reported tests; Docker E2E remains open. GAP-07 origin-bound transport and GAP-08 AndroidKeyStore-encrypted tokens (with legacy migration) are implemented, pending new Android tests. SvRe Home is the default guest entry; tap the top-right avatar to sign in. Existing sessions bootstrap from Home and display real server rooms, without a mandatory login screen. Local and video sharing remain planned. Gradle tests require the cached/installable Gradle distribution and Android SDK.

### Try login and My Rooms on your phone

1. Start ServerRelay on your PC (default port `8090`) and ensure it listens on the PC LAN interface, not only `127.0.0.1`. Do not expose the debug server to the public internet.
2. In **root `gradle.properties`**, uncomment `serverRelayDebugBaseUrl=http://192.168.1.123:8090/` and replace with your PC LAN IPv4.
3. In `app/src/debug/res/xml/network_security_config.xml`, uncomment the matching `<domain>192.168.1.123</domain>` and replace it with the *same* IPv4. Rebuild the debug app.
4. Put the phone on the same LAN/hotspot as the PC; open SvRe, tap the avatar, and sign in with your ServerRelay credentials. Home will load registered device/session bootstrap and **My Rooms** automatically. If no rooms exist, create one from the Rooms screen.
5. Emulator testing requires no IP edits (`10.0.2.2:8090`). Production builds reject cleartext HTTP. No JWT is entered manually: the repository stores login tokens, and the shared client attaches `Authorization: Bearer …` to protected requests and CSRF proof where needed.

**Known gap:** the requested 29 October 12:15 snapshot and `error.txt` were not available in the supplied files. This patch builds on the latest verified 29 October GAP-05–08 source. Android/real-server tests were not run here; previously reported tests do not automatically validate this change.
