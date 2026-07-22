# SwitchNAT Compact UI and Live Update Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved compact layered UI, concise standard Android Live Update, and the pending conservative STUN timeout classification, then verify everything on the paired HyperOS phone.

**Architecture:** Keep the forwarding and profile layers unchanged. Flutter widget changes remain inside Home and Config, while Android notification copy stays in the pure `LiveUpdateContent` formatter and platform-only notification properties stay in `SwitchNatVpnService`; STUN classification remains isolated in `StunNatTester`.

**Tech Stack:** Flutter/Dart Material 3, Kotlin, AndroidX `NotificationCompat`, JUnit 4, Flutter widget tests, Gradle, ADB.

## Global Constraints

- Preserve the four pages: Home, Config, Status, and Logs.
- Preserve no-root VPN forwarding, dynamic UDP learning, profile persistence, global STUN configuration, and the 516 bootstrap rules.
- Use only standard Android promoted ongoing notifications; do not add Xiaomi-private APIs or custom `RemoteViews`.
- Never put the SS hostname or endpoint in the notification title.
- Do not add third-party UI dependencies.
- The user requested one final commit on `master`; do not create intermediate implementation commits.

---

### Task 1: Full-width horizontal Home metric cards with complete NAT content

**Files:**
- Create: `test/screens/home_screen_test.dart`
- Modify: `test/support/controller_fakes.dart`
- Modify: `lib/src/screens/home_screen.dart`

**Interfaces:**
- Consumes: `AppController.status`, `TestBridge.statuses`, `ServiceStatus.fromJson(Map<Object?, Object?>)`.
- Produces: widget keys `switch-card` and `nat-card`; two stacked full-width cards whose `_MetricCard` renders `value` and `detail` without one-line ellipsis.

- [ ] **Step 1: Add a helper that emits diagnostic state in widget tests**

Add this method to `TestBridge`:

```dart
void emitStatus(Map<Object?, Object?> json) {
  statuses.add(ServiceStatus.fromJson(json));
}
```

- [ ] **Step 2: Write the narrow-screen failing widget test**

Create `test/screens/home_screen_test.dart` with a 360x800 logical viewport, a running profile, a connected Switch, NAT label `Port-Restricted Cone`, and mapped endpoint `203.0.113.8:51234`. After pumping `HomeScreen`, assert:

```dart
final switchCard = find.byKey(const Key('switch-card'));
final natCard = find.byKey(const Key('nat-card'));
final switchSize = tester.getSize(switchCard);
final natSize = tester.getSize(natCard);
expect(switchSize.width, natSize.width);
expect(switchSize.width, greaterThan(300));
expect(tester.getTopLeft(natCard).dy, greaterThan(tester.getTopLeft(switchCard).dy));

final detail = tester.widget<Text>(find.text('203.0.113.8:51234'));
expect(detail.maxLines, 2);
expect(detail.overflow, isNot(TextOverflow.ellipsis));
expect(tester.takeException(), isNull);
```

Reset `tester.view.physicalSize` and `devicePixelRatio` with `addTearDown` and dispose the controller.

- [ ] **Step 3: Run the test and verify the current layout fails**

Run:

```bash
.toolchains/flutter/bin/flutter test test/screens/home_screen_test.dart
```

Expected: FAIL because the current side-by-side cards are only about half the available width.

- [ ] **Step 4: Implement the full-width horizontal layout**

In `HomeScreen`, stack the two cards with an 8dp vertical gap and assign `key: const Key('switch-card')` to the Switch card. In `_MetricCard`, use a `Row` with a 44dp icon container and an expanded text column:

```dart
Text(
  value,
  maxLines: 2,
  softWrap: true,
  style: Theme.of(context).textTheme.titleMedium,
),
if (detail.isNotEmpty)
  Text(
    detail,
    maxLines: 2,
    softWrap: true,
    overflow: TextOverflow.visible,
  ),
```

Keep the NAT card tap callback and the current-node TCP test callback unchanged.

- [ ] **Step 5: Run focused Home tests**

Run:

```bash
.toolchains/flutter/bin/flutter test test/screens/home_screen_test.dart test/widget_test.dart
```

Expected: all tests PASS with no render overflow exception.

---

### Task 2: Compact Config profile summary and advanced sections

**Files:**
- Modify: `test/screens/config_screen_test.dart`
- Modify: `test/screens/config_screen_load_test.dart`
- Modify: `lib/src/screens/config_screen.dart`

**Interfaces:**
- Consumes: `ProfileLibrary.selectedProfile`, profile CRUD methods on `AppController`, and existing `_editStun` and `_save` flows.
- Produces: a non-overflowing profile summary card, `PopupMenuButton<_ProfileAction>`, compact editable server fields, and expandable advanced editors.

- [ ] **Step 1: Replace the existing Config expectations with the approved hierarchy**

Use a profile whose migrated name and endpoint are both `very-long-node-name.example.test:5375`. Pump at a 360x800 viewport and assert:

```dart
expect(find.text('Node config'), findsOneWidget);
expect(find.byKey(const Key('profile-summary')), findsOneWidget);
expect(find.byTooltip('Add profile'), findsOneWidget);
expect(find.byTooltip('Profile actions'), findsOneWidget);
expect(find.text('Server'), findsOneWidget);
expect(find.text('Advanced'), findsOneWidget);
expect(find.text('Save node'), findsOneWidget);
expect(tester.takeException(), isNull);
```

Open `Bootstrap UDP targets` and confirm the editable rule remains visible. Open the profile actions menu and confirm `Duplicate`, `Rename`, and `Delete` are present there rather than in the page body.

- [ ] **Step 2: Run the Config test and verify it fails on the current UI**

Run:

```bash
.toolchains/flutter/bin/flutter test test/screens/config_screen_test.dart test/screens/config_screen_load_test.dart
```

Expected: FAIL because the current page still uses `Server profiles`, an overflowing `DropdownButtonFormField`, and four visible profile-action buttons.

- [ ] **Step 3: Introduce the profile action enum and compact summary card**

Add:

```dart
enum _ProfileAction { duplicate, rename, delete }
```

Build the summary as a keyed `Card` containing one `ListTile`. Its title opens a profile-selection bottom sheet, its subtitle shows the endpoint with one safe ellipsis, and its trailing controls are:

```dart
IconButton(
  tooltip: 'Add profile',
  onPressed: widget.controller.busy
      ? null
      : () => widget.controller.createProfile(sourceId: profile.id),
  icon: const Icon(Icons.add),
),
PopupMenuButton<_ProfileAction>(
  tooltip: 'Profile actions',
  onSelected: (action) => _runProfileAction(action, profile, library),
  itemBuilder: (_) => <PopupMenuEntry<_ProfileAction>>[
    const PopupMenuItem(value: _ProfileAction.duplicate, child: Text('Duplicate')),
    const PopupMenuItem(value: _ProfileAction.rename, child: Text('Rename')),
    PopupMenuItem(
      value: _ProfileAction.delete,
      enabled: library.profiles.length > 1,
      child: const Text('Delete'),
    ),
  ],
),
```

Add `_pickProfile` and `_runProfileAction` methods that call the existing controller methods; keep delete disabled when only one profile exists.

- [ ] **Step 4: Compact and group the existing fields**

Change the page title to `Node config`; add `Server` and `Advanced` section labels. Use `isDense: true` `InputDecoration` values and keep host/password/encryption full-width while port and HTTP proxy remain a two-column row. Convert the two large advanced cards to compact cards with `ListTile` headers; expand the existing UDP and STUN editors in place. Rename the final button to `Save node`.

- [ ] **Step 5: Run all Config tests**

Run:

```bash
.toolchains/flutter/bin/flutter test test/screens/config_screen_test.dart test/screens/config_screen_load_test.dart test/screens/config_screen_permission_test.dart
```

Expected: all tests PASS with no `RenderFlex` overflow at 360 logical pixels.

---

### Task 3: Concise standard Android Live Update

**Files:**
- Modify: `android/app/src/test/kotlin/dev/switchnat/app/vpn/LiveUpdateContentTest.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/vpn/LiveUpdateContent.kt`
- Create: `android/app/src/main/res/drawable/ic_stat_switchnat.xml`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt`

**Interfaces:**
- Consumes: `DiagnosticsSnapshot.sessionStartedAtMillis`, Switch presence, NAT result, TCP result, profile name, and learned endpoint details.
- Produces: `LiveUpdateContent(title, text, bigText, startedAtMillis)` and a promoted ongoing notification using the dedicated status icon and chronometer.

- [ ] **Step 1: Write failing formatter tests**

Update the running-content test to assert:

```kotlin
assertEquals("SwitchNAT connected", content.title)
assertEquals("Switch connected · NAT Full Cone", content.text)
assertEquals(diagnostics.snapshot().sessionStartedAtMillis, content.startedAtMillis)
assertTrue(content.bigText.contains("Node Tokyo"))
assertTrue(content.bigText.contains("SS UDP 203.0.113.8:51234"))
assertTrue(content.bigText.contains("Switch 10.146.3.249"))
```

Add a connecting test that asserts title `SwitchNAT connecting`, text `Preparing VPN`, and that neither the title nor text contains the server hostname.

- [ ] **Step 2: Run the formatter tests and verify they fail**

Run from `android/`:

```bash
./gradlew :app:testDebugUnitTest --tests dev.switchnat.app.vpn.LiveUpdateContentTest
```

Expected: FAIL because the current title includes the active profile and `startedAtMillis` does not exist.

- [ ] **Step 3: Implement the pure content formatter**

Change the model to:

```kotlin
data class LiveUpdateContent(
    val title: String,
    val text: String,
    val bigText: String,
    val startedAtMillis: Long
)
```

Use stable titles, prefix the available NAT label with `NAT`, and build detail lines only for nonblank values. The expanded text order is state summary, `Node <profile>`, optional Switch IP, optional SS UDP mapping, and optional SS TCP latency.

- [ ] **Step 4: Add the dedicated monochrome notification icon**

Create `ic_stat_switchnat.xml` as a 24dp white vector containing two opposing horizontal arrows. It must use `android:fillColor="#FFFFFFFF"` and no colored background so Android and HyperOS can tint it correctly.

- [ ] **Step 5: Apply platform notification metadata**

In `buildNotification`, replace `android.R.drawable.stat_sys_upload_done` with `R.drawable.ic_stat_switchnat`. For a positive session start:

```kotlin
if ((content?.startedAtMillis ?: 0L) > 0L) {
    builder
        .setWhen(content!!.startedAtMillis)
        .setUsesChronometer(true)
        .setShowWhen(true)
}
```

Keep `setRequestPromotedOngoing(true)`, `setOngoing(true)`, `setOnlyAlertOnce(true)`, and `CATEGORY_SERVICE`. Do not add vendor APIs or custom views.

- [ ] **Step 6: Run the formatter and Android resource build checks**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests dev.switchnat.app.vpn.LiveUpdateContentTest
```

Expected: PASS.

---

### Task 4: Conservative STUN classification when alternate mapping probes time out

**Files:**
- Modify: `android/app/src/test/kotlin/dev/switchnat/app/diagnostics/StunNatTesterTest.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/diagnostics/StunNatTester.kt`

**Interfaces:**
- Consumes: the existing single `UdpRelaySession` transaction flow and `NatProbeOutcome`.
- Produces: timeout on required RFC 5780 mapping probes yields a partial result with no fabricated legacy NAT label.

- [ ] **Step 1: Add the failing alternate-mapping-timeout test**

Script the primary server to return a valid Binding response containing `OTHER-ADDRESS`, but return no response for the alternate target. Assert:

```kotlin
assertTrue(result.partial)
assertEquals("", result.legacyLabel)
assertEquals("Unknown", result.mappingBehavior)
assertEquals(mapped, result.mappedAddress)
```

- [ ] **Step 2: Run the focused test and verify the false classification**

Run from `android/`:

```bash
./gradlew :app:testDebugUnitTest --tests dev.switchnat.app.diagnostics.StunNatTesterTest
```

Expected: FAIL because the current fallback reports `Symmetric NAT` after an absent `map2`/`map3` response.

- [ ] **Step 3: Preserve a partial candidate instead of inventing a mapping class**

When `map2 == null`, store a partial candidate with `mappingBehavior = "Unknown"`, preserve the first mapped address and server, then continue to the next enabled STUN server. If `map2` differs but `map3 == null`, do the same. Only classify `Address-dependent` or `Address-and-port-dependent` when the required responses exist.

- [ ] **Step 4: Run all STUN tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests dev.switchnat.app.diagnostics.StunNatTesterTest --tests dev.switchnat.app.diagnostics.StunProtocolTest
```

Expected: all tests PASS; Full Cone remains available for complete RFC 5780 responses and Binding-only/timeout paths remain partial.

---

### Task 5: Full verification and paired-phone acceptance

**Files:**
- Modify: `.gitignore`
- Verify: all changed Flutter, Kotlin, resource, test, and documentation files.

**Interfaces:**
- Consumes: the complete app and paired device in `$ADB_SERIAL`.
- Produces: a clean debug APK, passing suites, verified UI/notification lifecycle, and no tracked visual-companion artifacts.

- [ ] **Step 1: Ignore the persisted visual companion workspace**

Add this line to `.gitignore`:

```gitignore
.superpowers/
```

- [ ] **Step 2: Format and run all Flutter checks**

Run:

```bash
.toolchains/flutter/bin/dart format lib test
.toolchains/flutter/bin/flutter test
.toolchains/flutter/bin/flutter analyze
```

Expected: formatting completes, all Flutter tests PASS, and analysis reports `No issues found!`.

- [ ] **Step 3: Run the complete Android unit suite**

Run from `android/`:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with all unit tests passing.

- [ ] **Step 4: Build the final debug APK**

Run from the repository root:

```bash
flutter build apk --debug
```

Expected: APK at `build/app/outputs/flutter-apk/app-debug.apk`.

- [ ] **Step 5: Install without deleting saved profiles**

Run:

```bash
.toolchains/android-sdk/platform-tools/adb -s "$ADB_SERIAL" install -r -d build/app/outputs/flutter-apk/app-debug.apk
```

Expected: `Success`.

- [ ] **Step 6: Verify Home and Config on the actual phone**

Launch `dev.switchnat.app/.MainActivity`, capture Home and Config screenshots, and verify:

- Switch and SS UDP NAT cards have the same rendered height.
- NAT type and mapped endpoint are fully readable.
- Config has no red overflow stripe and uses one profile actions menu.
- The saved host, port, password, encryption, 516 UDP rules, and global STUN list remain present.

- [ ] **Step 7: Verify Live Update lifecycle and metadata**

Start acceleration, then inspect `dumpsys notification --noredact` and HyperOS SystemUI logs. Verify the dedicated icon, stable short title, promoted ongoing flag, chronometer, Switch/NAT state text, and absence of the SS hostname in the title. Stop acceleration and verify the foreground notification disappears.

- [ ] **Step 8: Run repository hygiene checks**

Run:

```bash
git diff --check
git status --short --branch
rg -n "REAL_DEVICE_HOST|REAL_PUBLIC_IP|REAL_MAPPED_PORT" --glob "!build/**" --glob "!.dart_tool/**" .
```

Expected: no whitespace errors, `.superpowers/` is ignored, and no phone-derived endpoint or diagnostic result has leaked into tracked source. The configured host may legitimately exist only in device storage and must not appear in repository files.

---

### Task 6: Self-review and one final master commit

**Files:**
- Review: `git diff --stat`, `git diff`, and all files shown by `git status`.

**Interfaces:**
- Consumes: verified implementation and the user's pre-existing dynamic UDP changes.
- Produces: one final commit directly on `master`, preserving all requested in-scope work.

- [ ] **Step 1: Perform a self-review against both approved designs**

Check the dynamic UDP/profile/diagnostics/Live Update design and this compact UI design requirement by requirement. Confirm there are no generated APKs, screenshots, secrets, dead controls, or unrelated files in the diff.

- [ ] **Step 2: Re-run the completion verification commands if any review edit was made**

Any code or resource change after Task 5 requires rerunning its focused test plus the full Flutter tests, Android unit suite, analysis, and debug build.

- [ ] **Step 3: Stage all user-authorized changes**

Run:

```bash
git add -A
git status --short
```

Expected: every intended source, test, resource, and design/plan file is staged; build artifacts and `.superpowers/` are absent.

- [ ] **Step 4: Commit directly to master**

Run:

```bash
git commit -m "feat: add dynamic UDP diagnostics profiles and live updates"
```

Expected: one successful commit on `master` containing the complete implementation requested by the user.
