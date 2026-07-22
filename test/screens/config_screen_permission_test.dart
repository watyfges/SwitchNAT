import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/profile_library.dart';
import 'package:switchnat/src/config/ss_config.dart';
import 'package:switchnat/src/state/app_controller.dart';
import '../support/controller_fakes.dart';

void main() {
  test('permission denial does not start VPN', () async {
    final bridge = TestBridge()..vpnPermission = false;
    const config = SsConfig(
      serverHost: '203.0.113.10',
      serverPort: 8388,
      password: 'secret',
      method: 'aes-128-gcm',
      httpProxyPort: 7890,
    );
    final library = ProfileLibrary(
      schemaVersion: 1,
      profiles: const [ServerProfile(id: 'p1', name: 'Tokyo', config: config)],
      selectedProfileId: 'p1',
      stunServers: const [],
    );
    final controller = AppController(
      bridge: bridge,
      store: MemoryConfigStore(library),
    );
    await controller.initialize();
    await controller.start();
    expect(controller.feedback, 'VPN permission denied');
    expect(bridge.startCalls, 0);
    controller.dispose();
  });
}
