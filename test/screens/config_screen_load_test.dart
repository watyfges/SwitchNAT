import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/config_store.dart';
import 'package:switchnat/src/config/ss_config.dart';
import 'package:switchnat/src/screens/config_screen.dart';

void main() {
  testWidgets('shows the saved encryption method after async load', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: ConfigScreen(
          store: _SavedConfigStore(
            SsConfig(
              serverHost: '203.0.113.10',
              serverPort: 8388,
              password: 'secret',
              method: 'aes-256-gcm',
              httpProxyPort: 7890,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('aes-256-gcm'), findsOneWidget);
    expect(find.text('aes-128-gcm'), findsNothing);
  });
}

class _SavedConfigStore implements ConfigStore {
  const _SavedConfigStore(this.config);

  final SsConfig config;

  @override
  Future<void> clear() async {}

  @override
  Future<SsConfig?> load() async => config;

  @override
  Future<void> save(SsConfig config) async {}
}
