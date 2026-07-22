# Dynamic UDP Peer Alias Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver unknown P2P UDP packets to Nintendo Switch through dynamically allocated phone-LAN alias sockets and forward Switch replies to the real peers through the original Shadowsocks UDP relay session.

**Architecture:** Keep configured public endpoints as the static bootstrap path. Add a focused `UdpPeerAliasManager` keyed by Switch flow plus remote peer; it binds `phoneLanIp:0`, connects the socket to the Switch, and translates both directions without changing the VPN. Extend native and Flutter diagnostics so device testing can distinguish relay flows from dynamic aliases.

**Tech Stack:** Kotlin/JVM 17, Android `VpnService`, Java `DatagramSocket`, JUnit 4, Flutter/Dart, Flutter widget and unit tests.

## Global Constraints

- Runtime requires no root permission and no privileged shell command.
- Do not rebuild the VPN when a peer appears.
- Reuse one `UdpRelaySession` for every destination contacted by one Switch source `IP:port`.
- Preserve the existing TCP path and exact configured-endpoint UDP path.
- Dynamic aliases bind only to the detected Switch-reachable phone LAN address on an OS-assigned high port.
- Idle timeout is 5 minutes; sweep interval is 30 seconds.
- Limits are 128 aliases per Switch flow and 512 aliases globally.
- Preserve all pre-existing worktree edits. Production-code checkpoints use `git diff --check` rather than commits because the target files already contain overlapping uncommitted user work.
- This plan does not implement multi-server profiles or config-screen layout changes.

---

### Task 1: Publish dynamic UDP diagnostics end to end

**Files:**
- Modify: `android/app/src/test/kotlin/dev/switchnat/app/diagnostics/DiagnosticsTest.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/diagnostics/Diagnostics.kt`
- Modify: `test/state/service_status_test.dart`
- Modify: `lib/src/state/service_status.dart`

**Interfaces:**
- Produces native snapshot fields `activeUdpFlows`, `activePeerAliases`, `peerAliasesCreated`, and `peerAliasesExpired`.
- Produces matching immutable Dart `ServiceStatus` properties.
- Keeps legacy `activeMappings` unchanged.

- [ ] **Step 1: Add a failing native diagnostics assertion**

Extend `snapshotContainsExactCountersAndError()` with:

```kotlin
diagnostics.setActiveUdpFlows(2)
diagnostics.setActivePeerAliases(4)
diagnostics.incrementPeerAliasesCreated()
diagnostics.incrementPeerAliasesExpired()

assertEquals(2, map["activeUdpFlows"])
assertEquals(4, map["activePeerAliases"])
assertEquals(1L, map["peerAliasesCreated"])
assertEquals(1L, map["peerAliasesExpired"])
```

- [ ] **Step 2: Run the native test and verify RED**

Run:

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest --tests 'dev.switchnat.app.diagnostics.DiagnosticsTest'
```

Expected: Kotlin test compilation fails because the four new diagnostics methods do not exist.

- [ ] **Step 3: Add the native counters and map fields**

Add atomics and methods to `Diagnostics`:

```kotlin
private val activeUdpFlows = AtomicInteger(0)
private val activePeerAliases = AtomicInteger(0)
private val peerAliasesCreated = AtomicLong(0)
private val peerAliasesExpired = AtomicLong(0)

fun setActiveUdpFlows(count: Int) = activeUdpFlows.set(count)
fun setActivePeerAliases(count: Int) = activePeerAliases.set(count)
fun incrementPeerAliasesCreated() = peerAliasesCreated.incrementAndGet()
fun incrementPeerAliasesExpired() = peerAliasesExpired.incrementAndGet()
```

Add the same four typed fields to `DiagnosticsSnapshot`, populate them in
`snapshot()`, and emit them in `toMap()`.

- [ ] **Step 4: Run the native test and verify GREEN**

Run the command from Step 2.

Expected: `DiagnosticsTest` passes.

- [ ] **Step 5: Add a failing Dart status parsing assertion**

Add the four keys to the JSON in `service_status_test.dart` and assert:

```dart
expect(status.activeUdpFlows, 2);
expect(status.activePeerAliases, 4);
expect(status.peerAliasesCreated, 5);
expect(status.peerAliasesExpired, 3);
```

- [ ] **Step 6: Run the Dart test and verify RED**

Run:

```bash
.toolchains/flutter/bin/flutter test test/state/service_status_test.dart
```

Expected: Dart compilation fails because the new `ServiceStatus` getters do not exist.

- [ ] **Step 7: Add matching Dart fields**

Add required constructor parameters, final `int` properties, and parsing with
zero defaults:

```dart
activeUdpFlows: json['activeUdpFlows'] as int? ?? 0,
activePeerAliases: json['activePeerAliases'] as int? ?? 0,
peerAliasesCreated: json['peerAliasesCreated'] as int? ?? 0,
peerAliasesExpired: json['peerAliasesExpired'] as int? ?? 0,
```

- [ ] **Step 8: Run both focused tests and checkpoint**

Run the commands from Steps 2 and 6, then:

```bash
git diff --check -- android/app/src/main/kotlin/dev/switchnat/app/diagnostics/Diagnostics.kt android/app/src/test/kotlin/dev/switchnat/app/diagnostics/DiagnosticsTest.kt lib/src/state/service_status.dart test/state/service_status_test.dart
```

Expected: both tests pass and `git diff --check` prints nothing.

---

### Task 2: Build the dynamic alias round-trip core

**Files:**
- Create: `android/app/src/main/kotlin/dev/switchnat/app/udp/UdpPeerAliasManager.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/udp/UdpPeerAliasManagerTest.kt`

**Interfaces:**
- Consumes: `TargetAddress`, `UdpRelaySender`, `Diagnostics`, an optional local `InetAddress`, and a Switch `InetSocketAddress`.
- Produces: `UdpPeerAliasManager.start()`, `forwardInbound(flowKey, switchEndpoint, remoteEndpoint, payload, relaySender)`, `closeFlow(flowKey)`, and `close()`.
- Produces internal observability `activeAliasCount` and the returned local alias endpoint.

- [ ] **Step 1: Write the failing unknown-peer round-trip test**

Create a test that binds a real loopback Switch socket, starts the manager with
`127.0.0.1`, injects a payload from `TargetAddress("198.51.100.20", 14290)`,
receives it on the Switch socket, replies to the observed packet source, and
waits for a recording sender to contain the real remote target and reply bytes:

```kotlin
@Test
fun unknownPeerUsesLocalAliasAndForwardsSwitchReplyToRealPeer() {
    val diagnostics = Diagnostics()
    val relay = RecordingUdpRelaySender()
    val switchSocket = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
    val switchEndpoint = switchSocket.localSocketAddress as InetSocketAddress
    val remote = TargetAddress("198.51.100.20", 14290)
    val manager = UdpPeerAliasManager(
        localAddress = InetAddress.getByName("127.0.0.1"),
        diagnostics = diagnostics
    ).also { it.start() }

    val alias = manager.forwardInbound(
        flowKey = switchEndpoint,
        switchEndpoint = switchEndpoint,
        remoteEndpoint = remote,
        payload = byteArrayOf(9, 8, 7),
        relaySender = relay
    )

    val inbound = receivePacket(switchSocket)
    assertEquals(alias, inbound.socketAddress)
    assertArrayEquals(byteArrayOf(9, 8, 7), inbound.payload())

    switchSocket.send(
        DatagramPacket(byteArrayOf(1, 2, 3), 3, inbound.socketAddress)
    )
    waitUntil { relay.sent.isNotEmpty() }
    assertEquals(remote, relay.sent.single().first)
    assertArrayEquals(byteArrayOf(1, 2, 3), relay.sent.single().second)

    manager.close()
    switchSocket.close()
}
```

The test file also defines real-socket `receivePacket`, `payload`, `waitUntil`,
and a synchronized `RecordingUdpRelaySender : UdpRelaySender`.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest --tests 'dev.switchnat.app.udp.UdpPeerAliasManagerTest'
```

Expected: test compilation fails because `UdpPeerAliasManager` does not exist.

- [ ] **Step 3: Implement the minimal real-socket manager**

Create:

```kotlin
data class UdpPeerAliasKey(
    val flowKey: InetSocketAddress,
    val remoteEndpoint: TargetAddress
)

fun interface UdpAliasSocketFactory {
    fun bind(localAddress: InetAddress, switchEndpoint: InetSocketAddress): DatagramSocket
}

class DatagramUdpAliasSocketFactory : UdpAliasSocketFactory {
    override fun bind(
        localAddress: InetAddress,
        switchEndpoint: InetSocketAddress
    ): DatagramSocket = DatagramSocket(null).also { socket ->
        try {
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(localAddress, 0))
            socket.connect(switchEndpoint)
        } catch (error: Throwable) {
            socket.close()
            throw error
        }
    }
}
```

`UdpPeerAliasManager` uses a synchronized map. `forwardInbound()` checks
`running`, fails with `UDP_ALIAS_BIND` when `localAddress` is null, creates one
connected alias socket, starts a daemon receive thread, sends the inbound
payload through that socket, increments `udpWritten`, and returns the socket's
local endpoint. The receive thread forwards every Switch payload through the
stored `UdpRelaySender`, increments `udpRead`, `udpAccepted`, and `udpSent`, and
records `alias-forwarded`. `close()` closes all sockets and interrupts threads.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2.

Expected: the unknown-peer round trip passes with real UDP loopback sockets.

- [ ] **Step 5: Checkpoint the core**

Run:

```bash
git diff --check -- android/app/src/main/kotlin/dev/switchnat/app/udp/UdpPeerAliasManager.kt android/app/src/test/kotlin/dev/switchnat/app/udp/UdpPeerAliasManagerTest.kt
```

Expected: no output.

---

### Task 3: Add reuse, concurrency, expiry, limits, and flow cleanup

**Files:**
- Modify: `android/app/src/test/kotlin/dev/switchnat/app/udp/UdpPeerAliasManagerTest.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/udp/UdpPeerAliasManager.kt`

**Interfaces:**
- Extends the manager constructor with `clock`, `idleTimeoutMillis`, `sweepIntervalMillis`, `maxAliasesPerFlow`, and `maxAliases` defaults.
- Produces internal `sweepExpired(nowMillis)` for both the production sweeper and deterministic JVM tests.

- [ ] **Step 1: Add failing reuse and isolation tests**

Add tests asserting:

```kotlin
assertEquals(firstAlias, repeatedAlias)
assertNotEquals(firstRemoteAlias.port, secondRemoteAlias.port)
assertNotEquals(firstFlowAlias.port, secondFlowAlias.port)
assertEquals(3, diagnostics.snapshot().activePeerAliases)
```

The calls reuse the same remote in one flow, use two remotes in one flow, and
use the same remote in two different Switch flows.

- [ ] **Step 2: Run and verify RED**

Run the Task 2 focused test command.

Expected: at least the active alias count or flow-scoped reuse assertion fails.

- [ ] **Step 3: Make alias lookup flow-scoped and reusable**

Use `UdpPeerAliasKey(flowKey, remoteEndpoint)` for every lookup, return the
existing socket endpoint when found, update `lastActivityMillis` on traffic in
either direction, and call `diagnostics.setActivePeerAliases(aliases.size)`
after every add or removal.

- [ ] **Step 4: Run and verify GREEN**

Run the Task 2 focused test command.

Expected: all reuse and isolation tests pass.

- [ ] **Step 5: Add a failing concurrent-first-packet test**

Use eight executor tasks released by a `CountDownLatch`, call
`forwardInbound()` with one identical key, collect returned ports, and assert:

```kotlin
assertEquals(1, aliases.toSet().size)
assertEquals(1L, diagnostics.snapshot().peerAliasesCreated)
```

- [ ] **Step 6: Run and verify RED, then serialize creation**

Run the focused command. Expected RED: more than one alias may be allocated or
the created counter is absent. Keep lookup, capacity eviction, bind, map insert,
and receive-thread publication under one manager lock, then rerun to GREEN.

- [ ] **Step 7: Add failing expiry and pressure tests**

Use a mutable fake clock and constructor limits. Assert that a manual
`sweepExpired(now)` removes aliases older than 300,000 ms and increments
`peerAliasesExpired`. With `maxAliasesPerFlow = 1`, create two remote aliases in
one flow and assert only the newer alias remains. With `maxAliases = 2`, create
three aliases across flows and assert the globally oldest alias is evicted.

- [ ] **Step 8: Run and verify RED, then implement lifecycle limits**

Run the focused command. Expected RED: constructor parameters and
`sweepExpired` are missing. Add default constants `300_000L`, `30_000L`, `128`,
and `512`; start one sweeper daemon in `start()`; evict the oldest flow alias
before the globally oldest alias; close every evicted socket; and increment
`peerAliasesExpired` for timeout and pressure eviction. Rerun to GREEN.

- [ ] **Step 9: Add and satisfy the missing-address test**

Construct a second manager with `localAddress = null`, call
`forwardInbound()`, assert that it throws `IllegalStateException` with
`No Switch-reachable local address`, and assert diagnostics contain
`UDP_ALIAS_BIND` while the manager remains running. Implement the guard as:

```kotlin
val bindAddress = localAddress ?: run {
    val message = "No Switch-reachable local address"
    diagnostics.setLastError("UDP_ALIAS_BIND", message)
    throw IllegalStateException(message)
}
```

Run the focused command and verify both tests pass.

- [ ] **Step 10: Add and satisfy flow-close and manager-close tests**

Assert `closeFlow(flowKey)` removes only that flow's aliases and `close()` sets
both active counts to zero. Implement idempotent closure and normal handling of
`SocketException` after shutdown. Run the focused command to GREEN.

- [ ] **Step 11: Checkpoint lifecycle behavior**

Run the focused command and `git diff --check` for the two manager files.

Expected: all manager tests pass and no whitespace errors are reported.

---

### Task 4: Integrate aliases into the existing UDP proxy and VPN service

**Files:**
- Modify: `android/app/src/test/kotlin/dev/switchnat/app/udp/LocalUdpEndpointProxyTest.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/udp/LocalUdpEndpointProxy.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt`

**Interfaces:**
- `LocalUdpEndpointProxy` gains required `localAliasAddress: InetAddress?`.
- Its private `Flow` implements `UdpRelaySender` and serializes sends through one lock.
- `SwitchNatVpnService` passes the detected gateway address when constructing the proxy.

- [ ] **Step 1: Add a failing proxy-level unknown endpoint test**

Add a test based on the existing configured-endpoint round trip. Send one seed
packet to create the flow, enqueue a decoded packet from an unconfigured remote,
receive it at the Switch socket, reply to the observed alias source, and assert
the single fake relay session sends the reply to the unconfigured remote.

- [ ] **Step 2: Run and verify RED**

Run:

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest --tests 'dev.switchnat.app.udp.LocalUdpEndpointProxyTest'
```

Expected: the Switch receive times out because the current proxy records
`UDP_RESPONSE_ENDPOINT` and drops the unknown packet.

- [ ] **Step 3: Wire `UdpPeerAliasManager` into the proxy**

Construct and start the manager with `localAliasAddress`. In
`sendBackToSwitch()`, retain the exact endpoint branch when
`endpointsByTarget[decoded.target]` exists; otherwise call:

```kotlin
aliasManager.forwardInbound(
    flowKey = flow.sender,
    switchEndpoint = flow.sender,
    remoteEndpoint = decoded.target,
    payload = decoded.payload,
    relaySender = flow
)
```

Replace `Flow` with a class implementing `UdpRelaySender` whose `send()` is
synchronized on a private lock. Update flow diagnostics through
`setActiveUdpFlows()` while preserving legacy `setActiveMappings()`. Close flow
aliases whenever a relay receive or send fails. Close the alias manager before
relay sessions during proxy shutdown.

- [ ] **Step 4: Run and verify GREEN**

Run the focused command from Step 2.

Expected: all existing static endpoint tests and the new dynamic round trip pass.

- [ ] **Step 5: Pass the detected LAN address from the VPN service**

Convert a nonblank detected gateway with:

```kotlin
val localAliasAddress = gateway
    .takeIf(String::isNotBlank)
    ?.let(InetAddress::getByName)
```

Pass it as `localAliasAddress` to `LocalUdpEndpointProxy`. A blank gateway is
allowed so static endpoints still start; dynamic alias attempts then report
`UDP_ALIAS_BIND` without stopping the VPN.

- [ ] **Step 6: Run the entire Android unit suite and checkpoint**

Run:

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest
git diff --check -- app/src/main/kotlin/dev/switchnat/app/udp app/src/test/kotlin/dev/switchnat/app/udp app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt
```

Expected: Gradle reports `BUILD SUCCESSFUL`; diff check is silent.

---

### Task 5: Show alias diagnostics on the existing status screen and update technical docs

**Files:**
- Modify: `lib/src/screens/status_screen.dart`
- Modify: `test/widget_test.dart`
- Modify: `docs/TECHNICAL_DESIGN.md`

**Interfaces:**
- Consumes the four `ServiceStatus` properties created in Task 1.
- Keeps current Material 3 styling and navigation unchanged.

- [ ] **Step 1: Add a failing status-screen widget assertion**

Pump a `StatusScreen` with a single running status containing nonzero dynamic
metrics and assert labels and values exist:

```dart
expect(find.text('UDP relay flows'), findsOneWidget);
expect(find.text('P2P aliases'), findsOneWidget);
expect(find.text('Aliases created'), findsOneWidget);
expect(find.text('Aliases expired'), findsOneWidget);
```

- [ ] **Step 2: Run and verify RED**

Run:

```bash
.toolchains/flutter/bin/flutter test test/widget_test.dart
```

Expected: the four labels are absent.

- [ ] **Step 3: Add four rows to the Traffic section**

Use existing `_KeyValueRow` widgets with exact labels from Step 1 and values
from `activeUdpFlows`, `activePeerAliases`, `peerAliasesCreated`, and
`peerAliasesExpired`. Keep `Active mappings` during this phase for compatibility.

- [ ] **Step 4: Run and verify GREEN**

Run the command from Step 2.

Expected: widget test passes.

- [ ] **Step 5: Update the technical design**

Replace the current limitation that all runtime peer endpoints must be
preconfigured. Document that configured endpoints bootstrap the relay, unknown
remote sources receive local LAN aliases, aliases reuse the original relay
flow, and idle mappings expire without rebuilding the VPN.

- [ ] **Step 6: Run all Flutter tests and checkpoint**

Run:

```bash
.toolchains/flutter/bin/flutter test
git diff --check -- lib/src/screens/status_screen.dart test/widget_test.dart docs/TECHNICAL_DESIGN.md
```

Expected: all Flutter tests pass and diff check is silent.

---

### Task 6: Build, install, and perform device-side network verification

**Files:**
- No production file changes unless a failing device behavior first receives a reproducible automated test.

**Interfaces:**
- Produces a debug APK with version code `2001` so it can replace the currently installed debug build without clearing app data.

- [ ] **Step 1: Run fresh full verification**

Run:

```bash
cd android
JAVA_HOME=../.toolchains/jdk17 PATH=../.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest
cd ..
.toolchains/flutter/bin/flutter test
.toolchains/flutter/bin/flutter analyze
```

Expected: Android build successful, Flutter tests all passed, and analyzer has no errors.

- [ ] **Step 2: Build the installable debug APK**

Run:

```bash
flutter build apk --debug --build-number=2001
```

Expected: `build/app/outputs/flutter-apk/app-debug.apk` is produced successfully.

- [ ] **Step 3: Reconnect and install without clearing data**

Use the current wireless ADB endpoint if still available; otherwise request its
new connect port from the user. Run:

```bash
.toolchains/android-sdk/platform-tools/adb connect "$ADB_SERIAL"
.toolchains/android-sdk/platform-tools/adb -s "$ADB_SERIAL" install -r build/app/outputs/flutter-apk/app-debug.apk
```

Expected: ADB reports `connected` and installation reports `Success`.

- [ ] **Step 4: Verify the connection test regression**

Start acceleration, ask the user to run the Switch connection test, and inspect
status/log output. Acceptance: NAT A remains, public IP and speed remain correct,
static UDP read/sent/received/written counters increase, and no alias error is
recorded.

- [ ] **Step 5: Verify Mario Kart 8 dynamic peers**

Reset diagnostics by restarting the app runtime, ask the user to join an online
room, and inspect logs. Acceptance: the former unknown remote produces
`alias-created`, `alias-written`, and `alias-forwarded`; no
`UDP_RESPONSE_ENDPOINT` occurs; the room join succeeds.

- [ ] **Step 6: Verify endpoint churn**

Ask the user to leave and rejoin or test Mario Maker 2. Acceptance: new remote
`IP:port` values allocate new aliases during the same VPN session, old aliases
remain until expiry, and play continues without restarting the VPN.

- [ ] **Step 7: Verify shutdown cleanup**

Stop acceleration and inspect the status stream. Acceptance:
`activeUdpFlows == 0`, `activePeerAliases == 0`, and the foreground VPN service
stops without a socket error.
