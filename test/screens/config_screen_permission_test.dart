import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/config_store.dart';
import 'package:switchnat/src/config/ss_config.dart';
import 'package:switchnat/src/native/native_bridge.dart';
import 'package:switchnat/src/screens/config_screen.dart';

void main() {
  testWidgets('permission denial does not start VPN', (tester) async {
    final bridge = _DeniedPermissionBridge();
    await tester.pumpWidget(
      MaterialApp(
        home: ConfigScreen(
          bridge: bridge,
          store: const _ValidConfigStore(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Start'));
    await tester.pumpAndSettle();

    expect(find.text('VPN permission denied'), findsOneWidget);
    expect(bridge.startCalled, isFalse);
  });
}

class _DeniedPermissionBridge extends NativeBridge {
  bool startCalled = false;

  @override
  Future<bool> requestVpnPermission() async => false;

  @override
  Future<bool> start(SsConfig config) async {
    startCalled = true;
    return true;
  }
}

class _ValidConfigStore implements ConfigStore {
  const _ValidConfigStore();

  static const config = SsConfig(
    serverHost: '127.0.0.1',
    serverPort: 8388,
    password: 'secret',
    method: 'aes-128-gcm',
    httpProxyPort: 7890,
  );

  @override
  Future<void> clear() async {}

  @override
  Future<SsConfig?> load() async => config;

  @override
  Future<void> save(SsConfig config) async {}
}
