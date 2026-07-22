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
    await tester.scrollUntilVisible(
      find.text('UDP target rules').first,
      300,
      scrollable: page,
    );
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
