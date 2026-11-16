import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/profile_library.dart';
import 'package:switchnat/src/config/ss_config.dart';
import 'package:switchnat/src/screens/config_screen.dart';
import 'package:switchnat/src/state/app_controller.dart';
import '../support/controller_fakes.dart';

void main() {
  testWidgets('config stays compact with a long profile and editable targets', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final store = MemoryConfigStore(_library);
    final controller = AppController(bridge: TestBridge(), store: store);
    addTearDown(controller.dispose);
    await controller.initialize();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: ConfigScreen(controller: controller)),
      ),
    );
    expect(find.text('Node settings'), findsOneWidget);
    expect(find.byKey(const Key('profile-summary')), findsOneWidget);
    expect(find.text('New'), findsOneWidget);
    expect(find.text('Copy'), findsNothing);
    expect(find.byTooltip('More node actions'), findsOneWidget);
    expect(find.text('Default node'), findsOneWidget);
    expect(find.text('Shadowsocks'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.tap(find.byKey(const Key('profile-summary')));
    await tester.pumpAndSettle();
    expect(find.text('Select node'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey<String>('profile-option-p1')));
    await tester.pumpAndSettle();

    await tester.tap(find.text('New'));
    await tester.pumpAndSettle();
    expect(store.createCalls, 1);

    await tester.tap(find.byTooltip('More node actions'));
    await tester.pumpAndSettle();
    expect(find.text('Duplicate'), findsNothing);
    expect(find.text('Rename'), findsOneWidget);
    expect(find.text('Delete'), findsOneWidget);
    await tester.tapAt(const Offset(8, 8));
    await tester.pumpAndSettle();

    final page = find.byType(Scrollable).first;
    await tester.scrollUntilVisible(
      find.text('Advanced').first,
      300,
      scrollable: page,
    );
    expect(find.text('Advanced'), findsWidgets);
    await tester.drag(page, const Offset(0, -240));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('UDP target rules').first);
    await tester.tap(find.text('UDP target rules'));
    await tester.pump();
    expect(find.text('203.0.113.10:8123'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('Save changes'),
      300,
      scrollable: page,
    );
    expect(find.text('Save changes'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('adding a STUN server dismisses the editor without red screen', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final controller = AppController(
      bridge: TestBridge(),
      store: MemoryConfigStore(_library),
    );
    addTearDown(controller.dispose);
    await controller.initialize();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: ConfigScreen(controller: controller)),
      ),
    );

    final page = find.byType(Scrollable).first;
    await tester.scrollUntilVisible(
      find.text('STUN servers'),
      300,
      scrollable: page,
    );
    await tester.tap(find.text('STUN servers'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Add server'),
      200,
      scrollable: page,
    );
    await tester.tap(find.text('Add server'));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.descendant(
        of: find.byType(AlertDialog),
        matching: find.widgetWithText(TextField, 'Host'),
      ),
      'stun.sipnet.net',
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    await tester.pumpAndSettle();

    expect(find.text('stun.sipnet.net:3478'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('theme selector persists dark mode', (tester) async {
    final store = MemoryConfigStore(_library);
    final controller = AppController(bridge: TestBridge(), store: store);
    addTearDown(controller.dispose);
    await controller.initialize();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: ConfigScreen(controller: controller)),
      ),
    );

    final page = find.byType(Scrollable).first;
    await tester.scrollUntilVisible(
      find.text('Advanced').first,
      300,
      scrollable: page,
    );
    await tester.scrollUntilVisible(
      find.text('Appearance'),
      300,
      scrollable: page,
    );
    expect(find.text('Follow system'), findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('theme-mode-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('theme-mode-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Dark').last);
    await tester.pumpAndSettle();

    expect(store.value.themeMode, AppThemePreference.dark);
    expect(find.text('Dark'), findsOneWidget);
  });

  testWidgets(
    'subscription manager masks URL and managed nodes are read only',
    (tester) async {
      tester.view.physicalSize = const Size(360, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final store = MemoryConfigStore(_subscriptionLibrary);
      final controller = AppController(bridge: TestBridge(), store: store);
      addTearDown(controller.dispose);
      await controller.initialize();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: ConfigScreen(controller: controller)),
        ),
      );

      expect(find.text('Managed by subscription'), findsOneWidget);
      expect(find.byTooltip('More node actions'), findsNothing);
      expect(find.text('1 subscription'), findsOneWidget);

      await tester.tap(find.byKey(const Key('subscription-summary')));
      await tester.pumpAndSettle();
      expect(find.text('Main subscription'), findsOneWidget);
      expect(find.textContaining('subscription.example.com'), findsOneWidget);
      expect(find.textContaining('private-token'), findsNothing);
      expect(
        find.byKey(const ValueKey<String>('subscription-updated-sub-1')),
        findsOneWidget,
      );
      final updated = tester.widget<Text>(
        find.byKey(const ValueKey<String>('subscription-updated-sub-1')),
      );
      expect(updated.data, contains('Last updated:'));

      await tester.tap(
        find.byKey(const ValueKey<String>('subscription-enabled-sub-1')),
      );
      await tester.pumpAndSettle();
      expect(store.value.subscriptions.single.enabled, isFalse);
      expect(store.value.enabledProfiles.map((profile) => profile.id), <String>[
        'manual-p1',
      ]);
      expect(store.value.selectedProfileId, 'manual-p1');
      expect(tester.takeException(), isNull);
    },
  );
}

const _config = SsConfig(
  serverHost: 'very-long-node-name.example.test',
  serverPort: 5375,
  password: 'secret',
  method: 'aes-128-gcm',
  httpProxyPort: 7890,
  udpTargets: '203.0.113.10:8123',
);
final _library = ProfileLibrary(
  schemaVersion: 1,
  profiles: const [
    ServerProfile(
      id: 'p1',
      name: 'very-long-node-name.example.test:5375',
      config: _config,
    ),
  ],
  selectedProfileId: 'p1',
  stunServers: const [],
);

final _subscriptionLibrary = ProfileLibrary(
  schemaVersion: 3,
  profiles: const [
    ServerProfile(id: 'manual-p1', name: 'Manual', config: _config),
    ServerProfile(
      id: 'managed-p1',
      name: 'Tokyo',
      config: _config,
      subscriptionId: 'sub-1',
      subscriptionNodeKey: 'node-key',
    ),
  ],
  selectedProfileId: 'managed-p1',
  stunServers: const [],
  subscriptions: const [
    SubscriptionSource(
      id: 'sub-1',
      name: 'Main subscription',
      url:
          'https://this-is-a-very-long-subscription-host-name.subscription.example.com/path?token=private-token',
      updatedAtMillis: 1700000000000,
      nodeCount: 1,
    ),
  ],
);
