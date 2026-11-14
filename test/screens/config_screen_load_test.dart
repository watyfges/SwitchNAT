import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/profile_library.dart';
import 'package:switchnat/src/config/ss_config.dart';
import 'package:switchnat/src/screens/config_screen.dart';
import 'package:switchnat/src/state/app_controller.dart';
import '../support/controller_fakes.dart';

void main() {
  testWidgets('shows saved encryption method after library load', (
    tester,
  ) async {
    const config = SsConfig(
      serverHost: '203.0.113.10',
      serverPort: 8388,
      password: 'secret',
      method: 'aes-256-gcm',
      httpProxyPort: 7890,
    );
    final library = ProfileLibrary(
      schemaVersion: 1,
      profiles: const [ServerProfile(id: 'p1', name: 'Tokyo', config: config)],
      selectedProfileId: 'p1',
      stunServers: const [],
    );
    final controller = AppController(
      bridge: TestBridge(),
      store: MemoryConfigStore(library),
    );
    await controller.initialize();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: ConfigScreen(controller: controller)),
      ),
    );
    expect(find.text('aes-256-gcm'), findsOneWidget);
    controller.dispose();
  });

  testWidgets('shows saved VLESS Reality fields after library load', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(420, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    const config = SsConfig(
      protocol: SsConfig.protocolVlessReality,
      serverHost: 'vless.example.com',
      serverPort: 443,
      password: '',
      method: 'aes-128-gcm',
      httpProxyPort: 7890,
      uuid: 'd342d11e-d424-4583-b36e-524ab1f0afa4',
      serverName: 'www.example.com',
      realityPublicKey: 'public-key',
      realityShortId: '0123abcd',
    );
    final library = ProfileLibrary(
      schemaVersion: 2,
      profiles: const [
        ServerProfile(id: 'v1', name: 'Reality', config: config),
      ],
      selectedProfileId: 'v1',
      stunServers: const [],
    );
    final controller = AppController(
      bridge: TestBridge(),
      store: MemoryConfigStore(library),
    );
    addTearDown(controller.dispose);
    await controller.initialize();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: ConfigScreen(controller: controller)),
      ),
    );

    expect(find.text('VLESS + Reality'), findsOneWidget);
    expect(find.widgetWithText(TextField, 'UUID'), findsOneWidget);
    expect(find.widgetWithText(TextField, 'Server name (SNI)'), findsOneWidget);
    expect(
      find.widgetWithText(TextField, 'Reality public key'),
      findsOneWidget,
    );
    expect(find.widgetWithText(TextField, 'Password'), findsNothing);
    expect(
      tester
          .widget<TextField>(find.widgetWithText(TextField, 'UUID'))
          .controller
          ?.text,
      'd342d11e-d424-4583-b36e-524ab1f0afa4',
    );
    expect(tester.takeException(), isNull);
  });
}
