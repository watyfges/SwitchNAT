import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/state/service_status.dart';

void main() {
  test('parses packet samples from native status', () {
    final status = ServiceStatus.fromJson(<Object?, Object?>{
      'state': 'running',
      'activeUdpFlows': 2,
      'activePeerAliases': 4,
      'peerAliasesCreated': 5,
      'peerAliasesExpired': 3,
      'samples': <Object?>[
        <Object?, Object?>{
          'timestampMillis': 1234,
          'protocol': 'UDP',
          'source': '192.168.43.22:54321',
          'destination': '8.8.8.8:53',
          'direction': 'outbound',
          'outcome': 'sent',
        },
      ],
    });

    expect(status.samples, hasLength(1));
    expect(status.samples.single.timestampMillis, 1234);
    expect(status.samples.single.protocol, 'UDP');
    expect(status.samples.single.source, '192.168.43.22:54321');
    expect(status.samples.single.destination, '8.8.8.8:53');
    expect(status.samples.single.direction, 'outbound');
    expect(status.samples.single.outcome, 'sent');
    expect(status.activeUdpFlows, 2);
    expect(status.activePeerAliases, 4);
    expect(status.peerAliasesCreated, 5);
    expect(status.peerAliasesExpired, 3);
  });
}
