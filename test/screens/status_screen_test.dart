import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/screens/status_screen.dart';
import 'package:switchnat/src/state/service_status.dart';

void main() {
  testWidgets('shows dynamic UDP flow and peer alias metrics', (tester) async {
    tester.view.physicalSize = const Size(800, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final status = ServiceStatus.fromJson(<Object?, Object?>{
      'state': 'running',
      'activeUdpFlows': 2,
      'activePeerAliases': 4,
      'peerAliasesCreated': 5,
      'peerAliasesExpired': 3,
    });

    await tester.pumpWidget(
      MaterialApp(
        home: StatusScreen(statusStream: Stream<ServiceStatus>.value(status)),
      ),
    );
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(find.text('UDP relay flows'), 300);
    await tester.pumpAndSettle();

    expect(find.text('UDP relay flows'), findsOneWidget);
    expect(find.text('P2P aliases'), findsOneWidget);
    expect(find.text('Aliases created'), findsOneWidget);
    expect(find.text('Aliases expired'), findsOneWidget);
    expect(find.text('2'), findsOneWidget);
    expect(find.text('4'), findsOneWidget);
    expect(find.text('5'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
  });
}
