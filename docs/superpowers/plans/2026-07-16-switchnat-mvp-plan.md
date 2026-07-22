# SwitchNAT MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Flutter Android MVP that uses Android `VpnService` to relay Switch hotspot UDP through a standard Shadowsocks server and exposes a local HTTP CONNECT proxy for Switch manual TCP proxy settings.

**Architecture:** Flutter owns configuration, controls, and diagnostics. Android Kotlin owns `VpnService`, packet parsing, Shadowsocks TCP/UDP transport, HTTP proxying, and foreground service lifecycle. The packet and Shadowsocks logic is separated into pure Kotlin units with JVM tests so the risky VPN/hotspot behavior can be isolated during device verification.

**Tech Stack:** Flutter, Dart, Kotlin, Android `VpnService`, Kotlin coroutines, Java Cryptography Architecture AEAD ciphers, JUnit 4.

## Global Constraints

- Target first device: Xiaomi 15 on Android 16.
- Minimum Android compatibility: Android 11 / API 30.
- MVP uses a standard Shadowsocks server with UDP relay already enabled.
- MVP supports IPv4 UDP transparent forwarding and HTTP CONNECT TCP proxying.
- MVP does not use root, adb-assisted routing, or privileged system APIs.
- MVP does not transparently intercept TCP.
- MVP does not implement iOS, but keeps Flutter UI and configuration models platform-neutral.
- The app must expose diagnostics for UDP packets read, accepted, sent to Shadowsocks, received from Shadowsocks, written back to TUN, active UDP mappings, TCP proxy connections, and latest error category.
- Android outbound sockets used to reach the Shadowsocks server must call `VpnService.protect(...)`.
- Do not implement cryptographic primitives manually. Use platform AEAD primitives and implement only the Shadowsocks wire format around them.

---

## File Structure

Create this structure from a Flutter Android project:

- `pubspec.yaml`: Flutter dependencies and app metadata.
- `lib/main.dart`: app entry point.
- `lib/src/app.dart`: top-level Material app.
- `lib/src/config/ss_config.dart`: platform-neutral Shadowsocks config model and validation.
- `lib/src/config/config_store.dart`: persistent local config storage.
- `lib/src/native/native_bridge.dart`: MethodChannel/EventChannel API used by Flutter.
- `lib/src/state/service_status.dart`: Dart status and diagnostics models.
- `lib/src/screens/config_screen.dart`: config form and start/stop controls.
- `lib/src/screens/status_screen.dart`: live service status and counters.
- `lib/src/screens/logs_screen.dart`: recent diagnostics events.
- `test/config/ss_config_test.dart`: Dart config tests.
- `test/native/native_bridge_test.dart`: Dart native bridge serialization tests.
- `android/app/src/main/AndroidManifest.xml`: VPN service, permissions, foreground service declaration.
- `android/app/src/main/kotlin/dev/switchnat/app/MainActivity.kt`: Flutter channel registration.
- `android/app/src/main/kotlin/dev/switchnat/app/bridge/NativeBridge.kt`: Android side of MethodChannel/EventChannel.
- `android/app/src/main/kotlin/dev/switchnat/app/config/ShadowsocksConfig.kt`: native config model.
- `android/app/src/main/kotlin/dev/switchnat/app/diagnostics/Diagnostics.kt`: counters, samples, status snapshots.
- `android/app/src/main/kotlin/dev/switchnat/app/hotspot/HotspotAddressDetector.kt`: hotspot gateway candidate detection.
- `android/app/src/main/kotlin/dev/switchnat/app/packet/Checksum.kt`: IPv4/UDP checksum helpers.
- `android/app/src/main/kotlin/dev/switchnat/app/packet/Ipv4Packet.kt`: IPv4 parser/builder.
- `android/app/src/main/kotlin/dev/switchnat/app/packet/UdpPacket.kt`: UDP parser/builder.
- `android/app/src/main/kotlin/dev/switchnat/app/packet/NatTable.kt`: UDP mapping table.
- `android/app/src/main/kotlin/dev/switchnat/app/packet/PacketLoop.kt`: TUN read/dispatch/write loop.
- `android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksAddress.kt`: SS target address encoding.
- `android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksAead.kt`: AEAD method metadata and JCA wrapper.
- `android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksUdpClient.kt`: UDP relay packet codec and network client.
- `android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksTcpClient.kt`: TCP outbound connector.
- `android/app/src/main/kotlin/dev/switchnat/app/proxy/HttpConnectProxy.kt`: local HTTP CONNECT proxy.
- `android/app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt`: foreground `VpnService`.
- `android/app/src/main/kotlin/dev/switchnat/app/vpn/VpnController.kt`: service start/stop commands and notification helpers.
- `android/app/src/test/kotlin/dev/switchnat/app/packet/PacketCoreTest.kt`: packet parser and checksum tests.
- `android/app/src/test/kotlin/dev/switchnat/app/packet/NatTableTest.kt`: mapping timeout tests.
- `android/app/src/test/kotlin/dev/switchnat/app/ss/ShadowsocksAddressTest.kt`: SS address encoding tests.
- `android/app/src/test/kotlin/dev/switchnat/app/ss/ShadowsocksUdpCodecTest.kt`: SS UDP encode/decode tests.
- `android/app/src/test/kotlin/dev/switchnat/app/ss/ShadowsocksTcpCodecTest.kt`: SS TCP stream encode/decode tests.
- `android/app/src/test/kotlin/dev/switchnat/app/proxy/HttpConnectProxyTest.kt`: local CONNECT proxy tests.
- `android/app/src/test/kotlin/dev/switchnat/app/hotspot/HotspotAddressDetectorTest.kt`: gateway candidate selection tests.

## Task 1: Scaffold Flutter Android Project

**Files:**
- Create/modify generated Flutter project files under repository root.
- Modify: `pubspec.yaml`
- Modify: `android/app/build.gradle.kts` or `android/app/build.gradle`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `lib/main.dart`
- Create: `lib/src/app.dart`

**Interfaces:**
- Produces Flutter project package name `dev.switchnat.app`.
- Produces Android min SDK API 30.
- Produces an app that can run `flutter test` and Android JVM unit tests.

- [ ] **Step 1: Generate the Flutter project**

Run:

```bash
flutter create . --org dev.switchnat --project-name switchnat --platforms android
```

Expected:

```text
All done!
```

- [ ] **Step 2: Set Android package and min SDK**

Edit `android/app/build.gradle.kts` if the project uses Kotlin DSL. Ensure this shape is present inside `android { ... }`:

```kotlin
namespace = "dev.switchnat.app"

defaultConfig {
    applicationId = "dev.switchnat.app"
    minSdk = 30
    targetSdk = flutter.targetSdkVersion
    versionCode = flutter.versionCode
    versionName = flutter.versionName
}
```

If the project uses Groovy Gradle, set the equivalent values:

```groovy
namespace "dev.switchnat.app"

defaultConfig {
    applicationId "dev.switchnat.app"
    minSdkVersion 30
    targetSdkVersion flutter.targetSdkVersion
    versionCode flutter.versionCode
    versionName flutter.versionName
}
```

- [ ] **Step 3: Add Android test dependencies**

For Kotlin DSL, add:

```kotlin
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
```

For Groovy Gradle, add:

```groovy
dependencies {
    testImplementation "junit:junit:4.13.2"
    testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1"
}
```

- [ ] **Step 4: Replace the starter Flutter app**

Set `lib/main.dart` to:

```dart
import 'package:flutter/material.dart';
import 'src/app.dart';

void main() {
  runApp(const SwitchNatApp());
}
```

Create `lib/src/app.dart`:

```dart
import 'package:flutter/material.dart';

class SwitchNatApp extends StatelessWidget {
  const SwitchNatApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SwitchNAT',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff246B5A)),
        useMaterial3: true,
      ),
      home: const Scaffold(
        body: Center(child: Text('SwitchNAT')),
      ),
    );
  }
}
```

- [ ] **Step 5: Run Flutter tests**

Run:

```bash
flutter test
```

Expected:

```text
All tests passed!
```

- [ ] **Step 6: Run Android unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

Run:

```bash
git add .
git commit -m "chore: scaffold Flutter Android app"
```

## Task 2: Shared Configuration Model and Native Bridge Contract

**Files:**
- Create: `lib/src/config/ss_config.dart`
- Create: `lib/src/config/config_store.dart`
- Create: `lib/src/native/native_bridge.dart`
- Create: `lib/src/state/service_status.dart`
- Create: `test/config/ss_config_test.dart`
- Create: `test/native/native_bridge_test.dart`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/config/ShadowsocksConfig.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/bridge/NativeBridge.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/MainActivity.kt`

**Interfaces:**
- Produces Dart `SsConfig` with `toJson()`, `fromJson(Map<String, Object?>)`, and `validate()`.
- Produces Dart `NativeBridge.start(SsConfig config)`, `stop()`, `requestVpnPermission()`, and `statusStream`.
- Produces Kotlin `ShadowsocksConfig.fromMap(map: Map<String, Any?>): ShadowsocksConfig`.
- Produces channel names:
  - MethodChannel: `dev.switchnat.app/control`
  - EventChannel: `dev.switchnat.app/status`

- [ ] **Step 1: Write failing Dart config tests**

Create `test/config/ss_config_test.dart`:

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/ss_config.dart';

void main() {
  test('valid config serializes and deserializes', () {
    const config = SsConfig(
      serverHost: '203.0.113.10',
      serverPort: 8388,
      password: 'secret',
      method: 'aes-128-gcm',
      httpProxyPort: 7890,
    );

    final restored = SsConfig.fromJson(config.toJson());

    expect(restored, config);
    expect(restored.validate(), isEmpty);
  });

  test('invalid config returns exact validation messages', () {
    const config = SsConfig(
      serverHost: '',
      serverPort: 0,
      password: '',
      method: 'rc4-md5',
      httpProxyPort: 70000,
    );

    expect(config.validate(), <String>[
      'Server host is required',
      'Server port must be between 1 and 65535',
      'Password is required',
      'Unsupported method: rc4-md5',
      'HTTP proxy port must be between 1024 and 65535',
    ]);
  });
}
```

- [ ] **Step 2: Implement Dart config**

Create `lib/src/config/ss_config.dart`:

```dart
class SsConfig {
  static const supportedMethods = <String>{
    'aes-128-gcm',
    'aes-256-gcm',
    'chacha20-ietf-poly1305',
  };

  const SsConfig({
    required this.serverHost,
    required this.serverPort,
    required this.password,
    required this.method,
    required this.httpProxyPort,
  });

  final String serverHost;
  final int serverPort;
  final String password;
  final String method;
  final int httpProxyPort;

  Map<String, Object?> toJson() => <String, Object?>{
        'serverHost': serverHost,
        'serverPort': serverPort,
        'password': password,
        'method': method,
        'httpProxyPort': httpProxyPort,
      };

  factory SsConfig.fromJson(Map<String, Object?> json) {
    return SsConfig(
      serverHost: json['serverHost'] as String? ?? '',
      serverPort: json['serverPort'] as int? ?? 0,
      password: json['password'] as String? ?? '',
      method: json['method'] as String? ?? 'aes-128-gcm',
      httpProxyPort: json['httpProxyPort'] as int? ?? 7890,
    );
  }

  List<String> validate() {
    final errors = <String>[];
    if (serverHost.trim().isEmpty) errors.add('Server host is required');
    if (serverPort < 1 || serverPort > 65535) {
      errors.add('Server port must be between 1 and 65535');
    }
    if (password.isEmpty) errors.add('Password is required');
    if (!supportedMethods.contains(method)) {
      errors.add('Unsupported method: $method');
    }
    if (httpProxyPort < 1024 || httpProxyPort > 65535) {
      errors.add('HTTP proxy port must be between 1024 and 65535');
    }
    return errors;
  }

  @override
  bool operator ==(Object other) {
    return other is SsConfig &&
        other.serverHost == serverHost &&
        other.serverPort == serverPort &&
        other.password == password &&
        other.method == method &&
        other.httpProxyPort == httpProxyPort;
  }

  @override
  int get hashCode => Object.hash(
        serverHost,
        serverPort,
        password,
        method,
        httpProxyPort,
      );
}
```

- [ ] **Step 3: Write failing native bridge serialization test**

Create `test/native/native_bridge_test.dart`:

```dart
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/ss_config.dart';
import 'package:switchnat/src/native/native_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('start sends config over control channel', () async {
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(NativeBridge.controlChannel, (call) async {
      calls.add(call);
      return true;
    });

    final bridge = NativeBridge();
    await bridge.start(const SsConfig(
      serverHost: '203.0.113.10',
      serverPort: 8388,
      password: 'secret',
      method: 'aes-128-gcm',
      httpProxyPort: 7890,
    ));

    expect(calls.single.method, 'start');
    expect(calls.single.arguments, <String, Object?>{
      'serverHost': '203.0.113.10',
      'serverPort': 8388,
      'password': 'secret',
      'method': 'aes-128-gcm',
      'httpProxyPort': 7890,
    });
  });
}
```

- [ ] **Step 4: Implement native bridge and status model**

Create `lib/src/state/service_status.dart`:

```dart
enum VpnRunState { stopped, permissionNeeded, starting, running, stopping, failed }

class ServiceStatus {
  const ServiceStatus({
    required this.state,
    required this.hotspotGateway,
    required this.switchProxyAddress,
    required this.learnedSwitchIp,
    required this.udpRead,
    required this.udpAccepted,
    required this.udpSent,
    required this.udpReceived,
    required this.udpWritten,
    required this.activeMappings,
    required this.tcpConnections,
    required this.lastError,
  });

  final VpnRunState state;
  final String hotspotGateway;
  final String switchProxyAddress;
  final String learnedSwitchIp;
  final int udpRead;
  final int udpAccepted;
  final int udpSent;
  final int udpReceived;
  final int udpWritten;
  final int activeMappings;
  final int tcpConnections;
  final String lastError;

  factory ServiceStatus.fromJson(Map<Object?, Object?> json) {
    final stateName = json['state'] as String? ?? 'stopped';
    return ServiceStatus(
      state: VpnRunState.values.firstWhere(
        (value) => value.name == stateName,
        orElse: () => VpnRunState.stopped,
      ),
      hotspotGateway: json['hotspotGateway'] as String? ?? '',
      switchProxyAddress: json['switchProxyAddress'] as String? ?? '',
      learnedSwitchIp: json['learnedSwitchIp'] as String? ?? '',
      udpRead: json['udpRead'] as int? ?? 0,
      udpAccepted: json['udpAccepted'] as int? ?? 0,
      udpSent: json['udpSent'] as int? ?? 0,
      udpReceived: json['udpReceived'] as int? ?? 0,
      udpWritten: json['udpWritten'] as int? ?? 0,
      activeMappings: json['activeMappings'] as int? ?? 0,
      tcpConnections: json['tcpConnections'] as int? ?? 0,
      lastError: json['lastError'] as String? ?? '',
    );
  }
}
```

Create `lib/src/native/native_bridge.dart`:

```dart
import 'package:flutter/services.dart';
import '../config/ss_config.dart';
import '../state/service_status.dart';

class NativeBridge {
  static const controlChannel = MethodChannel('dev.switchnat.app/control');
  static const statusChannel = EventChannel('dev.switchnat.app/status');

  Future<bool> requestVpnPermission() async {
    return await controlChannel.invokeMethod<bool>('requestVpnPermission') ?? false;
  }

  Future<bool> start(SsConfig config) async {
    final errors = config.validate();
    if (errors.isNotEmpty) {
      throw ArgumentError(errors.join('; '));
    }
    return await controlChannel.invokeMethod<bool>('start', config.toJson()) ?? false;
  }

  Future<bool> stop() async {
    return await controlChannel.invokeMethod<bool>('stop') ?? false;
  }

  Stream<ServiceStatus> get statusStream {
    return statusChannel.receiveBroadcastStream().map((event) {
      return ServiceStatus.fromJson(event as Map<Object?, Object?>);
    });
  }
}
```

- [ ] **Step 5: Implement native Kotlin config and channel shell**

Create `android/app/src/main/kotlin/dev/switchnat/app/config/ShadowsocksConfig.kt`:

```kotlin
package dev.switchnat.app.config

data class ShadowsocksConfig(
    val serverHost: String,
    val serverPort: Int,
    val password: String,
    val method: String,
    val httpProxyPort: Int
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (serverHost.isBlank()) errors += "Server host is required"
        if (serverPort !in 1..65535) errors += "Server port must be between 1 and 65535"
        if (password.isEmpty()) errors += "Password is required"
        if (method !in SUPPORTED_METHODS) errors += "Unsupported method: $method"
        if (httpProxyPort !in 1024..65535) {
            errors += "HTTP proxy port must be between 1024 and 65535"
        }
        return errors
    }

    companion object {
        val SUPPORTED_METHODS = setOf("aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305")

        fun fromMap(map: Map<String, Any?>): ShadowsocksConfig {
            return ShadowsocksConfig(
                serverHost = map["serverHost"] as? String ?: "",
                serverPort = map["serverPort"] as? Int ?: 0,
                password = map["password"] as? String ?: "",
                method = map["method"] as? String ?: "aes-128-gcm",
                httpProxyPort = map["httpProxyPort"] as? Int ?: 7890
            )
        }
    }
}
```

Create `android/app/src/main/kotlin/dev/switchnat/app/bridge/NativeBridge.kt`:

```kotlin
package dev.switchnat.app.bridge

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import dev.switchnat.app.config.ShadowsocksConfig
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class NativeBridge(private val activity: Activity) : MethodChannel.MethodCallHandler,
    EventChannel.StreamHandler {

    private var eventSink: EventChannel.EventSink? = null

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "requestVpnPermission" -> {
                val intent = VpnService.prepare(activity)
                if (intent != null) {
                    activity.startActivity(intent)
                    result.success(false)
                } else {
                    result.success(true)
                }
            }
            "start" -> {
                val args = call.arguments as? Map<String, Any?>
                if (args == null) {
                    result.error("BAD_ARGS", "start requires config map", null)
                    return
                }
                val config = ShadowsocksConfig.fromMap(args)
                val errors = config.validate()
                if (errors.isNotEmpty()) {
                    result.error("BAD_CONFIG", errors.joinToString("; "), null)
                    return
                }
                result.success(true)
            }
            "stop" -> result.success(true)
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        eventSink?.success(mapOf("state" to "stopped"))
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }
}
```

Modify `android/app/src/main/kotlin/dev/switchnat/app/MainActivity.kt`:

```kotlin
package dev.switchnat.app

import dev.switchnat.app.bridge.NativeBridge
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val bridge = NativeBridge(this)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "dev.switchnat.app/control"
        ).setMethodCallHandler(bridge)
        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "dev.switchnat.app/status"
        ).setStreamHandler(bridge)
    }
}
```

- [ ] **Step 6: Run tests**

Run:

```bash
flutter test test/config/ss_config_test.dart test/native/native_bridge_test.dart
```

Expected:

```text
All tests passed!
```

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

Run:

```bash
git add lib test android/app/src/main/kotlin
git commit -m "feat: add shared config and native bridge contract"
```

## Task 3: Diagnostics Model and Status Streaming

**Files:**
- Create: `android/app/src/main/kotlin/dev/switchnat/app/diagnostics/Diagnostics.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/diagnostics/DiagnosticsTest.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/bridge/NativeBridge.kt`
- Modify: `lib/src/state/service_status.dart`

**Interfaces:**
- Produces Kotlin `DiagnosticsSnapshot.toMap(): Map<String, Any>`.
- Produces Kotlin `object DiagnosticsHub { val diagnostics: Diagnostics }` for service/UI shared status.
- Produces Kotlin `Diagnostics.incrementUdpRead()`, `incrementUdpAccepted()`, `incrementUdpSent()`, `incrementUdpReceived()`, `incrementUdpWritten()`, `setActiveMappings(count: Int)`, `incrementTcpConnections()`, `setLastError(category: String, message: String)`.
- Consumes Dart `ServiceStatus.fromJson`.

- [ ] **Step 1: Write failing diagnostics test**

Create `android/app/src/test/kotlin/dev/switchnat/app/diagnostics/DiagnosticsTest.kt`:

```kotlin
package dev.switchnat.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun snapshotContainsExactCountersAndError() {
        val diagnostics = Diagnostics()
        diagnostics.setState(VpnRunState.RUNNING)
        diagnostics.setHotspotGateway("192.168.43.1", 7890)
        diagnostics.setLearnedSwitchIp("192.168.43.22")
        diagnostics.incrementUdpRead()
        diagnostics.incrementUdpAccepted()
        diagnostics.incrementUdpSent()
        diagnostics.incrementUdpReceived()
        diagnostics.incrementUdpWritten()
        diagnostics.setActiveMappings(3)
        diagnostics.incrementTcpConnections()
        diagnostics.setLastError("SS_UDP_NO_RESPONSE", "No response for 10 seconds")

        val map = diagnostics.snapshot().toMap()

        assertEquals("running", map["state"])
        assertEquals("192.168.43.1", map["hotspotGateway"])
        assertEquals("192.168.43.1:7890", map["switchProxyAddress"])
        assertEquals("192.168.43.22", map["learnedSwitchIp"])
        assertEquals(1, map["udpRead"])
        assertEquals(1, map["udpAccepted"])
        assertEquals(1, map["udpSent"])
        assertEquals(1, map["udpReceived"])
        assertEquals(1, map["udpWritten"])
        assertEquals(3, map["activeMappings"])
        assertEquals(1, map["tcpConnections"])
        assertEquals("SS_UDP_NO_RESPONSE: No response for 10 seconds", map["lastError"])
    }
}
```

- [ ] **Step 2: Implement diagnostics**

Create `android/app/src/main/kotlin/dev/switchnat/app/diagnostics/Diagnostics.kt`:

```kotlin
package dev.switchnat.app.diagnostics

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class VpnRunState(val wireName: String) {
    STOPPED("stopped"),
    PERMISSION_NEEDED("permissionNeeded"),
    STARTING("starting"),
    RUNNING("running"),
    STOPPING("stopping"),
    FAILED("failed")
}

data class PacketSample(
    val timestampMillis: Long,
    val protocol: String,
    val source: String,
    val destination: String,
    val direction: String,
    val outcome: String
) {
    fun toMap(): Map<String, Any> = mapOf(
        "timestampMillis" to timestampMillis,
        "protocol" to protocol,
        "source" to source,
        "destination" to destination,
        "direction" to direction,
        "outcome" to outcome
    )
}

data class DiagnosticsSnapshot(
    val state: VpnRunState,
    val hotspotGateway: String,
    val switchProxyAddress: String,
    val learnedSwitchIp: String,
    val udpRead: Long,
    val udpAccepted: Long,
    val udpSent: Long,
    val udpReceived: Long,
    val udpWritten: Long,
    val activeMappings: Int,
    val tcpConnections: Long,
    val lastError: String,
    val samples: List<PacketSample>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "state" to state.wireName,
        "hotspotGateway" to hotspotGateway,
        "switchProxyAddress" to switchProxyAddress,
        "learnedSwitchIp" to learnedSwitchIp,
        "udpRead" to udpRead,
        "udpAccepted" to udpAccepted,
        "udpSent" to udpSent,
        "udpReceived" to udpReceived,
        "udpWritten" to udpWritten,
        "activeMappings" to activeMappings,
        "tcpConnections" to tcpConnections,
        "lastError" to lastError,
        "samples" to samples.map { it.toMap() }
    )
}

class Diagnostics {
    private var state: VpnRunState = VpnRunState.STOPPED
    private var hotspotGateway: String = ""
    private var proxyPort: Int = 0
    private var learnedSwitchIp: String = ""
    private var lastError: String = ""
    private val udpRead = AtomicLong(0)
    private val udpAccepted = AtomicLong(0)
    private val udpSent = AtomicLong(0)
    private val udpReceived = AtomicLong(0)
    private val udpWritten = AtomicLong(0)
    private val activeMappings = AtomicInteger(0)
    private val tcpConnections = AtomicLong(0)
    private val samples = CopyOnWriteArrayList<PacketSample>()

    @Synchronized fun setState(value: VpnRunState) {
        state = value
    }

    @Synchronized fun setHotspotGateway(gateway: String, port: Int) {
        hotspotGateway = gateway
        proxyPort = port
    }

    @Synchronized fun setLearnedSwitchIp(ip: String) {
        learnedSwitchIp = ip
    }

    fun incrementUdpRead() = udpRead.incrementAndGet()
    fun incrementUdpAccepted() = udpAccepted.incrementAndGet()
    fun incrementUdpSent() = udpSent.incrementAndGet()
    fun incrementUdpReceived() = udpReceived.incrementAndGet()
    fun incrementUdpWritten() = udpWritten.incrementAndGet()
    fun incrementTcpConnections() = tcpConnections.incrementAndGet()
    fun setActiveMappings(count: Int) = activeMappings.set(count)

    @Synchronized fun setLastError(category: String, message: String) {
        lastError = "$category: $message"
    }

    fun addSample(sample: PacketSample) {
        samples += sample
        while (samples.size > 50) {
            samples.removeAt(0)
        }
    }

    @Synchronized fun snapshot(): DiagnosticsSnapshot {
        val proxyAddress = if (hotspotGateway.isBlank() || proxyPort == 0) {
            ""
        } else {
            "$hotspotGateway:$proxyPort"
        }
        return DiagnosticsSnapshot(
            state = state,
            hotspotGateway = hotspotGateway,
            switchProxyAddress = proxyAddress,
            learnedSwitchIp = learnedSwitchIp,
            udpRead = udpRead.get(),
            udpAccepted = udpAccepted.get(),
            udpSent = udpSent.get(),
            udpReceived = udpReceived.get(),
            udpWritten = udpWritten.get(),
            activeMappings = activeMappings.get(),
            tcpConnections = tcpConnections.get(),
            lastError = lastError,
            samples = samples.toList()
        )
    }
}

object DiagnosticsHub {
    val diagnostics = Diagnostics()
}
```

- [ ] **Step 3: Stream diagnostics through NativeBridge**

Modify `NativeBridge` to accept a `Diagnostics` instance and publish snapshots:

```kotlin
class NativeBridge(
    private val activity: Activity,
    private val diagnostics: Diagnostics = DiagnosticsHub.diagnostics
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private var eventSink: EventChannel.EventSink? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            emitStatus()
            handler.postDelayed(this, 1_000L)
        }
    }

    private fun emitStatus() {
        eventSink?.success(diagnostics.snapshot().toMap())
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        emitStatus()
        handler.post(ticker)
    }

    override fun onCancel(arguments: Any?) {
        handler.removeCallbacks(ticker)
        eventSink = null
    }
}
```

Keep the existing `onMethodCall` branches and call `emitStatus()` after successful `start` and `stop`.

- [ ] **Step 4: Run tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests dev.switchnat.app.diagnostics.DiagnosticsTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

Run:

```bash
git add android/app/src/main/kotlin android/app/src/test/kotlin lib/src/state
git commit -m "feat: add diagnostics status model"
```

## Task 4: Packet Parser, Checksum, and NAT Table

**Files:**
- Create: `android/app/src/main/kotlin/dev/switchnat/app/packet/Checksum.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/packet/Ipv4Packet.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/packet/UdpPacket.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/packet/NatTable.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/packet/PacketCoreTest.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/packet/NatTableTest.kt`

**Interfaces:**
- Produces `Ipv4Packet.parse(bytes: ByteArray): Ipv4Packet?`.
- Produces `Ipv4Packet.buildUdp(sourceIp: String, destinationIp: String, sourcePort: Int, destinationPort: Int, payload: ByteArray): ByteArray`.
- Produces `UdpPacket(sourcePort: Int, destinationPort: Int, payload: ByteArray)`.
- Produces `NatTable.remember(switchIp: String, switchPort: Int, remoteIp: String, remotePort: Int): NatMapping`.
- Produces `NatTable.findByRemote(remoteIp: String, remotePort: Int): NatMapping?`.

- [ ] **Step 1: Write failing packet tests**

Create `android/app/src/test/kotlin/dev/switchnat/app/packet/PacketCoreTest.kt`:

```kotlin
package dev.switchnat.app.packet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PacketCoreTest {
    @Test
    fun buildsAndParsesIpv4UdpPacket() {
        val payload = byteArrayOf(1, 2, 3, 4)

        val packetBytes = Ipv4Packet.buildUdp(
            sourceIp = "192.168.43.22",
            destinationIp = "8.8.8.8",
            sourcePort = 54321,
            destinationPort = 1234,
            payload = payload
        )

        val packet = Ipv4Packet.parse(packetBytes)

        assertNotNull(packet)
        requireNotNull(packet)
        assertEquals("192.168.43.22", packet.sourceIp)
        assertEquals("8.8.8.8", packet.destinationIp)
        assertEquals(17, packet.protocol)
        assertEquals(54321, packet.udp?.sourcePort)
        assertEquals(1234, packet.udp?.destinationPort)
        assertArrayEquals(payload, packet.udp?.payload)
        assertEquals(0, Checksum.ipv4HeaderChecksum(packetBytes.copyOfRange(0, 20)))
    }

    @Test
    fun ignoresNonIpv4AndNonUdpPackets() {
        assertEquals(null, Ipv4Packet.parse(byteArrayOf(0x60, 0, 0, 0)))

        val tcpLike = Ipv4Packet.buildForTest(
            sourceIp = "192.168.43.22",
            destinationIp = "8.8.8.8",
            protocol = 6,
            payload = byteArrayOf(0, 1, 2, 3)
        )

        val parsed = Ipv4Packet.parse(tcpLike)
        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(null, parsed.udp)
    }
}
```

Create `android/app/src/test/kotlin/dev/switchnat/app/packet/NatTableTest.kt`:

```kotlin
package dev.switchnat.app.packet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NatTableTest {
    @Test
    fun remembersAndExpiresMappings() {
        var now = 1_000L
        val table = NatTable(timeoutMillis = 30_000L, clock = { now })

        table.remember(
            switchIp = "192.168.43.22",
            switchPort = 54321,
            remoteIp = "8.8.8.8",
            remotePort = 1234
        )

        val mapping = table.findByRemote("8.8.8.8", 1234)
        requireNotNull(mapping)
        assertEquals("192.168.43.22", mapping.switchIp)
        assertEquals(54321, mapping.switchPort)
        assertEquals(1, table.size())

        now = 32_000L
        table.prune()

        assertNull(table.findByRemote("8.8.8.8", 1234))
        assertEquals(0, table.size())
    }
}
```

- [ ] **Step 2: Implement packet classes**

Create focused Kotlin files with these exact public declarations:

```kotlin
package dev.switchnat.app.packet

object Checksum {
    fun ipv4HeaderChecksum(header: ByteArray): Int
    fun udpChecksum(sourceIp: String, destinationIp: String, udpDatagram: ByteArray): Int
}

data class UdpPacket(
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray
)

data class Ipv4Packet(
    val sourceIp: String,
    val destinationIp: String,
    val protocol: Int,
    val payload: ByteArray,
    val udp: UdpPacket?
) {
    companion object {
        fun parse(bytes: ByteArray): Ipv4Packet?
        fun buildUdp(
            sourceIp: String,
            destinationIp: String,
            sourcePort: Int,
            destinationPort: Int,
            payload: ByteArray
        ): ByteArray
        fun buildForTest(
            sourceIp: String,
            destinationIp: String,
            protocol: Int,
            payload: ByteArray
        ): ByteArray
    }
}
```

Implementation requirements:

- Reject packets shorter than 20 bytes.
- Accept only IPv4 version `4`.
- Respect IHL when extracting payload.
- UDP header length is 8 bytes.
- Recompute IPv4 header checksum when building packets.
- Recompute UDP checksum when building packets.
- Convert IP strings with `InetAddress.getByName(ip).address`.

- [ ] **Step 3: Implement NAT table**

Create `NatTable.kt`:

```kotlin
package dev.switchnat.app.packet

data class NatMapping(
    val switchIp: String,
    val switchPort: Int,
    val remoteIp: String,
    val remotePort: Int,
    val lastSeenMillis: Long
)

class NatTable(
    private val timeoutMillis: Long = 60_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val byRemote = LinkedHashMap<String, NatMapping>()

    @Synchronized
    fun remember(
        switchIp: String,
        switchPort: Int,
        remoteIp: String,
        remotePort: Int
    ): NatMapping {
        val mapping = NatMapping(
            switchIp = switchIp,
            switchPort = switchPort,
            remoteIp = remoteIp,
            remotePort = remotePort,
            lastSeenMillis = clock()
        )
        byRemote[remoteKey(remoteIp, remotePort)] = mapping
        return mapping
    }

    @Synchronized
    fun findByRemote(remoteIp: String, remotePort: Int): NatMapping? {
        prune()
        return byRemote[remoteKey(remoteIp, remotePort)]
    }

    @Synchronized
    fun prune() {
        val now = clock()
        val iterator = byRemote.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeenMillis > timeoutMillis) {
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun size(): Int {
        prune()
        return byRemote.size
    }

    private fun remoteKey(remoteIp: String, remotePort: Int): String = "$remoteIp:$remotePort"
}
```

- [ ] **Step 4: Run packet tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests dev.switchnat.app.packet.PacketCoreTest --tests dev.switchnat.app.packet.NatTableTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

Run:

```bash
git add android/app/src/main/kotlin/dev/switchnat/app/packet android/app/src/test/kotlin/dev/switchnat/app/packet
git commit -m "feat: add IPv4 UDP packet core"
```

## Task 5: Shadowsocks Address and UDP AEAD Codec

**Files:**
- Create: `android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksAddress.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksAead.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksUdpClient.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/ss/ShadowsocksAddressTest.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/ss/ShadowsocksUdpCodecTest.kt`

**Interfaces:**
- Produces `TargetAddress(host: String, port: Int)`.
- Produces `ShadowsocksAddressCodec.encode(target: TargetAddress): ByteArray`.
- Produces `ShadowsocksAddressCodec.decode(bytes: ByteArray, offset: Int = 0): DecodedTargetAddress`.
- Produces `ShadowsocksUdpCodec.encryptPacket(target: TargetAddress, payload: ByteArray): ByteArray`.
- Produces `ShadowsocksUdpCodec.decryptPacket(packet: ByteArray): DecodedUdpPacket`.

- [ ] **Step 1: Write failing address codec test**

Create `android/app/src/test/kotlin/dev/switchnat/app/ss/ShadowsocksAddressTest.kt`:

```kotlin
package dev.switchnat.app.ss

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ShadowsocksAddressTest {
    @Test
    fun encodesIpv4Address() {
        val encoded = ShadowsocksAddressCodec.encode(TargetAddress("1.2.3.4", 53))

        assertArrayEquals(
            byteArrayOf(0x01, 0x01, 0x02, 0x03, 0x04, 0x00, 0x35),
            encoded
        )
    }

    @Test
    fun encodesDomainAddress() {
        val encoded = ShadowsocksAddressCodec.encode(TargetAddress("example.com", 443))

        assertArrayEquals(
            byteArrayOf(
                0x03, 0x0b,
                0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2e, 0x63, 0x6f, 0x6d,
                0x01, 0xbb.toByte()
            ),
            encoded
        )
    }

    @Test
    fun decodesIpv4AddressAndReturnsNextOffset() {
        val decoded = ShadowsocksAddressCodec.decode(
            byteArrayOf(0x01, 0x01, 0x02, 0x03, 0x04, 0x00, 0x35, 0x7f)
        )

        assertEquals(TargetAddress("1.2.3.4", 53), decoded.target)
        assertEquals(7, decoded.nextOffset)
    }
}
```

- [ ] **Step 2: Write failing UDP codec roundtrip test**

Create `android/app/src/test/kotlin/dev/switchnat/app/ss/ShadowsocksUdpCodecTest.kt`:

```kotlin
package dev.switchnat.app.ss

import dev.switchnat.app.config.ShadowsocksConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ShadowsocksUdpCodecTest {
    @Test
    fun encryptsAndDecryptsUdpPacketWithFixedSalt() {
        val codec = ShadowsocksUdpCodec(
            config = ShadowsocksConfig(
                serverHost = "127.0.0.1",
                serverPort = 8388,
                password = "secret",
                method = "aes-128-gcm",
                httpProxyPort = 7890
            ),
            saltGenerator = FixedSaltGenerator(ByteArray(16) { it.toByte() })
        )

        val encrypted = codec.encryptPacket(
            target = TargetAddress("8.8.8.8", 53),
            payload = byteArrayOf(1, 2, 3, 4)
        )

        val decoded = codec.decryptPacket(encrypted)

        assertEquals(TargetAddress("8.8.8.8", 53), decoded.target)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), decoded.payload)
    }
}
```

- [ ] **Step 3: Implement Shadowsocks address codec**

Create `ShadowsocksAddress.kt` with:

```kotlin
package dev.switchnat.app.ss

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

data class TargetAddress(val host: String, val port: Int)
data class DecodedTargetAddress(val target: TargetAddress, val nextOffset: Int)

object ShadowsocksAddressCodec {
    fun encode(target: TargetAddress): ByteArray {
        require(target.port in 1..65535) { "Port must be between 1 and 65535" }
        val portBytes = byteArrayOf(
            ((target.port ushr 8) and 0xff).toByte(),
            (target.port and 0xff).toByte()
        )
        val parsed = runCatching { InetAddress.getByName(target.host) }.getOrNull()
        return when (parsed) {
            is Inet4Address -> byteArrayOf(0x01) + parsed.address + portBytes
            is Inet6Address -> byteArrayOf(0x04) + parsed.address + portBytes
            else -> {
                val hostBytes = target.host.toByteArray(Charsets.UTF_8)
                require(hostBytes.size <= 255) { "Domain is too long" }
                byteArrayOf(0x03, hostBytes.size.toByte()) + hostBytes + portBytes
            }
        }
    }

    fun decode(bytes: ByteArray, offset: Int = 0): DecodedTargetAddress {
        require(offset < bytes.size) { "Missing address type" }
        return when (bytes[offset].toInt() and 0xff) {
            0x01 -> {
                require(bytes.size >= offset + 7) { "Short IPv4 address" }
                val host = bytes.copyOfRange(offset + 1, offset + 5)
                    .joinToString(".") { (it.toInt() and 0xff).toString() }
                val port = readPort(bytes, offset + 5)
                DecodedTargetAddress(TargetAddress(host, port), offset + 7)
            }
            0x03 -> {
                require(bytes.size >= offset + 2) { "Short domain length" }
                val length = bytes[offset + 1].toInt() and 0xff
                require(bytes.size >= offset + 2 + length + 2) { "Short domain address" }
                val host = bytes.copyOfRange(offset + 2, offset + 2 + length).toString(Charsets.UTF_8)
                val port = readPort(bytes, offset + 2 + length)
                DecodedTargetAddress(TargetAddress(host, port), offset + 2 + length + 2)
            }
            0x04 -> {
                require(bytes.size >= offset + 19) { "Short IPv6 address" }
                val host = InetAddress.getByAddress(bytes.copyOfRange(offset + 1, offset + 17)).hostAddress
                val port = readPort(bytes, offset + 17)
                DecodedTargetAddress(TargetAddress(host, port), offset + 19)
            }
            else -> error("Unsupported address type: ${bytes[offset].toInt() and 0xff}")
        }
    }

    private fun readPort(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
    }
}
```

- [ ] **Step 4: Implement Shadowsocks AEAD UDP codec**

Create `ShadowsocksAead.kt` with:

```kotlin
package dev.switchnat.app.ss

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class AeadMethod(val wireName: String, val keySize: Int, val saltSize: Int, val tagSize: Int) {
    AES_128_GCM("aes-128-gcm", 16, 16, 16),
    AES_256_GCM("aes-256-gcm", 32, 32, 16),
    CHACHA20_IETF_POLY1305("chacha20-ietf-poly1305", 32, 32, 16);

    companion object {
        fun fromWireName(name: String): AeadMethod = values().first { it.wireName == name }
    }
}

object ShadowsocksKeys {
    fun evpBytesToKey(password: String, keySize: Int): ByteArray {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("MD5")
        val result = ByteArray(keySize)
        var generated = 0
        var previous = ByteArray(0)
        while (generated < keySize) {
            digest.reset()
            digest.update(previous)
            digest.update(passwordBytes)
            previous = digest.digest()
            val copy = minOf(previous.size, keySize - generated)
            System.arraycopy(previous, 0, result, generated, copy)
            generated += copy
        }
        return result
    }

    fun hkdfSha1(masterKey: ByteArray, salt: ByteArray, length: Int): ByteArray {
        val prkMac = Mac.getInstance("HmacSHA1")
        prkMac.init(SecretKeySpec(salt, "HmacSHA1"))
        val prk = prkMac.doFinal(masterKey)
        val okmMac = Mac.getInstance("HmacSHA1")
        okmMac.init(SecretKeySpec(prk, "HmacSHA1"))
        okmMac.update("ss-subkey".toByteArray(Charsets.UTF_8))
        okmMac.update(1)
        return okmMac.doFinal().copyOf(length)
    }
}

class AeadCipher(private val method: AeadMethod, private val subkey: ByteArray) {
    fun encryptOnce(plain: ByteArray): ByteArray {
        return encrypt(plain, ByteArray(12))
    }

    fun decryptOnce(cipherText: ByteArray): ByteArray {
        return decrypt(cipherText, ByteArray(12))
    }

    fun encrypt(plain: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(cipherName())
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(subkey, keyAlgorithm()), parameterSpec(nonce))
        return cipher.doFinal(plain)
    }

    fun decrypt(cipherText: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(cipherName())
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(subkey, keyAlgorithm()), parameterSpec(nonce))
        return cipher.doFinal(cipherText)
    }

    private fun cipherName(): String = when (method) {
        AeadMethod.AES_128_GCM, AeadMethod.AES_256_GCM -> "AES/GCM/NoPadding"
        AeadMethod.CHACHA20_IETF_POLY1305 -> "ChaCha20-Poly1305"
    }

    private fun keyAlgorithm(): String = when (method) {
        AeadMethod.AES_128_GCM, AeadMethod.AES_256_GCM -> "AES"
        AeadMethod.CHACHA20_IETF_POLY1305 -> "ChaCha20"
    }

    private fun parameterSpec(nonce: ByteArray): java.security.spec.AlgorithmParameterSpec {
        require(nonce.size == 12) { "AEAD nonce must be 12 bytes" }
        return when (method) {
            AeadMethod.AES_128_GCM, AeadMethod.AES_256_GCM -> GCMParameterSpec(128, nonce)
            AeadMethod.CHACHA20_IETF_POLY1305 -> javax.crypto.spec.IvParameterSpec(nonce)
        }
    }
}

class NonceCounter(private val size: Int = 12) {
    private val nonce = ByteArray(size)

    fun next(): ByteArray {
        val current = nonce.copyOf()
        increment()
        return current
    }

    private fun increment() {
        for (index in nonce.indices) {
            val value = (nonce[index].toInt() and 0xff) + 1
            nonce[index] = value.toByte()
            if (value <= 0xff) return
        }
    }
}
```

Create `ShadowsocksUdpClient.kt` codec portion:

```kotlin
package dev.switchnat.app.ss

import dev.switchnat.app.config.ShadowsocksConfig
import java.security.SecureRandom

interface SaltGenerator {
    fun nextSalt(size: Int): ByteArray
}

class SecureSaltGenerator : SaltGenerator {
    private val random = SecureRandom()
    override fun nextSalt(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }
}

class FixedSaltGenerator(private val salt: ByteArray) : SaltGenerator {
    override fun nextSalt(size: Int): ByteArray {
        require(salt.size == size) { "Fixed salt size ${salt.size} did not match $size" }
        return salt.copyOf()
    }
}

data class DecodedUdpPacket(val target: TargetAddress, val payload: ByteArray)

class ShadowsocksUdpCodec(
    private val config: ShadowsocksConfig,
    private val saltGenerator: SaltGenerator = SecureSaltGenerator()
) {
    private val method = AeadMethod.fromWireName(config.method)
    private val masterKey = ShadowsocksKeys.evpBytesToKey(config.password, method.keySize)

    fun encryptPacket(target: TargetAddress, payload: ByteArray): ByteArray {
        val salt = saltGenerator.nextSalt(method.saltSize)
        val subkey = ShadowsocksKeys.hkdfSha1(masterKey, salt, method.keySize)
        val plain = ShadowsocksAddressCodec.encode(target) + payload
        return salt + AeadCipher(method, subkey).encryptOnce(plain)
    }

    fun decryptPacket(packet: ByteArray): DecodedUdpPacket {
        require(packet.size > method.saltSize + method.tagSize) { "Short Shadowsocks UDP packet" }
        val salt = packet.copyOfRange(0, method.saltSize)
        val encrypted = packet.copyOfRange(method.saltSize, packet.size)
        val subkey = ShadowsocksKeys.hkdfSha1(masterKey, salt, method.keySize)
        val plain = AeadCipher(method, subkey).decryptOnce(encrypted)
        val decodedAddress = ShadowsocksAddressCodec.decode(plain)
        return DecodedUdpPacket(
            target = decodedAddress.target,
            payload = plain.copyOfRange(decodedAddress.nextOffset, plain.size)
        )
    }
}
```

- [ ] **Step 5: Run SS codec tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests dev.switchnat.app.ss.ShadowsocksAddressTest --tests dev.switchnat.app.ss.ShadowsocksUdpCodecTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

Run:

```bash
git add android/app/src/main/kotlin/dev/switchnat/app/ss android/app/src/test/kotlin/dev/switchnat/app/ss
git commit -m "feat: add Shadowsocks UDP codec"
```

## Task 6: Shadowsocks UDP Network Client and Packet Loop

**Files:**
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksUdpClient.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/packet/PacketLoop.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/packet/PacketLoopTest.kt`

**Interfaces:**
- Consumes `Ipv4Packet`, `NatTable`, `TargetAddress`, `ShadowsocksUdpCodec`, and `Diagnostics`.
- Produces `SocketProtector.protectDatagramSocket(socket: DatagramSocket): Boolean`.
- Produces `ShadowsocksUdpClient.send(target: TargetAddress, payload: ByteArray)`.
- Produces `PacketLoop.handleTunPacket(bytes: ByteArray): PacketLoopDecision`.

- [ ] **Step 1: Write failing PacketLoop unit test**

Create `android/app/src/test/kotlin/dev/switchnat/app/packet/PacketLoopTest.kt`:

```kotlin
package dev.switchnat.app.packet

import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.ss.TargetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PacketLoopTest {
    @Test
    fun acceptsSwitchUdpAndSendsToRemoteTarget() {
        val sent = mutableListOf<Pair<TargetAddress, ByteArray>>()
        val loop = PacketLoop(
            switchSubnetPrefix = "192.168.43.",
            natTable = NatTable(clock = { 1_000L }),
            diagnostics = Diagnostics(),
            udpSender = object : UdpRelaySender {
                override fun send(target: TargetAddress, payload: ByteArray) {
                    sent += target to payload
                }
            },
            tunWriter = object : TunWriter {
                override fun write(packet: ByteArray) = Unit
            }
        )

        val packet = Ipv4Packet.buildUdp(
            sourceIp = "192.168.43.22",
            destinationIp = "8.8.8.8",
            sourcePort = 54321,
            destinationPort = 53,
            payload = byteArrayOf(1, 2, 3)
        )

        val decision = loop.handleTunPacket(packet)

        assertEquals(PacketLoopDecision.ACCEPTED_UDP, decision)
        assertEquals(TargetAddress("8.8.8.8", 53), sent.single().first)
        assertArrayEquals(byteArrayOf(1, 2, 3), sent.single().second)
    }

    @Test
    fun buildsTunResponseFromRemoteUdpResponse() {
        val written = mutableListOf<ByteArray>()
        val table = NatTable(clock = { 1_000L })
        table.remember("192.168.43.22", 54321, "8.8.8.8", 53)

        val loop = PacketLoop(
            switchSubnetPrefix = "192.168.43.",
            natTable = table,
            diagnostics = Diagnostics(),
            udpSender = object : UdpRelaySender {
                override fun send(target: TargetAddress, payload: ByteArray) = Unit
            },
            tunWriter = object : TunWriter {
                override fun write(packet: ByteArray) {
                    written += packet
                }
            }
        )

        loop.handleRemoteUdp(TargetAddress("8.8.8.8", 53), byteArrayOf(9, 8, 7))

        val response = Ipv4Packet.parse(written.single())
        requireNotNull(response)
        assertEquals("8.8.8.8", response.sourceIp)
        assertEquals("192.168.43.22", response.destinationIp)
        assertEquals(53, response.udp?.sourcePort)
        assertEquals(54321, response.udp?.destinationPort)
        assertArrayEquals(byteArrayOf(9, 8, 7), response.udp?.payload)
    }
}
```

- [ ] **Step 2: Implement PacketLoop**

Create `PacketLoop.kt`:

```kotlin
package dev.switchnat.app.packet

import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.ss.TargetAddress

interface UdpRelaySender {
    fun send(target: TargetAddress, payload: ByteArray)
}

interface TunWriter {
    fun write(packet: ByteArray)
}

enum class PacketLoopDecision {
    ACCEPTED_UDP,
    IGNORED_NON_IPV4,
    IGNORED_NON_UDP,
    IGNORED_NON_SWITCH_SOURCE
}

class PacketLoop(
    private val switchSubnetPrefix: String,
    private val natTable: NatTable,
    private val diagnostics: Diagnostics,
    private val udpSender: UdpRelaySender,
    private val tunWriter: TunWriter
) {
    fun handleTunPacket(bytes: ByteArray): PacketLoopDecision {
        diagnostics.incrementUdpRead()
        val packet = Ipv4Packet.parse(bytes) ?: return PacketLoopDecision.IGNORED_NON_IPV4
        val udp = packet.udp ?: return PacketLoopDecision.IGNORED_NON_UDP
        if (!packet.sourceIp.startsWith(switchSubnetPrefix)) {
            return PacketLoopDecision.IGNORED_NON_SWITCH_SOURCE
        }
        diagnostics.incrementUdpAccepted()
        diagnostics.setLearnedSwitchIp(packet.sourceIp)
        natTable.remember(
            switchIp = packet.sourceIp,
            switchPort = udp.sourcePort,
            remoteIp = packet.destinationIp,
            remotePort = udp.destinationPort
        )
        diagnostics.setActiveMappings(natTable.size())
        udpSender.send(TargetAddress(packet.destinationIp, udp.destinationPort), udp.payload)
        diagnostics.incrementUdpSent()
        return PacketLoopDecision.ACCEPTED_UDP
    }

    fun handleRemoteUdp(source: TargetAddress, payload: ByteArray) {
        diagnostics.incrementUdpReceived()
        val mapping = natTable.findByRemote(source.host, source.port)
        if (mapping == null) {
            diagnostics.setLastError("NAT_MAPPING_MISS", "No mapping for ${source.host}:${source.port}")
            return
        }
        val packet = Ipv4Packet.buildUdp(
            sourceIp = mapping.remoteIp,
            destinationIp = mapping.switchIp,
            sourcePort = mapping.remotePort,
            destinationPort = mapping.switchPort,
            payload = payload
        )
        tunWriter.write(packet)
        diagnostics.incrementUdpWritten()
        diagnostics.setActiveMappings(natTable.size())
    }
}
```

- [ ] **Step 3: Implement network UDP client**

Extend `ShadowsocksUdpClient.kt`:

```kotlin
package dev.switchnat.app.ss

import dev.switchnat.app.config.ShadowsocksConfig
import dev.switchnat.app.packet.UdpRelaySender
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

interface SocketProtector {
    fun protectDatagramSocket(socket: DatagramSocket): Boolean
}

class ShadowsocksUdpClient(
    private val config: ShadowsocksConfig,
    private val codec: ShadowsocksUdpCodec = ShadowsocksUdpCodec(config),
    private val protector: SocketProtector
) : UdpRelaySender {
    private val socket = DatagramSocket()
    private val serverAddress = InetAddress.getByName(config.serverHost)

    init {
        require(protector.protectDatagramSocket(socket)) { "Failed to protect Shadowsocks UDP socket" }
    }

    override fun send(target: TargetAddress, payload: ByteArray) {
        val encrypted = codec.encryptPacket(target, payload)
        socket.send(DatagramPacket(encrypted, encrypted.size, serverAddress, config.serverPort))
    }

    fun receive(bufferSize: Int = 65_535): DecodedUdpPacket {
        val buffer = ByteArray(bufferSize)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        return codec.decryptPacket(packet.data.copyOf(packet.length))
    }

    fun close() {
        socket.close()
    }
}
```

- [ ] **Step 4: Run PacketLoop tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests dev.switchnat.app.packet.PacketLoopTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

Run:

```bash
git add android/app/src/main/kotlin/dev/switchnat/app/packet android/app/src/main/kotlin/dev/switchnat/app/ss android/app/src/test/kotlin/dev/switchnat/app/packet
git commit -m "feat: connect packet loop to Shadowsocks UDP"
```

## Task 7: HTTP CONNECT Proxy and Shadowsocks TCP Connector

**Files:**
- Create: `android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksTcpClient.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/proxy/HttpConnectProxy.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/proxy/HttpConnectProxyTest.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/ss/ShadowsocksTcpCodecTest.kt`

**Interfaces:**
- Produces `TcpOutboundFactory.open(target: TargetAddress): TcpOutbound`.
- Produces `TcpOutbound.input(): InputStream`, `TcpOutbound.output(): OutputStream`, and `TcpOutbound.close()`.
- Produces `HttpConnectProxy.start()` and `stop()`.
- Consumes `Diagnostics.incrementTcpConnections()`.

- [ ] **Step 1: Write failing HTTP CONNECT proxy test**

Create `android/app/src/test/kotlin/dev/switchnat/app/proxy/HttpConnectProxyTest.kt`:

```kotlin
package dev.switchnat.app.proxy

import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.ss.SocketTcpOutbound
import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.TcpOutbound
import dev.switchnat.app.ss.TcpOutboundFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class HttpConnectProxyTest {
    @Test
    fun connectRequestReturns200AndRelaysBytes() {
        val echoServer = ServerSocket(0)
        thread(start = true) {
            val socket = echoServer.accept()
            socket.getInputStream().copyTo(socket.getOutputStream())
            socket.close()
        }

        val proxy = HttpConnectProxy(
            bindHost = "127.0.0.1",
            port = 0,
            outboundFactory = object : TcpOutboundFactory {
                override fun open(target: TargetAddress): TcpOutbound {
                    assertEquals(TargetAddress("example.com", 443), target)
                    return SocketTcpOutbound(Socket("127.0.0.1", echoServer.localPort))
                }
            },
            diagnostics = Diagnostics()
        )
        proxy.start()

        val client = Socket("127.0.0.1", proxy.boundPort())
        client.getOutputStream().write(
            "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\nping".toByteArray()
        )
        client.getOutputStream().flush()

        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        assertEquals("HTTP/1.1 200 Connection Established", reader.readLine())
        assertEquals("", reader.readLine())
        val payload = CharArray(4)
        reader.read(payload)
        assertEquals("ping", String(payload))

        client.close()
        proxy.stop()
        echoServer.close()
    }
}
```

- [ ] **Step 2: Write failing Shadowsocks TCP stream codec test**

Create `android/app/src/test/kotlin/dev/switchnat/app/ss/ShadowsocksTcpCodecTest.kt`:

```kotlin
package dev.switchnat.app.ss

import dev.switchnat.app.config.ShadowsocksConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ShadowsocksTcpCodecTest {
    @Test
    fun writesSaltAndEncryptedAddressChunkThenDecryptsIt() {
        val config = ShadowsocksConfig(
            serverHost = "127.0.0.1",
            serverPort = 8388,
            password = "secret",
            method = "aes-128-gcm",
            httpProxyPort = 7890
        )
        val output = ByteArrayOutputStream()
        val encoder = ShadowsocksTcpCodec(
            config = config,
            saltGenerator = FixedSaltGenerator(ByteArray(16) { it.toByte() })
        )
        val decoder = ShadowsocksTcpCodec(config = config)

        encoder.writeInitialRequest(output, TargetAddress("example.com", 443))

        val decoded = decoder.readChunk(ByteArrayInputStream(output.toByteArray()))
        val address = ShadowsocksAddressCodec.decode(decoded)

        assertEquals(TargetAddress("example.com", 443), address.target)
        assertEquals(address.nextOffset, decoded.size)
    }

    @Test
    fun encryptedOutputCanBeReadBackAsPlainChunks() {
        val config = ShadowsocksConfig(
            serverHost = "127.0.0.1",
            serverPort = 8388,
            password = "secret",
            method = "aes-128-gcm",
            httpProxyPort = 7890
        )
        val raw = ByteArrayOutputStream()
        val encoder = ShadowsocksTcpCodec(
            config = config,
            saltGenerator = FixedSaltGenerator(ByteArray(16) { it.toByte() })
        )
        val decoder = ShadowsocksTcpCodec(config = config)
        encoder.writeInitialRequest(raw, TargetAddress("example.com", 443))
        encoder.writeChunk(raw, byteArrayOf(1, 2, 3))

        val input = ByteArrayInputStream(raw.toByteArray())
        decoder.readChunk(input)
        assertArrayEquals(byteArrayOf(1, 2, 3), decoder.readChunk(input))
    }
}
```

- [ ] **Step 3: Implement TCP factory interface and Shadowsocks TCP stream codec**

Create `ShadowsocksTcpClient.kt`:

```kotlin
package dev.switchnat.app.ss

import dev.switchnat.app.config.ShadowsocksConfig
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

interface TcpOutbound : Closeable {
    fun input(): InputStream
    fun output(): OutputStream
}

class SocketTcpOutbound(private val socket: Socket) : TcpOutbound {
    override fun input(): InputStream = socket.getInputStream()
    override fun output(): OutputStream = socket.getOutputStream()
    override fun close() = socket.close()
}

interface TcpOutboundFactory {
    fun open(target: TargetAddress): TcpOutbound
}

class ShadowsocksTcpClient(
    private val config: ShadowsocksConfig,
    private val protector: TcpSocketProtector
) : TcpOutboundFactory {
    override fun open(target: TargetAddress): TcpOutbound {
        val socket = Socket()
        require(protector.protectTcpSocket(socket)) { "Failed to protect Shadowsocks TCP socket" }
        socket.connect(InetSocketAddress(config.serverHost, config.serverPort), 10_000)
        val outputCodec = ShadowsocksTcpCodec(config)
        val inputCodec = ShadowsocksTcpCodec(config)
        outputCodec.writeInitialRequest(socket.getOutputStream(), target)
        return ShadowsocksTcpOutbound(socket, inputCodec, outputCodec)
    }
}

interface TcpSocketProtector {
    fun protectTcpSocket(socket: Socket): Boolean
}

class ShadowsocksTcpCodec(
    private val config: ShadowsocksConfig,
    private val saltGenerator: SaltGenerator = SecureSaltGenerator()
) {
    private val method = AeadMethod.fromWireName(config.method)
    private val masterKey = ShadowsocksKeys.evpBytesToKey(config.password, method.keySize)
    private var subkey: ByteArray? = null
    private val encryptNonce = NonceCounter()
    private val decryptNonce = NonceCounter()

    fun writeInitialRequest(output: OutputStream, target: TargetAddress) {
        val salt = saltGenerator.nextSalt(method.saltSize)
        subkey = ShadowsocksKeys.hkdfSha1(masterKey, salt, method.keySize)
        output.write(salt)
        writeChunk(output, ShadowsocksAddressCodec.encode(target))
    }

    fun writeChunk(output: OutputStream, plain: ByteArray) {
        require(plain.size <= 0x3fff) { "Shadowsocks TCP chunk too large" }
        val cipher = AeadCipher(method, requireSubkey())
        val lengthBytes = byteArrayOf(((plain.size ushr 8) and 0xff).toByte(), (plain.size and 0xff).toByte())
        output.write(cipher.encrypt(lengthBytes, encryptNonce.next()))
        output.write(cipher.encrypt(plain, encryptNonce.next()))
        output.flush()
    }

    fun readChunk(input: InputStream): ByteArray {
        if (subkey == null) {
            val salt = input.readExact(method.saltSize)
            subkey = ShadowsocksKeys.hkdfSha1(masterKey, salt, method.keySize)
        }
        val cipher = AeadCipher(method, requireSubkey())
        val encryptedLength = input.readExact(2 + method.tagSize)
        val lengthBytes = cipher.decrypt(encryptedLength, decryptNonce.next())
        val length = ((lengthBytes[0].toInt() and 0xff) shl 8) or (lengthBytes[1].toInt() and 0xff)
        val encryptedPayload = input.readExact(length + method.tagSize)
        return cipher.decrypt(encryptedPayload, decryptNonce.next())
    }

    private fun requireSubkey(): ByteArray = subkey ?: error("TCP salt has not been initialized")
}

class ShadowsocksTcpOutbound(
    private val socket: Socket,
    private val inputCodec: ShadowsocksTcpCodec,
    private val outputCodec: ShadowsocksTcpCodec
) : TcpOutbound {
    override fun input(): InputStream = object : InputStream() {
        private var buffer = ByteArray(0)
        private var offset = 0

        override fun read(): Int {
            if (offset >= buffer.size) {
                buffer = inputCodec.readChunk(socket.getInputStream())
                offset = 0
            }
            return buffer[offset++].toInt() and 0xff
        }
    }

    override fun output(): OutputStream = object : OutputStream() {
        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()))
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var cursor = off
            var remaining = len
            while (remaining > 0) {
                val chunk = minOf(remaining, 0x3fff)
                outputCodec.writeChunk(socket.getOutputStream(), b.copyOfRange(cursor, cursor + chunk))
                cursor += chunk
                remaining -= chunk
            }
        }
    }

    override fun close() = socket.close()
}

private fun InputStream.readExact(length: Int): ByteArray {
    val out = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val count = read(out, offset, length - offset)
        if (count < 0) error("Unexpected end of stream")
        offset += count
    }
    return out
}
```

- [ ] **Step 4: Implement HTTP CONNECT proxy**

Create `HttpConnectProxy.kt`:

```kotlin
package dev.switchnat.app.proxy

import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.TcpOutbound
import dev.switchnat.app.ss.TcpOutboundFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class HttpConnectProxy(
    private val bindHost: String,
    private val port: Int,
    private val outboundFactory: TcpOutboundFactory,
    private val diagnostics: Diagnostics
) {
    private var serverSocket: ServerSocket? = null
    private val clientThreads = CopyOnWriteArrayList<Thread>()

    fun start() {
        val server = ServerSocket(port, 50, java.net.InetAddress.getByName(bindHost))
        serverSocket = server
        thread(name = "SwitchNAT-HTTPProxy", start = true) {
            while (!server.isClosed) {
                runCatching {
                    val client = server.accept()
                    val worker = thread(start = true) { handleClient(client) }
                    clientThreads += worker
                }.onFailure {
                    if (!server.isClosed) {
                        diagnostics.setLastError("HTTP_PROXY_ACCEPT", it.message ?: it.javaClass.simpleName)
                    }
                }
            }
        }
    }

    fun boundPort(): Int = serverSocket?.localPort ?: 0

    fun stop() {
        serverSocket?.close()
        clientThreads.forEach { it.interrupt() }
        clientThreads.clear()
    }

    private fun handleClient(client: Socket) {
        client.use { local ->
            val reader = BufferedReader(InputStreamReader(local.getInputStream(), Charsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 3 || parts[0] != "CONNECT") {
                local.getOutputStream().write("HTTP/1.1 405 Method Not Allowed\r\n\r\n".toByteArray())
                return
            }
            while (true) {
                val line = reader.readLine() ?: return
                if (line.isEmpty()) break
            }
            val hostPort = parts[1]
            val separator = hostPort.lastIndexOf(':')
            if (separator <= 0) {
                local.getOutputStream().write("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray())
                return
            }
            val target = TargetAddress(hostPort.substring(0, separator), hostPort.substring(separator + 1).toInt())
            val remote = outboundFactory.open(target)
            diagnostics.incrementTcpConnections()
            local.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            local.getOutputStream().flush()
            relayBothWays(local, remote)
        }
    }

    private fun relayBothWays(a: Socket, b: TcpOutbound) {
        b.use { remote ->
            val t1 = thread(start = true) { a.getInputStream().copyTo(remote.output()) }
            val t2 = thread(start = true) { remote.input().copyTo(a.getOutputStream()) }
            t1.join()
            t2.join()
        }
    }
}
```

- [ ] **Step 5: Run TCP tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests dev.switchnat.app.proxy.HttpConnectProxyTest --tests dev.switchnat.app.ss.ShadowsocksTcpCodecTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

Run:

```bash
git add android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksTcpClient.kt android/app/src/main/kotlin/dev/switchnat/app/proxy android/app/src/test/kotlin/dev/switchnat/app/proxy android/app/src/test/kotlin/dev/switchnat/app/ss/ShadowsocksTcpCodecTest.kt
git commit -m "feat: add local HTTP CONNECT proxy"
```

## Task 8: Android VPN Foreground Service

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/vpn/VpnController.kt`
- Create: `android/app/src/main/kotlin/dev/switchnat/app/hotspot/HotspotAddressDetector.kt`
- Create: `android/app/src/test/kotlin/dev/switchnat/app/hotspot/HotspotAddressDetectorTest.kt`
- Modify: `android/app/src/main/kotlin/dev/switchnat/app/bridge/NativeBridge.kt`

**Interfaces:**
- Produces Android service `dev.switchnat.app.vpn.SwitchNatVpnService`.
- Produces `VpnController.start(context: Context, config: ShadowsocksConfig)` and `stop(context: Context)`.
- Produces `HotspotAddressDetector.chooseGateway(candidates: List<String>): String`.
- Consumes `PacketLoop`, `ShadowsocksUdpClient`, `HttpConnectProxy`, and `Diagnostics`.

- [ ] **Step 1: Write failing gateway detector test**

Create `HotspotAddressDetectorTest.kt`:

```kotlin
package dev.switchnat.app.hotspot

import org.junit.Assert.assertEquals
import org.junit.Test

class HotspotAddressDetectorTest {
    @Test
    fun prefersPrivateGatewayEndingInOne() {
        val gateway = HotspotAddressDetector.chooseGateway(
            listOf("10.88.0.2", "192.168.43.1", "172.20.10.1")
        )

        assertEquals("192.168.43.1", gateway)
    }

    @Test
    fun fallsBackToFirstPrivateAddress() {
        val gateway = HotspotAddressDetector.chooseGateway(
            listOf("10.88.0.2", "100.64.1.1")
        )

        assertEquals("10.88.0.2", gateway)
    }
}
```

- [ ] **Step 2: Implement gateway detector**

Create `HotspotAddressDetector.kt`:

```kotlin
package dev.switchnat.app.hotspot

import java.net.Inet4Address
import java.net.NetworkInterface

object HotspotAddressDetector {
    fun detectCandidates(): List<String> {
        return NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
            .flatMap { iface -> iface.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress ?: "" }
            .filter { it.isNotBlank() && isPrivate(it) }
    }

    fun chooseGateway(candidates: List<String>): String {
        val privateCandidates = candidates.filter { isPrivate(it) }
        return privateCandidates.firstOrNull { it.startsWith("192.168.") && it.endsWith(".1") }
            ?: privateCandidates.firstOrNull { it.startsWith("172.") && it.endsWith(".1") }
            ?: privateCandidates.firstOrNull { it.startsWith("10.") && it.endsWith(".1") }
            ?: privateCandidates.firstOrNull()
            ?: ""
    }

    private fun isPrivate(ip: String): Boolean {
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }
}
```

- [ ] **Step 3: Add VPN service manifest entries**

Update `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<application
    android:label="SwitchNAT"
    android:name="${applicationName}"
    android:icon="@mipmap/ic_launcher">

    <service
        android:name=".vpn.SwitchNatVpnService"
        android:exported="false"
        android:foregroundServiceType="specialUse"
        android:permission="android.permission.BIND_VPN_SERVICE">
        <intent-filter>
            <action android:name="android.net.VpnService" />
        </intent-filter>
        <property
            android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
            android:value="vpn_packet_tunnel" />
    </service>
</application>
```

Keep the generated Flutter `MainActivity` entry in the same application block.

- [ ] **Step 4: Implement VpnController**

Create `VpnController.kt`:

```kotlin
package dev.switchnat.app.vpn

import android.content.Context
import android.content.Intent
import dev.switchnat.app.config.ShadowsocksConfig

object VpnController {
    const val ACTION_START = "dev.switchnat.app.action.START"
    const val ACTION_STOP = "dev.switchnat.app.action.STOP"
    const val EXTRA_CONFIG = "dev.switchnat.app.extra.CONFIG"

    fun start(context: Context, config: ShadowsocksConfig) {
        val intent = Intent(context, SwitchNatVpnService::class.java).apply {
            action = ACTION_START
            putExtra("serverHost", config.serverHost)
            putExtra("serverPort", config.serverPort)
            putExtra("password", config.password)
            putExtra("method", config.method)
            putExtra("httpProxyPort", config.httpProxyPort)
        }
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, SwitchNatVpnService::class.java).apply {
            action = ACTION_STOP
        }
        context.startService(intent)
    }
}
```

- [ ] **Step 5: Implement SwitchNatVpnService**

Create `SwitchNatVpnService.kt` with these behaviors:

```kotlin
package dev.switchnat.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import dev.switchnat.app.MainActivity
import dev.switchnat.app.config.ShadowsocksConfig
import dev.switchnat.app.diagnostics.DiagnosticsHub
import dev.switchnat.app.diagnostics.VpnRunState
import dev.switchnat.app.hotspot.HotspotAddressDetector
import dev.switchnat.app.packet.NatTable
import dev.switchnat.app.packet.PacketLoop
import dev.switchnat.app.packet.TunWriter
import dev.switchnat.app.proxy.HttpConnectProxy
import dev.switchnat.app.ss.ShadowsocksTcpClient
import dev.switchnat.app.ss.ShadowsocksUdpClient
import dev.switchnat.app.ss.SocketProtector
import dev.switchnat.app.ss.TcpSocketProtector
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket
import kotlin.concurrent.thread

class SwitchNatVpnService : VpnService(), SocketProtector, TcpSocketProtector {
    private val diagnostics = DiagnosticsHub.diagnostics
    private var tun: ParcelFileDescriptor? = null
    private var httpProxy: HttpConnectProxy? = null
    private var udpClient: ShadowsocksUdpClient? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            VpnController.ACTION_START -> startFromIntent(intent)
            VpnController.ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startFromIntent(intent: Intent) {
        val config = ShadowsocksConfig(
            serverHost = intent.getStringExtra("serverHost") ?: "",
            serverPort = intent.getIntExtra("serverPort", 0),
            password = intent.getStringExtra("password") ?: "",
            method = intent.getStringExtra("method") ?: "aes-128-gcm",
            httpProxyPort = intent.getIntExtra("httpProxyPort", 7890)
        )
        diagnostics.setState(VpnRunState.STARTING)
        startForeground(1, buildNotification("SwitchNAT running"))

        val gateway = HotspotAddressDetector.chooseGateway(HotspotAddressDetector.detectCandidates())
        diagnostics.setHotspotGateway(gateway, config.httpProxyPort)

        tun = Builder()
            .setSession("SwitchNAT")
            .addAddress("10.88.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .setMtu(1500)
            .establish()

        val fd = tun ?: run {
            diagnostics.setState(VpnRunState.FAILED)
            diagnostics.setLastError("VPN_ESTABLISH", "TUN establish returned null")
            stopSelf()
            return
        }

        val natTable = NatTable()
        val tunOutput = FileOutputStream(fd.fileDescriptor)
        udpClient = ShadowsocksUdpClient(config, protector = this)
        val packetLoop = PacketLoop(
            switchSubnetPrefix = gateway.substringBeforeLast('.', missingDelimiterValue = gateway) + ".",
            natTable = natTable,
            diagnostics = diagnostics,
            udpSender = udpClient!!,
            tunWriter = object : TunWriter {
                override fun write(packet: ByteArray) {
                    tunOutput.write(packet)
                    tunOutput.flush()
                }
            }
        )

        httpProxy = HttpConnectProxy(
            bindHost = "0.0.0.0",
            port = config.httpProxyPort,
            outboundFactory = ShadowsocksTcpClient(config, protector = this),
            diagnostics = diagnostics
        )
        httpProxy?.start()

        running = true
        diagnostics.setState(VpnRunState.RUNNING)

        thread(name = "SwitchNAT-TUN", start = true) {
            val input = FileInputStream(fd.fileDescriptor)
            val buffer = ByteArray(65_535)
            while (running) {
                val count = input.read(buffer)
                if (count > 0) {
                    packetLoop.handleTunPacket(buffer.copyOf(count))
                }
            }
        }

        thread(name = "SwitchNAT-SS-UDP", start = true) {
            val client = udpClient ?: return@thread
            while (running) {
                runCatching {
                    val decoded = client.receive()
                    packetLoop.handleRemoteUdp(decoded.target, decoded.payload)
                }.onFailure {
                    if (running) diagnostics.setLastError("SS_UDP_RECEIVE", it.message ?: it.javaClass.simpleName)
                }
            }
        }
    }

    override fun protectDatagramSocket(socket: DatagramSocket): Boolean = protect(socket)
    override fun protectTcpSocket(socket: Socket): Boolean = protect(socket)

    override fun onDestroy() {
        running = false
        httpProxy?.stop()
        udpClient?.close()
        tun?.close()
        diagnostics.setState(VpnRunState.STOPPED)
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "switchnat_vpn"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "SwitchNAT VPN", NotificationManager.IMPORTANCE_LOW))
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, channelId)
            .setContentTitle("SwitchNAT")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pending)
            .build()
    }
}
```

- [ ] **Step 6: Wire NativeBridge start/stop to VpnController**

In `NativeBridge`, replace `result.success(true)` in the successful `start` branch:

```kotlin
VpnController.start(activity, config)
result.success(true)
```

Replace the `stop` branch:

```kotlin
VpnController.stop(activity)
result.success(true)
```

- [ ] **Step 7: Run unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests dev.switchnat.app.hotspot.HotspotAddressDetectorTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Build debug APK**

Run:

```bash
flutter build apk --debug
```

Expected:

```text
Built build/app/outputs/flutter-apk/app-debug.apk
```

- [ ] **Step 9: Commit**

Run:

```bash
git add android/app/src/main android/app/src/test
git commit -m "feat: add Android VPN foreground service"
```

## Task 9: Flutter Configuration, Status, and Logs UI

**Files:**
- Modify: `lib/src/app.dart`
- Create: `lib/src/config/config_store.dart`
- Create: `lib/src/screens/config_screen.dart`
- Create: `lib/src/screens/status_screen.dart`
- Create: `lib/src/screens/logs_screen.dart`
- Create: `test/screens/config_screen_test.dart`

**Interfaces:**
- Consumes `SsConfig`, `NativeBridge`, and `ServiceStatus`.
- Produces a 3-tab Flutter UI: Config, Status, Logs.
- Start button calls `NativeBridge.start(config)`.
- Stop button calls `NativeBridge.stop()`.

- [ ] **Step 1: Write failing widget test**

Create `test/screens/config_screen_test.dart`:

```dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/screens/config_screen.dart';

void main() {
  testWidgets('config screen validates required fields', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: ConfigScreen()));

    await tester.tap(find.text('Start'));
    await tester.pump();

    expect(find.text('Server host is required'), findsOneWidget);
    expect(find.text('Password is required'), findsOneWidget);
  });
}
```

- [ ] **Step 2: Implement config screen**

Create `ConfigScreen` with:

```dart
import 'package:flutter/material.dart';
import '../config/ss_config.dart';
import '../native/native_bridge.dart';

class ConfigScreen extends StatefulWidget {
  const ConfigScreen({super.key, this.bridge});

  final NativeBridge? bridge;

  @override
  State<ConfigScreen> createState() => _ConfigScreenState();
}

class _ConfigScreenState extends State<ConfigScreen> {
  final host = TextEditingController();
  final port = TextEditingController(text: '8388');
  final password = TextEditingController();
  final proxyPort = TextEditingController(text: '7890');
  String method = 'aes-128-gcm';
  List<String> errors = const [];

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        TextField(controller: host, decoration: const InputDecoration(labelText: 'Server host')),
        TextField(controller: port, decoration: const InputDecoration(labelText: 'Server port'), keyboardType: TextInputType.number),
        TextField(controller: password, decoration: const InputDecoration(labelText: 'Password'), obscureText: true),
        DropdownButtonFormField<String>(
          value: method,
          decoration: const InputDecoration(labelText: 'Method'),
          items: SsConfig.supportedMethods
              .map((value) => DropdownMenuItem(value: value, child: Text(value)))
              .toList(),
          onChanged: (value) => setState(() => method = value ?? method),
        ),
        TextField(controller: proxyPort, decoration: const InputDecoration(labelText: 'HTTP proxy port'), keyboardType: TextInputType.number),
        const SizedBox(height: 16),
        FilledButton(onPressed: start, child: const Text('Start')),
        OutlinedButton(onPressed: () => (widget.bridge ?? NativeBridge()).stop(), child: const Text('Stop')),
        for (final error in errors)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(error, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ),
      ],
    );
  }

  Future<void> start() async {
    final config = SsConfig(
      serverHost: host.text,
      serverPort: int.tryParse(port.text) ?? 0,
      password: password.text,
      method: method,
      httpProxyPort: int.tryParse(proxyPort.text) ?? 0,
    );
    final validation = config.validate();
    setState(() => errors = validation);
    if (validation.isEmpty) {
      await (widget.bridge ?? NativeBridge()).start(config);
    }
  }
}
```

- [ ] **Step 3: Implement status and logs screens**

Create `StatusScreen`:

```dart
import 'package:flutter/material.dart';
import '../native/native_bridge.dart';
import '../state/service_status.dart';

class StatusScreen extends StatelessWidget {
  const StatusScreen({super.key, this.bridge});

  final NativeBridge? bridge;

  @override
  Widget build(BuildContext context) {
    final native = bridge ?? NativeBridge();
    return StreamBuilder<ServiceStatus>(
      stream: native.statusStream,
      builder: (context, snapshot) {
        final status = snapshot.data;
        if (status == null) return const Center(child: Text('Stopped'));
        final rows = <String, String>{
          'State': status.state.name,
          'Hotspot gateway': status.hotspotGateway,
          'Switch proxy': status.switchProxyAddress,
          'Switch IP': status.learnedSwitchIp,
          'UDP read': '${status.udpRead}',
          'UDP accepted': '${status.udpAccepted}',
          'UDP sent': '${status.udpSent}',
          'UDP received': '${status.udpReceived}',
          'UDP written': '${status.udpWritten}',
          'Mappings': '${status.activeMappings}',
          'TCP connections': '${status.tcpConnections}',
          'Last error': status.lastError,
        };
        return ListView(
          padding: const EdgeInsets.all(16),
          children: rows.entries
              .map((entry) => ListTile(title: Text(entry.key), subtitle: Text(entry.value)))
              .toList(),
        );
      },
    );
  }
}
```

Create `LogsScreen`:

```dart
import 'package:flutter/material.dart';

class LogsScreen extends StatelessWidget {
  const LogsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Center(child: Text('Recent packet samples appear in Status for MVP'));
  }
}
```

- [ ] **Step 4: Wire app tabs**

Modify `app.dart`:

```dart
import 'package:flutter/material.dart';
import 'screens/config_screen.dart';
import 'screens/logs_screen.dart';
import 'screens/status_screen.dart';

class SwitchNatApp extends StatefulWidget {
  const SwitchNatApp({super.key});

  @override
  State<SwitchNatApp> createState() => _SwitchNatAppState();
}

class _SwitchNatAppState extends State<SwitchNatApp> {
  int index = 0;

  @override
  Widget build(BuildContext context) {
    final screens = const [ConfigScreen(), StatusScreen(), LogsScreen()];
    return MaterialApp(
      title: 'SwitchNAT',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff246B5A)),
        useMaterial3: true,
      ),
      home: Scaffold(
        appBar: AppBar(title: const Text('SwitchNAT')),
        body: screens[index],
        bottomNavigationBar: NavigationBar(
          selectedIndex: index,
          onDestinationSelected: (value) => setState(() => index = value),
          destinations: const [
            NavigationDestination(icon: Icon(Icons.settings), label: 'Config'),
            NavigationDestination(icon: Icon(Icons.monitor_heart), label: 'Status'),
            NavigationDestination(icon: Icon(Icons.article), label: 'Logs'),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 5: Run Flutter tests**

Run:

```bash
flutter test
```

Expected:

```text
All tests passed!
```

- [ ] **Step 6: Commit**

Run:

```bash
git add lib test
git commit -m "feat: add Flutter MVP controls and status UI"
```

## Task 10: Device Verification Workflow

**Files:**
- Create: `docs/testing/xiaomi15-android16-verification.md`

**Interfaces:**
- Consumes the debug APK.
- Produces a written verification record for Xiaomi 15 / Android 16.

- [ ] **Step 1: Create verification checklist**

Create `docs/testing/xiaomi15-android16-verification.md`:

```markdown
# Xiaomi 15 Android 16 Verification

Date:
Device:
Android build:
Carrier:
Shadowsocks server:
Shadowsocks method:

## Baseline

- Hotspot enabled:
- Switch connected to hotspot:
- Switch proxy disabled:
- Baseline NAT type:
- Baseline connection-test IP:
- Baseline download speed:

## App Startup

- VPN permission prompt appeared:
- VPN status reached running:
- Hotspot gateway shown:
- Switch proxy address shown:
- Last error:

## UDP Without Switch Proxy

- Switch proxy disabled:
- UDP read count before test:
- UDP read count after test:
- UDP accepted count after test:
- UDP sent count after test:
- UDP received count after test:
- UDP written count after test:
- Learned Switch IP:
- Active mappings:
- NAT type after app enabled:
- Result: pass/fail

## TCP With Switch Proxy

- Switch proxy host:
- Switch proxy port:
- TCP connection count before test:
- TCP connection count after test:
- Connection-test IP changed:
- Download speed changed:
- eShop load improved:
- Result: pass/fail

## Game Test

- Game:
- Matchmaking success:
- Session duration:
- Disconnects:
- UDP mappings peak:
- Last error:
- Result: pass/fail

## Notes
```

- [ ] **Step 2: Build debug APK**

Run:

```bash
flutter build apk --debug
```

Expected:

```text
Built build/app/outputs/flutter-apk/app-debug.apk
```

- [ ] **Step 3: Install on Xiaomi 15**

Run:

```bash
adb install -r build/app/outputs/flutter-apk/app-debug.apk
```

Expected:

```text
Success
```

- [ ] **Step 4: Run baseline Switch NAT test**

Manual actions:

1. Disable Switch proxy.
2. Enable phone hotspot.
3. Connect Switch to hotspot.
4. Run Switch connection test.
5. Fill the Baseline section in the verification doc.

- [ ] **Step 5: Run UDP capture test**

Manual actions:

1. Start SwitchNAT app.
2. Grant VPN permission.
3. Leave Switch proxy disabled.
4. Run Switch connection test.
5. Fill the UDP Without Switch Proxy section.

Expected pass condition:

```text
udpRead > 0
udpAccepted > 0
udpSent > 0
udpReceived > 0
udpWritten > 0
NAT type improves from C to A or materially improves from baseline
```

- [ ] **Step 6: Run TCP proxy test**

Manual actions:

1. In Switch network settings, set proxy host to the app's displayed hotspot gateway.
2. Set proxy port to the app's displayed HTTP proxy port.
3. Run Switch connection test.
4. Open Nintendo eShop or start a download.
5. Fill the TCP With Switch Proxy section.

Expected pass condition:

```text
tcpConnections > 0
Connection-test IP or speed changes through the Shadowsocks server
eShop or download behavior improves compared with baseline
```

- [ ] **Step 7: Commit verification doc template**

Run:

```bash
git add docs/testing/xiaomi15-android16-verification.md
git commit -m "docs: add Xiaomi 15 verification checklist"
```

## Plan Self-Review

Spec coverage:

- Flutter UI, config, start/stop, status, and logs are covered by Tasks 1, 2, 3, and 9.
- Android `VpnService` foreground service is covered by Task 8.
- IPv4 UDP packet handling and NAT mapping are covered by Tasks 4 and 6.
- Shadowsocks UDP relay is covered by Tasks 5 and 6.
- Local HTTP CONNECT proxy is covered by Task 7.
- Diagnostics are covered by Tasks 3, 6, 8, 9, and 10.
- Xiaomi 15 / Android 16 verification is covered by Task 10.
- iOS is intentionally kept outside implementation scope while preserving Flutter/native boundaries in Tasks 2 and 9.

Red-flag scan:

- The plan contains no intentionally blank sections or deferred implementation markers.

Type consistency:

- Dart `SsConfig` maps to Kotlin `ShadowsocksConfig`.
- Kotlin `TargetAddress` is used by SS UDP, SS TCP, PacketLoop, and HTTP proxy.
- `DiagnosticsSnapshot.toMap()` uses keys consumed by Dart `ServiceStatus.fromJson`.
- `UdpRelaySender` is implemented by `ShadowsocksUdpClient` and consumed by `PacketLoop`.
- `TcpOutboundFactory` is implemented by `ShadowsocksTcpClient` and consumed by `HttpConnectProxy`.
