# App Control, Diagnostics, and Live Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the four-page SwitchNAT control app with named SS profiles, embedded bootstrap targets, proxy-aware TCP/STUN diagnostics, Switch presence, and an Android standard Live Update tied to the VPN session.

**Architecture:** Keep the validated TCP, static UDP, and dynamic peer-alias data plane unchanged. Add a versioned native profile repository, focused TCP/STUN diagnostic components that publish into the existing native snapshot, a shared Flutter controller for the four pages, and a notification controller that renders the same snapshot into the existing foreground notification.

**Tech Stack:** Flutter/Dart 3, Material 3, Kotlin 2.3/JVM 17, Android `VpnService`, AndroidX Core 1.17.0, JUnit 4, RFC 8489 STUN, RFC 5780 NAT behavior discovery.

## Global Constraints

- Runtime remains no-root and uses no privileged shell command.
- Preserve the validated TCP forwarding, static UDP bootstrap, and dynamic UDP peer-alias paths.
- UDP targets are per-profile bootstrap rules; the global STUN list is shared by all profiles.
- New profiles receive the exact captured 516-rule target list, expanding to 573 endpoints.
- Existing saved configuration migrates without silently replacing a blank or nonblank UDP target value.
- Selecting a profile never restarts an active VPN; applying it requires a manual stop/start.
- Startup automatically runs one SS TCP test and one SS UDP STUN test against the active profile.
- STUN probes for one classification attempt reuse one `UdpRelaySession`.
- A Binding-only response is a partial result and must not receive a fabricated cone label.
- The notification uses Android standard Live Updates only, never Xiaomi private extras.
- Notification permission denial or notification construction failure must not stop forwarding.
- Preserve all existing dirty-worktree changes. Checkpoints use path-scoped `git diff --check`; do not stage unrelated files.

---

### Task 1: Add the versioned native profile library and embedded defaults

**Files:**
- Create: `android/app/src/main/res/raw/default_udp_targets.txt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/config/ProfileLibrary.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/config/ProfileRepository.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/config/ProfileRepositoryTest.kt`
- Modify: `android/app/build.gradle.kts`

**Interfaces:**
- Produces `ServerProfile(id: String, name: String, config: ShadowsocksConfig)`.
- Produces `StunServerEndpoint(id: String, host: String, port: Int, enabled: Boolean)`.
- Produces `ProfileLibrary(schemaVersion: Int, profiles: List<ServerProfile>, selectedProfileId: String, stunServers: List<StunServerEndpoint>)`.
- Produces `ProfileRepository.load()`, `saveProfile()`, `selectProfile()`, `createProfile()`, `duplicateProfile()`, `renameProfile()`, `deleteProfile()`, and `saveStunServers()`.

- [ ] **Step 1: Capture the accepted device target list**

Run:

```bash
.toolchains/android-sdk/platform-tools/adb connect "$ADB_SERIAL"
.toolchains/android-sdk/platform-tools/adb -s "$ADB_SERIAL" shell run-as dev.switchnat.app cat shared_prefs/switchnat_config.xml
```

Expected: the XML contains a nonempty `udpTargets` entry. Decode XML entities and add the exact 516 rules, one rule per line, to `default_udp_targets.txt` with `apply_patch`.

- [ ] **Step 2: Write failing repository tests**

Create tests using a `FakePreferenceStore` and fixed ID supplier:

```kotlin
@Test fun newLibraryUsesEmbeddedTargetsAndFourStunPresets() {
    val repository = ProfileRepository(
        store = FakePreferenceStore(),
        defaultTargets = { "203.0.113.1:8123\n203.0.113.0/30:10025" },
        nextId = sequenceOf("profile-1", "stun-1", "stun-2", "stun-3", "stun-4").iterator()::next
    )
    val library = repository.load()
    assertEquals("profile-1", library.selectedProfileId)
    assertEquals(
        "203.0.113.1:8123\n203.0.113.0/30:10025",
        library.profiles.single().config.udpTargets
    )
    assertEquals(4, library.stunServers.size)
}

@Test fun migrationPreservesBlankLegacyTargets() {
    val store = FakePreferenceStore().apply {
        putString("serverHost", "203.0.113.10")
        putInt("serverPort", 8388)
        putString("password", "secret")
        putString("method", "aes-128-gcm")
        putInt("httpProxyPort", 7890)
        putString("udpTargets", "")
    }
    val library = repository(store).load()
    assertEquals("", library.profiles.single().config.udpTargets)
}

@Test fun activeProfileCannotBeDeleted() {
    val repository = repository(FakePreferenceStore())
    val library = repository.load()
    assertFailsWith<IllegalArgumentException> {
        repository.deleteProfile(library.selectedProfileId, activeProfileId = library.selectedProfileId)
    }
}
```

- [ ] **Step 3: Run the focused tests and verify RED**

Run:

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest --tests 'dev.switchnat.app.config.ProfileRepositoryTest'
```

Expected: compilation fails because the profile types and repository do not exist.

- [ ] **Step 4: Implement the pure repository model**

Define the core records and store boundary:

```kotlin
data class ServerProfile(
    val id: String,
    val name: String,
    val config: ShadowsocksConfig
)

data class StunServerEndpoint(
    val id: String,
    val host: String,
    val port: Int,
    val enabled: Boolean = true
)

data class ProfileLibrary(
    val schemaVersion: Int = 1,
    val profiles: List<ServerProfile>,
    val selectedProfileId: String,
    val stunServers: List<StunServerEndpoint>
) {
    val selectedProfile: ServerProfile
        get() = profiles.first { it.id == selectedProfileId }
}

interface PreferenceStore {
    fun contains(key: String): Boolean
    fun getString(key: String, fallback: String = ""): String
    fun getInt(key: String, fallback: Int = 0): Int
    fun putString(key: String, value: String)
}
```

Use one `profileLibraryJson` key, `org.json` serialization, stable IDs, schema version 1, four preset STUN records, legacy-key migration, name fallback to `host:port`, validation before writes, and the delete guards from the design. Add `testImplementation("org.json:json:20250517")` for JVM tests.

- [ ] **Step 5: Load the raw resource through an Android adapter**

Add `AndroidPreferenceStore` and:

```kotlin
fun Context.profileRepository(): ProfileRepository = ProfileRepository(
    store = AndroidPreferenceStore(
        getSharedPreferences("switchnat_config", Context.MODE_PRIVATE)
    ),
    defaultTargets = {
        resources.openRawResource(R.raw.default_udp_targets)
            .bufferedReader()
            .use { it.readText().trim() }
    }
)
```

- [ ] **Step 6: Verify repository behavior and target counts**

Add an assertion that the real raw file has 516 nonblank rules and, after `ShadowsocksConfig` parsing, 573 expanded endpoints. Run the focused test from Step 3.

Expected: all `ProfileRepositoryTest` cases pass.

- [ ] **Step 7: Checkpoint**

```bash
git diff --check -- android/app/src/main/res/raw/default_udp_targets.txt android/app/src/main/kotlin/dev/switchnat/app/config android/app/src/test/kotlin/dev/switchnat/app/config android/app/build.gradle.kts
```

Expected: no output.

---

### Task 2: Expose profiles and immutable active identity through the bridge

**Files:**
- Create: `lib/src/config/profile_library.dart`
- Modify: `lib/src/config/config_store.dart`
- Modify: `lib/src/native/native_bridge.dart`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/bridge/NativeBridge.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/vpn/VpnController.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt`
- Modify: `test/config/config_store_test.dart`
- Modify: `test/native/native_bridge_test.dart`

**Interfaces:**
- Produces Dart `ServerProfile`, `StunServerEndpoint`, and `ProfileLibrary` JSON models.
- Produces `AppBridge` as a fakeable Dart interface and `NativeBridge implements AppBridge`.
- Replaces single-config store calls with profile-library CRUD calls.
- Extends `NativeBridge.start(ServerProfile profile)`.
- Carries `EXTRA_PROFILE_ID` and `EXTRA_PROFILE_NAME` into the service.
- Adds native snapshot fields `activeProfileId`, `activeProfileName`, and `activeServerEndpoint`.

- [ ] **Step 1: Add failing Dart serialization and channel tests**

```dart
test('profile library round trips selected profile and global stun list', () {
  final library = ProfileLibrary.fromJson(<String, Object?>{
    'schemaVersion': 1,
    'selectedProfileId': 'p1',
    'profiles': <Object?>[
      <String, Object?>{
        'id': 'p1',
        'name': 'Tokyo',
        'config': validConfig.toJson(),
      },
    ],
    'stunServers': <Object?>[
      <String, Object?>{
        'id': 's1',
        'host': 'stun.cloudflare.com',
        'port': 3478,
        'enabled': true,
      },
    ],
  });
  expect(library.selectedProfile.name, 'Tokyo');
  expect(library.toJson()['selectedProfileId'], 'p1');
});

test('start sends profile identity and config', () async {
  await bridge.start(profile);
  expect(calls.single.arguments['profileId'], 'p1');
  expect(calls.single.arguments['profileName'], 'Tokyo');
});
```

- [ ] **Step 2: Run Dart tests and verify RED**

```bash
.toolchains/flutter/bin/flutter test test/config/config_store_test.dart test/native/native_bridge_test.dart
```

Expected: compilation fails because the profile models and new bridge signature do not exist.

- [ ] **Step 3: Implement Dart models and profile store methods**

The store API is:

```dart
abstract interface class ConfigStore {
  Future<ProfileLibrary> loadLibrary();
  Future<ProfileLibrary> saveProfile(ServerProfile profile);
  Future<ProfileLibrary> selectProfile(String id);
  Future<ProfileLibrary> createProfile({String? sourceId});
  Future<ProfileLibrary> duplicateProfile(String id);
  Future<ProfileLibrary> renameProfile(String id, String name);
  Future<ProfileLibrary> deleteProfile(String id);
  Future<ProfileLibrary> saveStunServers(List<StunServerEndpoint> servers);
  Future<String> loadDefaultUdpTargets();
}
```

Each method invokes the same-named native method and parses the returned complete library. Keep `SsConfig` as the nested configuration value.

Define the bridge boundary before the method-channel implementation:

```dart
abstract interface class AppBridge {
  Future<bool> requestVpnPermission();
  Future<bool> requestNotificationPermission();
  Future<bool> start(ServerProfile profile);
  Future<bool> stop();
  Future<bool> runTcpTest();
  Future<bool> runNatTest();
  Stream<ServiceStatus> get statusStream;
}

class NativeBridge implements AppBridge {
  // Existing method and event channels remain the concrete transport.
}
```

- [ ] **Step 4: Replace legacy bridge handlers with repository operations**

In `NativeBridge.kt`, create one repository from the activity context and add handlers:

```kotlin
"loadLibrary" -> result.success(repository.load().toMap())
"saveProfile" -> mutateLibrary(result) { repository.saveProfile(profileFromCall(call)) }
"selectProfile" -> mutateLibrary(result) { repository.selectProfile(call.argument<String>("id").orEmpty()) }
"createProfile" -> mutateLibrary(result) { repository.createProfile(call.argument("sourceId")) }
"duplicateProfile" -> mutateLibrary(result) { repository.duplicateProfile(requiredId(call)) }
"renameProfile" -> mutateLibrary(result) { repository.renameProfile(requiredId(call), requiredName(call)) }
"deleteProfile" -> mutateLibrary(result) {
    repository.deleteProfile(requiredId(call), diagnostics.snapshot().activeProfileId)
}
"saveStunServers" -> mutateLibrary(result) { repository.saveStunServers(stunServersFromCall(call)) }
"loadDefaultUdpTargets" -> result.success(repository.defaultUdpTargets())
```

Retain legacy keys for one-way migration only. Return `BAD_PROFILE` or `BAD_STUN_SERVERS` errors with actionable messages.

- [ ] **Step 5: Carry immutable active profile identity**

Change the Dart start call to:

```dart
Future<bool> start(ServerProfile profile) async {
  final errors = profile.config.validate();
  if (errors.isNotEmpty) throw ArgumentError(errors.join('; '));
  return await controlChannel.invokeMethod<bool>('start', <String, Object?>{
        ...profile.config.toJson(),
        'profileId': profile.id,
        'profileName': profile.name,
      }) ??
      false;
}
```

Add `profileId` and `profileName` parameters to `VpnController.start`, put them into explicit extras, read them in `SwitchNatVpnService`, and publish them before STARTING. Clear active identity only on failed startup or complete stop.

In this task, add the three active identity fields and `beginSession/endSession`
methods to `DiagnosticsSnapshot`; Task 3 extends the same session model with
timestamps and Switch/test state. This makes the delete guard compile before
Task 3 starts.

- [ ] **Step 6: Run focused Dart and native tests**

Run Step 2 and:

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest --tests 'dev.switchnat.app.config.ProfileRepositoryTest' --tests 'dev.switchnat.app.diagnostics.DiagnosticsTest'
```

Expected: all focused tests pass.

---

### Task 3: Extend diagnostics with session, tests, and Switch presence

**Files:**
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/diagnostics/Diagnostics.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/proxy/HttpConnectProxy.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/udp/LocalUdpEndpointProxy.kt`
- Modify: `android/app/src/test/kotlin/dev/switchnat/app/diagnostics/DiagnosticsTest.kt`
- Modify: `android/app/src/test/kotlin/dev/switchnat/app/proxy/HttpConnectProxyTest.kt`
- Modify: `lib/src/state/service_status.dart`
- Modify: `test/state/service_status_test.dart`

**Interfaces:**
- Produces `ConnectivityTestState`, `NatTestState`, and `SwitchPresence`.
- Adds active-profile identity, session timestamps, TCP result, NAT result, and `lastSwitchActivityMillis` to `DiagnosticsSnapshot`.
- Produces matching immutable Dart status fields.

- [ ] **Step 1: Write failing native state-transition tests**

```kotlin
@Test fun switchPresenceUsesCurrentSessionAndActivityWindow() {
    val clock = FakeClock(10_000L)
    val diagnostics = Diagnostics(clockMillis = clock::now)
    diagnostics.beginSession("p1", "Tokyo", "203.0.113.10:8388")
    assertEquals(SwitchPresence.WAITING, diagnostics.snapshot().switchPresence)
    diagnostics.recordSwitchActivity("10.146.3.249")
    assertEquals(SwitchPresence.CONNECTED, diagnostics.snapshot().switchPresence)
    clock.advance(60_001L)
    assertEquals(SwitchPresence.INACTIVE, diagnostics.snapshot().switchPresence)
    diagnostics.endSession()
    assertEquals("", diagnostics.snapshot().learnedSwitchIp)
}
```

Also assert `beginTcpTest/completeTcpTest/failTcpTest` and `beginNatTest/completeNatTest/failNatTest` maps.

- [ ] **Step 2: Run diagnostics tests and verify RED**

Use the native command from Task 2 Step 6, restricted to `DiagnosticsTest`.

Expected: missing session and diagnostic state APIs.

- [ ] **Step 3: Implement immutable result models**

```kotlin
enum class DiagnosticRunState { IDLE, TESTING, AVAILABLE, PARTIAL, UNAVAILABLE }
enum class SwitchPresence { WAITING, CONNECTED, INACTIVE }

data class TcpTestResult(
    val state: DiagnosticRunState = DiagnosticRunState.IDLE,
    val latencyMillis: Long = 0,
    val completedAtMillis: Long = 0,
    val error: String = ""
)

data class NatTestResult(
    val state: DiagnosticRunState = DiagnosticRunState.IDLE,
    val legacyLabel: String = "",
    val mappedAddress: String = "",
    val mappingBehavior: String = "",
    val filteringBehavior: String = "",
    val rttMillis: Long = 0,
    val stunServer: String = "",
    val completedAtMillis: Long = 0,
    val error: String = ""
)
```

Make every compound update synchronized, return copied immutable values in snapshots, and derive presence using a 60,000 ms threshold.

- [ ] **Step 4: Record both TCP and UDP Switch traffic**

On accepted HTTP client sockets call:

```kotlin
client.inetAddress?.hostAddress?.let(diagnostics::recordSwitchActivity)
```

Replace `setLearnedSwitchIp` in the UDP path with `recordSwitchActivity`. Reset the fields at session start and end.

- [ ] **Step 5: Add matching Dart parsing**

Add typed Dart enums/models and parse all new keys with safe defaults. The JSON names must exactly match the native `toMap()` keys.

- [ ] **Step 6: Verify native and Dart tests**

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest --tests 'dev.switchnat.app.diagnostics.DiagnosticsTest' --tests 'dev.switchnat.app.proxy.HttpConnectProxyTest'
cd ..
.toolchains/flutter/bin/flutter test test/state/service_status_test.dart
```

Expected: all tests pass.

---

### Task 4: Implement the encrypted SS TCP connectivity test

**Files:**
- Create: `android/app/src/main/kotlin/dev/switchnat/app/diagnostics/SsTcpConnectivityTester.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/diagnostics/SsTcpConnectivityTesterTest.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksTcpClient.kt`

**Interfaces:**
- Produces `SsTcpConnectivityTester.test(): TcpProbeOutcome`.
- Consumes a `TcpOutboundFactory`, monotonic clock, target `TargetAddress("1.1.1.1", 80)`, and bounded read timeout.

- [ ] **Step 1: Write failing success and failure tests**

```kotlin
@Test fun validHttpStatusProvesEncryptedRelayPath() {
    val outbound = FakeTcpOutbound("HTTP/1.1 301 Moved Permanently\r\n\r\n")
    val result = SsTcpConnectivityTester(factory = FakeFactory(outbound), nanoTime = tickingClock()).test()
    assertTrue(result.available)
    assertEquals(TargetAddress("1.1.1.1", 80), outbound.target)
    assertTrue(outbound.writtenText.contains("HEAD / HTTP/1.1"))
    assertTrue(outbound.writtenText.contains("Host: one.one.one.one"))
}

@Test fun malformedResponseIsUnavailable() {
    val result = testerWithResponse("not http").test()
    assertFalse(result.available)
    assertEquals("Invalid HTTP status line", result.error)
}
```

- [ ] **Step 2: Run and verify RED**

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest --tests 'dev.switchnat.app.diagnostics.SsTcpConnectivityTesterTest'
```

- [ ] **Step 3: Add read-timeout control to outbound sockets**

Extend `TcpOutbound` with `fun setReadTimeout(timeoutMillis: Int)`. Implement it with `socket.soTimeout = timeoutMillis` in both socket-backed outbound classes and in test fakes.

- [ ] **Step 4: Implement the bounded probe**

```kotlin
data class TcpProbeOutcome(
    val available: Boolean,
    val latencyMillis: Long,
    val error: String = ""
)

fun test(): TcpProbeOutcome {
    val start = nanoTime()
    return runCatching {
        factory.open(TargetAddress("1.1.1.1", 80)).use { outbound ->
            outbound.setReadTimeout(5_000)
            outbound.output().write(
                "HEAD / HTTP/1.1\r\nHost: one.one.one.one\r\nConnection: close\r\n\r\n"
                    .toByteArray(Charsets.US_ASCII)
            )
            outbound.output().flush()
            val line = readAsciiLine(outbound.input(), maxBytes = 4_096)
            require(HTTP_STATUS.matches(line)) { "Invalid HTTP status line" }
        }
        TcpProbeOutcome(true, (nanoTime() - start) / 1_000_000)
    }.getOrElse {
        TcpProbeOutcome(false, (nanoTime() - start) / 1_000_000, it.message ?: it.javaClass.simpleName)
    }
}
```

`readAsciiLine` reads one byte at a time, stops at LF, strips a preceding CR,
and throws `HTTP status line exceeds 4096 bytes` at the bound. It never wraps
the untrusted stream in an unbounded `BufferedReader.readLine()`.

- [ ] **Step 5: Run TCP tests**

Run Step 2 plus existing `ShadowsocksTcpClientTest` and `HttpConnectProxyTest`.

Expected: all pass.

---

### Task 5: Implement STUN framing and RFC 5780 classification over SS UDP

**Files:**
- Create: `android/app/src/main/kotlin/dev/switchnat/app/diagnostics/StunProtocol.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/diagnostics/StunNatTester.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/diagnostics/StunProtocolTest.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/diagnostics/StunNatTesterTest.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksUdpClient.kt`
- Modify: `android/app/src/test/kotlin/dev/switchnat/app/udp/LocalUdpEndpointProxyTest.kt`

**Interfaces:**
- Produces `StunCodec.bindingRequest()` and `parseBindingResponse()`.
- Produces `StunNatTester.test(servers): NatProbeOutcome`.
- Consumes one `UdpRelaySession` per attempt. Its timeout-capable
  `receive(bufferSize, timeoutMillis)` method lets STUN retry or cancel without
  leaving a permanently blocked socket thread.

- [ ] **Step 1: Write failing codec vector tests**

Use fixed transaction ID `01..0c` and assert:

```kotlin
val request = StunCodec.bindingRequest(transactionId, changeIp = true, changePort = true)
assertEquals(0x0001, request.u16(0))
assertEquals(0x2112A442, request.u32(4))
assertArrayEquals(transactionId, request.copyOfRange(8, 20))
assertTrue(request.containsAttribute(type = 0x0003, value = byteArrayOf(0, 0, 0, 6)))
```

Build response fixtures with XOR-MAPPED-ADDRESS and OTHER-ADDRESS. Assert decoded IPv4 and port, padding, mismatched transaction rejection, error-response rejection, and truncated-attribute rejection.

- [ ] **Step 2: Run codec tests and verify RED**

Run the focused native test command for `StunProtocolTest`.

- [ ] **Step 3: Implement strict STUN codec**

Implement the 20-byte RFC 8489 header, 32-bit attribute padding, magic-cookie XOR for IPv4 address/port, CHANGE-REQUEST, XOR-MAPPED-ADDRESS, OTHER-ADDRESS, RESPONSE-ORIGIN, and ERROR-CODE. Reject non-Binding success responses and lengths exceeding the datagram.

- [ ] **Step 4: Add bounded UDP relay receives**

Change the existing interface compatibly:

```kotlin
interface UdpRelaySession : UdpRelaySender, AutoCloseable {
    fun receive(
        bufferSize: Int = 65_535,
        timeoutMillis: Int = 0
    ): DecodedUdpPacket
}
```

`ShadowsocksUdpClient.receive` validates a nonnegative timeout and assigns
`socket.soTimeout = timeoutMillis` before receiving. Existing production calls
keep the zero/infinite default; update test fakes with the new second parameter.
The STUN transaction sends one request, receives until the matching transaction
ID arrives or the deadline expires, and treats `SocketTimeoutException` as no
response.

- [ ] **Step 5: Write failing classification tests**

Script a fake relay session for:

```kotlin
@Test fun endpointIndependentMappingAndFilteringIsFullCone() {
    val result = scriptedTester(
        primary = response(mapped = A, other = OTHER),
        changedIpAndPort = response(mapped = A),
        alternateIp = response(mapped = A, other = OTHER)
    ).test(servers)
    assertEquals("Full Cone", result.legacyLabel)
    assertEquals("Endpoint-independent", result.mappingBehavior)
    assertEquals("Endpoint-independent", result.filteringBehavior)
}

@Test fun bindingOnlyResponseIsPartialNotConeLabel() {
    val result = scriptedTester(primary = response(mapped = A, other = null)).test(servers)
    assertTrue(result.partial)
    assertEquals("", result.legacyLabel)
    assertEquals("Unknown", result.filteringBehavior)
}
```

Add Restricted Cone, Port-Restricted Cone, Symmetric NAT, invalid-first-server fallback, capable-server preference, timeout, cancellation, and same-session assertions.

- [ ] **Step 6: Implement the exact classification sequence**

For each enabled server:

1. Binding Test I to the primary address.
2. If OTHER-ADDRESS is absent, retain a partial candidate and continue.
3. Mapping Test II to alternate IP with primary port, then Test III to alternate IP and alternate port only when needed.
4. Filtering Test II with change IP+port; if no response, Filtering Test III with change port only.
5. Map EIM+EIF to Full Cone, EIM+ADF to Restricted Cone, EIM+APDF to Port-Restricted Cone, and any non-EIM mapping to Symmetric NAT.

Return the first full classification. If none exists, return the first valid partial. Close the one relay session in `finally`.

- [ ] **Step 7: Run STUN tests**

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest --tests 'dev.switchnat.app.diagnostics.StunProtocolTest' --tests 'dev.switchnat.app.diagnostics.StunNatTesterTest'
```

Expected: all codec and classification branches pass.

---

### Task 6: Orchestrate automatic and manual diagnostics in the VPN service

**Files:**
- Create: `android/app/src/main/kotlin/dev/switchnat/app/diagnostics/ConnectionDiagnosticsCoordinator.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/diagnostics/ConnectionDiagnosticsCoordinatorTest.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/vpn/VpnController.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/bridge/NativeBridge.kt`
- Modify: `lib/src/native/native_bridge.dart`

**Interfaces:**
- Produces service actions `ACTION_TEST_TCP` and `ACTION_TEST_NAT`.
- Produces Dart `runTcpTest()` and `runNatTest()`.
- Produces a single-flight coordinator scoped to the active profile.

- [ ] **Step 1: Write failing coordinator tests**

```kotlin
@Test fun startupRunsBothTestsAndPublishesResults() {
    val diagnostics = Diagnostics()
    val coordinator = coordinator(tcp = successTcp(48), nat = fullCone("203.0.113.8:51234"), diagnostics)
    coordinator.runStartupTests()
    coordinator.awaitIdle()
    assertEquals(DiagnosticRunState.AVAILABLE, diagnostics.snapshot().tcpTest.state)
    assertEquals("Full Cone", diagnostics.snapshot().natTest.legacyLabel)
}

@Test fun secondNatRequestSupersedesFirstWithoutTwoConcurrentSessions() {
    val probe = BlockingNatProbe()
    val coordinator = coordinator(nat = probe)
    coordinator.runNatTest()
    coordinator.runNatTest()
    assertEquals(1, probe.maxConcurrent)
}
```

- [ ] **Step 2: Implement coordinator lifecycle**

Use a two-thread executor, one `Future` per test type, generation counters to ignore stale completions, and `close()` that cancels tasks and shuts down the executor. Publish TESTING before work and AVAILABLE/PARTIAL/UNAVAILABLE afterward.

- [ ] **Step 3: Wire service actions**

After RUNNING:

```kotlin
diagnosticsCoordinator = ConnectionDiagnosticsCoordinator(
    config = runtimeConfig,
    diagnostics = diagnostics,
    tcpFactory = { ShadowsocksTcpClient(runtimeConfig, this) },
    udpFactory = { ShadowsocksUdpClient(runtimeConfig, protector = this) }
).also { it.runStartupTests() }
```

Handle manual actions only when `running == true`; otherwise return an error through diagnostics. Close the coordinator before forwarding resources during every stop/failure path.

- [ ] **Step 4: Add bridge methods and tests**

Map `runTcpTest` and `runNatTest` to `VpnController` service actions. Dart methods return a boolean indicating that the request was accepted.

- [ ] **Step 5: Run coordinator, bridge, and service-adjacent tests**

Run all diagnostics tests plus existing VPN shutdown/startup tests.

Expected: all pass and cancellation leaves no live worker threads.

---

### Task 7: Build the shared Flutter controller and four pages

**Files:**
- Create: `lib/src/state/app_controller.dart`
- Create: `lib/src/screens/home_screen.dart`
- Modify: `lib/src/app.dart`
- Rewrite: `lib/src/screens/config_screen.dart`
- Modify: `lib/src/screens/status_screen.dart`
- Modify: `lib/src/screens/logs_screen.dart`
- Create: `test/state/app_controller_test.dart`
- Create: `test/screens/home_screen_test.dart`
- Modify: `test/screens/config_screen_test.dart`
- Modify: `test/screens/status_screen_test.dart`
- Modify: `test/widget_test.dart`

**Interfaces:**
- Produces `AppController extends ChangeNotifier` as the single Flutter orchestration state.
- Home, Config, Status, and Logs receive the same controller/status stream.
- Preserves page state with one `IndexedStack`.

- [ ] **Step 1: Write failing controller tests**

```dart
test('start uses selected profile and requests both permissions', () async {
  final controller = AppController(bridge: fakeBridge, store: fakeStore);
  await controller.initialize();
  await controller.start();
  expect(fakeBridge.notificationPermissionRequests, 1);
  expect(fakeBridge.vpnPermissionRequests, 1);
  expect(fakeBridge.startedProfileId, controller.library!.selectedProfileId);
});

test('selecting while running creates pending mismatch without restart', () async {
  fakeBridge.emitStatus(runningStatus(activeProfileId: 'p1'));
  await controller.selectProfile('p2');
  expect(controller.hasPendingProfileChange, isTrue);
  expect(fakeBridge.startCalls, 0);
  expect(fakeBridge.stopCalls, 0);
});
```

- [ ] **Step 2: Implement `AppController`**

It owns library load/mutations, selected-profile editor state, status subscription, busy flags, start/stop, manual tests, feedback, and disposal. Start requests notification permission but ignores a false result, then requires VPN permission before calling `bridge.start(selectedProfile)`.

- [ ] **Step 3: Write failing Home widget tests**

Assert these visible states and interactions:

```dart
expect(find.text('Acceleration is off'), findsOneWidget);
await tester.tap(find.byKey(const Key('master-switch')));
expect(fakeController.startCalls, 1);

fakeController.status = runningStatus(
  activeProfileName: 'Tokyo 01',
  switchPresence: SwitchPresence.waiting,
  natState: DiagnosticRunState.available,
  natLegacyLabel: 'Full Cone',
  mappedAddress: '203.0.113.8:51234',
);
expect(find.text('Waiting for Switch'), findsOneWidget);
expect(find.text('Full Cone'), findsOneWidget);
await tester.tap(find.byKey(const Key('nat-card')));
expect(fakeController.natTestCalls, 1);
```

- [ ] **Step 4: Implement Home**

Use the approved single-column layout: master card, current-node card, two metric cards for Switch and SS UDP NAT, and a pending-profile warning. The current-node latency area triggers TCP retest; `Change` opens a bottom-sheet profile picker; the NAT card triggers STUN retest only when RUNNING and not TESTING.

- [ ] **Step 5: Refactor Config around profile CRUD**

Keep the current form styling. Add profile selector and Add/Duplicate/Rename/Delete actions, server fields, collapsed bootstrap targets with count and Restore Defaults, global STUN editor with presets, and Save Profile. Track a baseline profile to detect unsaved edits and guard navigation with a discard dialog.

- [ ] **Step 6: Extend Status and Logs**

Status renders active/selected identity, duration, TCP test, NAT details, Switch last activity, and Live Update state before existing counters. Logs includes the native structured events and keeps Copy Logs.

- [ ] **Step 7: Install four destinations**

```dart
destinations: const <NavigationDestination>[
  NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: 'Home'),
  NavigationDestination(icon: Icon(Icons.settings_outlined), selectedIcon: Icon(Icons.settings), label: 'Config'),
  NavigationDestination(icon: Icon(Icons.monitor_heart_outlined), selectedIcon: Icon(Icons.monitor_heart), label: 'Status'),
  NavigationDestination(icon: Icon(Icons.article_outlined), selectedIcon: Icon(Icons.article), label: 'Logs'),
],
```

Make Home index 0 and notification taps open the default Home route.

- [ ] **Step 8: Run Flutter verification**

```bash
.toolchains/flutter/bin/flutter test
.toolchains/flutter/bin/flutter analyze
```

Expected: all tests pass and analysis reports no issues.

---

### Task 8: Add Android notification permission and standard Live Update

**Files:**
- Create: `android/app/src/main/kotlin/dev/switchnat/app/notification/LiveActivityModel.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/notification/ConnectionLiveActivityController.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/notification/LiveActivityModelTest.kt`
- Create: `android/app/src/main/res/drawable/ic_stat_switchnat.xml`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/bridge/NotificationPermissionCoordinator.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/bridge/NotificationPermissionCoordinatorTest.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/MainActivity.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/bridge/NativeBridge.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt`
- Modify: `lib/src/native/native_bridge.dart`

**Interfaces:**
- Produces `LiveActivityModel.from(snapshot)` pure rendering state.
- Produces `ConnectionLiveActivityController.starting()`, `connected()`, and `stop()`.
- Produces Dart `requestNotificationPermission()`.

- [ ] **Step 1: Add dependency and permissions**

Add:

```kotlin
implementation("androidx.core:core:1.17.0")
```

Declare:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />
```

- [ ] **Step 2: Write failing pure rendering tests**

```kotlin
@Test fun runningSnapshotRendersNodeSwitchAndNat() {
    val model = LiveActivityModel.from(
        snapshot(activeProfileName = "Tokyo 01", switchPresence = CONNECTED, natLabel = "Full Cone")
    )
    assertEquals("SwitchNAT acceleration active", model.title)
    assertTrue(model.compactText.contains("Tokyo 01"))
    assertTrue(model.compactText.contains("Switch connected"))
    assertTrue(model.expandedText.contains("Full Cone"))
    assertTrue(model.requestPromotedOngoing)
}

@Test fun identicalVisibleStateDoesNotRequireRepublish() {
    assertEquals(LiveActivityModel.from(snapshot()), LiveActivityModel.from(snapshot()))
}
```

- [ ] **Step 3: Implement the notification controller**

Create the silent LOW channel once. Build with:

```kotlin
NotificationCompat.Builder(context, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_stat_switchnat)
    .setContentTitle(model.title)
    .setContentText(model.compactText)
    .setStyle(NotificationCompat.BigTextStyle().bigText(model.expandedText))
    .setContentIntent(homePendingIntent)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setWhen(sessionStartedAtMillis)
    .setUsesChronometer(true)
    .setRequestPromotedOngoing(model.requestPromotedOngoing)
    .build()
```

`starting()` passes the ordinary notification to an injected
`foregroundStarter: (Notification) -> Unit`, which the service binds to
`startForeground(NOTIFICATION_ID, notification)`. `connected()` starts one
two-second sampler, compares `LiveActivityModel` values, and calls an injected
`NotificationPublisher` only after a visible change. The Android publisher
wraps `NotificationManagerCompat.notify` in `runCatching`, records
`LIVE_UPDATE_PUBLISH` on failure, and never throws into the service.
`stop()` cancels the sampler; the service removes the same notification ID
with `stopForeground(STOP_FOREGROUND_REMOVE)`.

- [ ] **Step 4: Implement nonblocking permission flow**

`NotificationPermissionCoordinator` returns true immediately below API 33, checks `POST_NOTIFICATIONS` when already granted, launches one request at a time, and completes from `MainActivity.onRequestPermissionsResult`. Add `requestNotificationPermission` to both native and Dart bridges. A denial returns false without changing VPN state.

- [ ] **Step 5: Replace direct service notification construction**

Instantiate one controller in the service. Call `starting()` before asynchronous startup, `connected()` after RUNNING, and `stop()` before every foreground removal in stop, failure, revocation, and destruction paths. Delete the old `buildNotification` method.

- [ ] **Step 6: Run notification, bridge, and VPN tests**

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest --tests 'dev.switchnat.app.notification.*' --tests 'dev.switchnat.app.bridge.*' --tests 'dev.switchnat.app.vpn.*'
```

Expected: all tests pass.

---

### Task 9: Full regression, APK, and Xiaomi device acceptance

**Files:**
- Modify: `docs/testing/xiaomi15-android16-verification.md`

**Interfaces:**
- Produces a debug APK installed over the accepted app data.
- Produces device evidence for automatic diagnostics, P2P forwarding, and Live Update removal.

- [ ] **Step 1: Run full static and unit verification**

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest
cd ..
.toolchains/flutter/bin/flutter test
.toolchains/flutter/bin/flutter analyze
```

Expected: Gradle succeeds, all Flutter tests pass, and analysis reports no issues.

- [ ] **Step 2: Build the debug APK**

```bash
flutter build apk --debug --build-number=2002
```

Expected: `build/app/outputs/flutter-apk/app-debug.apk` exists.

- [ ] **Step 3: Install without clearing app data**

```bash
.toolchains/android-sdk/platform-tools/adb connect "$ADB_SERIAL"
.toolchains/android-sdk/platform-tools/adb -s "$ADB_SERIAL" install -r build/app/outputs/flutter-apk/app-debug.apk
```

Expected: `Success`.

- [ ] **Step 4: Verify migration and startup diagnostics**

Launch the app. Confirm the existing server and 516-rule UDP field remain present. Start acceleration and confirm the Home cards move through TCP/NAT Testing to a result, mapped public endpoint is shown, and Switch changes from Waiting to Connected after traffic.

- [ ] **Step 5: Verify Android Live Update state**

Run:

```bash
.toolchains/android-sdk/platform-tools/adb -s "$ADB_SERIAL" shell dumpsys notification
```

Expected while connected: the SwitchNAT notification is ongoing and requests promoted ongoing treatment. Observe HyperOS island presentation when allowed. Disconnect in Home and rerun the command; the SwitchNAT foreground notification must be absent.

- [ ] **Step 6: Re-run Switch and game acceptance**

Confirm the Switch connection test remains NAT A with correct public IP and speed. Join and sustain a Mario Kart 8 room. Exercise a P2P endpoint change or player join/leave without restarting acceleration and confirm dynamic aliases remain active.

- [ ] **Step 7: Verify denial isolation**

Disable SwitchNAT notification permission in system settings, start acceleration again, and verify TCP/UDP forwarding plus the Switch connection test still work. Re-enable permission afterward.

- [ ] **Step 8: Final diff audit**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors. Review every changed path against the design and confirm no unrelated dirty file was overwritten or staged.
