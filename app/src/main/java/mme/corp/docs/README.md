# SvReMobile documentation

Single maintained documentation source: [roadmap](src/main/docs/roadmap/SVREMOBILE_ROADMAP.md).

References: [stage dependencies](src/main/docs/roadmap/SVREMOBILE_ROADMAP_DEPENDENCIES_TREE.md) · [integration gaps](src/main/docs/roadmap/SVREMOBILE_SERVERRELAY_INTEGRATION_GAPS.md) · [accepted architecture/security decisions](src/main/docs/roadmap/SVREMOBILE_ARCHITECTURE_SECURITY_DECISIONS.md) · [owner decisions](src/main/docs/roadmap/SVREMOBILE_PRE_IMPLEMENTATION_DECISIONS.md) · [student resources](src/main/docs/roadmap/SVREMOBILE_STUDENT_DEVELOPER_PACK.md).

Old and duplicate roadmaps under `app/src/main/java/**/docs/` were removed because they contained obsolete progress assertions; documentation is **not** packaged with Kotlin sources.

## Developer endpoint setup (GAP-00)

- Android emulator + local Docker Compose: `bash ./gradlew :app:assembleDebug` (default `http://10.0.2.2:8090/`).
- Physical Android phone on trusted LAN: `bash ./gradlew :app:assembleDebug -PserverRelayDebugBaseUrl=http://192.168.1.123:8090/` (replace with the reachable **private** host IP).
- Production build: `bash ./gradlew :app:assembleRelease -PserverRelayReleaseBaseUrl=https://api.example.org/` (replace with your verified TLS endpoint). Without the property, release is intentionally blocked.
- No debug HTTP traffic may target public hosts under `ApiEndpointPolicy`; release base config disallows cleartext. Debug variant has broader platform cleartext permission for local testing—do not distribute debug builds to users. No automatic certificate bypass or trust-all manager is used.
- ServerRelay device registration, auth/CSRF, bootstrap and LAN media interoperability are **not yet repaired** by GAP-00. Pre-existing application-layer auth logs still need the LOG/SEC privacy audit; transport-layer BODY logging removal alone is not enough.

Never place passwords, bearer tokens or API secrets in Gradle properties.

Mobile-only entity reference: [ServerRelay ↔ SvReMobile entity mapping](src/docs/roadmap/SVREMOBILE_SERVERRELAY_ENTITY_CONTRACT.md). Entity definitions are not active API DTOs.
