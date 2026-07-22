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
}
