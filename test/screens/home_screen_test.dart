import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/profile_library.dart';
import 'package:switchnat/src/config/ss_config.dart';
import 'package:switchnat/src/screens/home_screen.dart';
import 'package:switchnat/src/state/app_controller.dart';

import '../support/controller_fakes.dart';

void main() {
  testWidgets(
    'stacks full-width metric cards and shows the complete endpoint',
    (tester) async {
      tester.view.physicalSize = const Size(360, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final bridge = TestBridge();
      final controller = AppController(
        bridge: bridge,
        store: MemoryConfigStore(_library),
      );
      addTearDown(controller.dispose);
      await controller.initialize();

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: HomeScreen(controller: controller)),
        ),
      );
      bridge.emitStatus(<Object?, Object?>{
        'state': 'running',
        'activeProfileId': 'p1',
        'activeProfileName': 'Tokyo',
        'activeServerEndpoint': '203.0.113.10:8388',
        'switchPresence': 'connected',
        'learnedSwitchIp': '10.146.3.249',
        'natTest': <Object?, Object?>{
          'state': 'available',
          'legacyLabel': 'Port-Restricted Cone',
          'mappedAddress': '203.0.113.8:51234',
        },
      });
      await tester.pump();

      final switchCard = find.byKey(const Key('switch-card'));
      final natCard = find.byKey(const Key('nat-card'));
      final switchSize = tester.getSize(switchCard);
      final natSize = tester.getSize(find.byKey(const Key('nat-card')));
      expect(switchSize.width, natSize.width);
      expect(switchSize.width, greaterThan(300));
      expect(
        tester.getTopLeft(natCard).dy,
        greaterThan(tester.getTopLeft(switchCard).dy),
      );

      final detail = tester.widget<Text>(find.text('203.0.113.8:51234'));
      expect(detail.maxLines, 2);
      expect(detail.overflow, isNot(TextOverflow.ellipsis));
      expect(find.text('Selected node'), findsOneWidget);
      expect(find.text('Select'), findsOneWidget);

      await tester.tap(find.text('Select'));
      await tester.pumpAndSettle();
      expect(find.text('Select node'), findsOneWidget);
      expect(
        find.byKey(const ValueKey<String>('home-profile-option-p1')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('does not repeat an endpoint-shaped migrated profile name', (
    tester,
  ) async {
    final endpoint = '${_config.serverHost}:${_config.serverPort}';
    final controller = AppController(
      bridge: TestBridge(),
      store: MemoryConfigStore(
        ProfileLibrary(
          schemaVersion: 1,
          profiles: <ServerProfile>[
            ServerProfile(id: 'p1', name: endpoint, config: _config),
          ],
          selectedProfileId: 'p1',
          stunServers: const <StunServerEndpoint>[],
        ),
      ),
    );
    addTearDown(controller.dispose);
    await controller.initialize();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: HomeScreen(controller: controller)),
      ),
    );

    final endpointText = tester.widget<Text>(find.textContaining(endpoint));
    expect(endpointText.data, endpoint);
  });

  testWidgets('shows an unconfigured label for a blank node', (tester) async {
    const blankConfig = SsConfig(
      serverHost: '',
      serverPort: 8388,
      password: '',
      method: 'aes-128-gcm',
      httpProxyPort: 7890,
    );
    final controller = AppController(
      bridge: TestBridge(),
      store: MemoryConfigStore(
        ProfileLibrary(
          schemaVersion: 1,
          profiles: <ServerProfile>[
            ServerProfile(id: 'p1', name: 'New node', config: blankConfig),
          ],
          selectedProfileId: 'p1',
          stunServers: const <StunServerEndpoint>[],
        ),
      ),
    );
    addTearDown(controller.dispose);
    await controller.initialize();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: HomeScreen(controller: controller)),
      ),
    );

    expect(find.textContaining('Not configured'), findsOneWidget);
    expect(find.text(':8388'), findsNothing);
  });
}

const _config = SsConfig(
  serverHost: '203.0.113.10',
  serverPort: 8388,
  password: 'secret',
  method: 'aes-128-gcm',
  httpProxyPort: 7890,
);

final _library = ProfileLibrary(
  schemaVersion: 1,
  profiles: const <ServerProfile>[
    ServerProfile(id: 'p1', name: 'Tokyo', config: _config),
  ],
  selectedProfileId: 'p1',
  stunServers: const <StunServerEndpoint>[],
);
