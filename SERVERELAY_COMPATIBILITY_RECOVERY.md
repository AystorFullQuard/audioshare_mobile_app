# AudioShare ↔ ServeRelay Compatibility Recovery

Temporary development tracker. Delete this file after compatibility recovery,
full regression, and the final two-device E2E are complete.

## Source-of-truth baseline

- AudioShare branch: `prototype-v1`
- AudioShare starting commit: `86edaa3e3a43639e886709048a5687c0c45ea1f9`
- ServeRelay starting commit: `cb723769c09730ad4e1666f2ff3c5c5875972297`
- ServeRelay ADR-004/ADR-005 Device + Session Bootstrap architecture is authoritative.
- Do not restore legacy `POST /api/v1/bootstrap` on ServeRelay.
- Invites/QR/deep links are outside this recovery stage.

## Working rules

A stage becomes `✅` only after its patch is applied and its complete gate is green.
The next stage must read this file first and update the previous stage status.
Do not combine compatibility work with unrelated cleanup or new product features.

## Recovery stages

- ✅ **AS-COMPAT-01A — Device API/repository foundation**
  - `POST /api/v1/devices` Android client contract.
  - Device registration request/response DTOs.
  - Repository validation/error/cancellation behavior.
  - MockWebServer contract tests.
  - AppContainer foundation wiring

- ✅ **AS-COMPAT-01B — User-scoped persistent device identity**
  - Preserve registered device identity across auth cleanup.
  - User-scoped device mapping in the existing SessionManager/DataStore.
  - Legacy `device_id` migration.
  - Explicit current-user device invalidation support.

- ✅ **AS-COMPAT-02 — Current session-bootstrap contract**
  - Align Android request with current `/api/v1/session/bootstrap`.
  - Add current device metadata fields.
  - Test against a full ServeRelay-shaped bootstrap response.

- ✅ **AS-COMPAT-03 — Startup cutover**
  - Resolve/register device before session bootstrap.
  - Remove production use of legacy `/api/v1/bootstrap`.
  - Preserve startup order: device → session bootstrap → room restore → heartbeat.

- [ ] ⬜ **AS-COMPAT-04 — Stale/revoked device recovery**
  - Recover once from device-not-owned/not-found/registration-required.
  - No retry loop or registration storm.
  - Do not discard valid device state on transient network/server failures.

- [ ] ⬜ **AS-COMPAT-05 — Cross-contract regression through Room entry**
  - Lock the real ServeRelay request paths and JSON shapes in integration tests.
  - Cover fresh device, session bootstrap, Presence, LOCAL_DISCOVERY create/join,
    activate, room details, and members.

## Final acceptance

- [ ] ⬜ One-device fresh-install smoke.
- [ ] ⬜ App restart reuses the registered device.
- [ ] ⬜ Same-account logout/login does not create duplicate devices.
- [ ] ⬜ Access-token refresh preserves device identity.
- [ ] ⬜ Two-device LOCAL_DISCOVERY smoke through room entry.
- [ ] ⬜ Full AudioShare regression.
- [ ] ⬜ Final ServeRelay + AudioShare two-device E2E.
- [ ] ⬜ Delete this temporary tracker after the recovery baseline is accepted.

## AS-COMPAT-01A gate

```bat
gradlew.bat :app:testDebugUnitTest --tests "mme.corp.audioshare.data.repository.DeviceRepositoryTest" --rerun-tasks --console=plain
gradlew.bat :app:compileDebugKotlin --rerun-tasks --console=plain
gradlew.bat :app:lintDebug :app:assembleDebug --rerun-tasks --console=plain
git diff --check
git status --short
```

## AS-COMPAT-01B gate

```bat
gradlew.bat :app:testDebugUnitTest --tests "mme.corp.audioshare.data.storage.DeviceIdentityPreferencesTest" --rerun-tasks --console=plain
gradlew.bat :app:assembleDebugAndroidTest --rerun-tasks --console=plain
gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=mme.corp.audioshare.data.storage.SessionManagerTokenStoreTest --rerun-tasks --console=plain
gradlew.bat :app:testDebugUnitTest --rerun-tasks --console=plain
gradlew.bat :app:lintDebug :app:assembleDebug --rerun-tasks --console=plain
git diff --check
git status --short
```

## AS-COMPAT-02 gate

```bat
gradlew.bat :app:testDebugUnitTest --tests "mme.corp.audioshare.data.repository.BootstrapRepositoryTest" --tests "mme.corp.audioshare.startup.BootstrapStartupCoordinatorTest" --rerun-tasks --console=plain
gradlew.bat :app:testDebugUnitTest --rerun-tasks --console=plain
gradlew.bat :app:lintDebug :app:assembleDebug --rerun-tasks --console=plain
git diff --check
git status --short
```

## AS-COMPAT-03 gate

```bat
gradlew.bat :app:testDebugUnitTest --tests "mme.corp.audioshare.startup.DeviceRegistrationCoordinatorTest" --tests "mme.corp.audioshare.startup.BootstrapStartupCoordinatorTest" --tests "mme.corp.audioshare.data.repository.BootstrapRepositoryTest" --rerun-tasks --console=plain
gradlew.bat :app:testDebugUnitTest --rerun-tasks --console=plain
gradlew.bat :app:lintDebug :app:assembleDebug --rerun-tasks --console=plain
git diff --check
git status --short
```
