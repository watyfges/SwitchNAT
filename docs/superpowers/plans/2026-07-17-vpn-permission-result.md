# VPN Permission Result Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Launch Android's VPN consent activity for a result and continue the existing start flow automatically after approval.

**Architecture:** Add a generic, JVM-testable coordinator that owns one pending permission callback. `MainActivity` supplies `VpnService.prepare()` and an Activity Result launcher; `NativeBridge` converts coordinator outcomes into method-channel results and diagnostics.

**Tech Stack:** Kotlin 2.x, Android Activity Result API, Flutter method channels, JUnit 4, Flutter widget tests

## Global Constraints

- Change only VPN permission handling and its Flutter feedback.
- Do not change VPN service startup, TUN, Shadowsocks, HTTP proxy, NAT, or packet processing.
- Treat permission approval as valid only when the Activity Result code is `Activity.RESULT_OK`.
- Keep at most one pending permission request.

---

### Task 1: Permission Coordinator

**Files:**
- Create: `android/app/src/main/kotlin/dev/switchnat/app/bridge/VpnPermissionCoordinator.kt`
- Test: `android/app/src/test/kotlin/dev/switchnat/app/bridge/VpnPermissionCoordinatorTest.kt`

**Interfaces:**
- Produces: `VpnPermissionCoordinator<T>.request((Boolean) -> Unit): RequestOutcome`
- Produces: `VpnPermissionCoordinator<T>.complete(Boolean)`
- Produces: `RequestOutcome.ALREADY_GRANTED`, `LAUNCHED`, and `BUSY`

- [ ] **Step 1: Write failing tests**

Cover a null prepared token, approved result, denied result, and a second request while one is pending using a `String` token so the tests do not require an Android runtime.

- [ ] **Step 2: Verify the tests fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*VpnPermissionCoordinatorTest'`

Expected: compilation failure because `VpnPermissionCoordinator` and `RequestOutcome` do not exist.

- [ ] **Step 3: Implement the coordinator**

Store one nullable completion callback. Return `ALREADY_GRANTED` and invoke it with `true` when prepare returns null; store and launch for `LAUNCHED`; return `BUSY` without replacing the first callback; clear the callback before delivering an approved or denied result.

- [ ] **Step 4: Verify the coordinator tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*VpnPermissionCoordinatorTest'`

Expected: `BUILD SUCCESSFUL`.

### Task 2: Android Bridge Integration

**Files:**
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/MainActivity.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/bridge/NativeBridge.kt`

**Interfaces:**
- Consumes: `VpnPermissionCoordinator<Intent>`
- Produces: `NativeBridge.onVpnPermissionResult(Boolean)`

- [ ] **Step 1: Register the Activity Result launcher**

Use `ActivityResultContracts.StartActivityForResult()` in `MainActivity` and pass its `launch` function plus `VpnService.prepare(this)` to the coordinator.

- [ ] **Step 2: Await the permission result in NativeBridge**

Do not complete the method-channel result immediately after launch. Complete it from the coordinator callback, update diagnostics to `PERMISSION_NEEDED` only for `LAUNCHED`, and return `VPN_PERMISSION_IN_PROGRESS` for `BUSY`.

- [ ] **Step 3: Run Android tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

### Task 3: Flutter Denial Feedback

**Files:**
- Modify: `test/screens/config_screen_test.dart`
- Modify: `lib/src/screens/config_screen.dart`

**Interfaces:**
- Consumes: `NativeBridge.requestVpnPermission(): Future<bool>`
- Produces: user feedback `VPN permission denied`

- [ ] **Step 1: Write a failing widget test**

Inject a valid saved configuration and a bridge returning `false`, tap Start, and assert that `VPN permission denied` is shown and `start` is not called.

- [ ] **Step 2: Verify the widget test fails**

Run: `flutter test test/screens/config_screen_test.dart`

Expected: failure because the current copy asks the user to tap Start again.

- [ ] **Step 3: Update the denial copy**

Replace the obsolete second-tap instruction with `VPN permission denied`.

- [ ] **Step 4: Run Flutter tests**

Run: `flutter test`

Expected: all tests pass.

### Task 4: Build and Device Verification

**Files:**
- Modify: `docs/testing/xiaomi15-android16-verification.md` only if the reusable procedure changes.
- Create: `docs/testing/vivo-v2458a-android16-smoke.md` with observed supplemental results.

- [ ] **Step 1: Run static and full automated checks**

Run: `flutter analyze`, `flutter test`, and `./gradlew :app:testDebugUnitTest`.

Expected: no analysis issues and all tests successful.

- [ ] **Step 2: Build and install the APK**

Run: `flutter build apk --debug` and install with `adb -s <device-ip>:<adb-port> install -r`.

- [ ] **Step 3: Verify the device lifecycle**

Confirm the consent dialog remains visible, approve it, verify automatic startup, foreground service/TUN/running UI state, then stop and verify no service remains.

- [ ] **Step 4: Record evidence and commit**

Record device model, Android version, observed states, limitations, APK hash, and exact verification commands. Commit source, tests, and evidence together.
