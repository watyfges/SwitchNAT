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
