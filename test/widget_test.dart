import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/app.dart';

void main() {
  testWidgets('shows SwitchNAT home text', (WidgetTester tester) async {
    await tester.pumpWidget(const SwitchNatApp());

    expect(find.text('SwitchNAT'), findsOneWidget);
  });
}
