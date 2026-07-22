import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/state/service_status.dart';

void main() {
  test('parses packet samples from native status', () {
    final status = ServiceStatus.fromJson(<Object?, Object?>{
      'state': 'running',
      'activeProfileId': 'p1',
      'activeProfileName': 'Tokyo',
      'activeServerEndpoint': '203.0.113.10:8388',
      'sessionStartedAtMillis': 1000,
      'lastSwitchActivityMillis': 1200,
      'switchPresence': 'connected',
      'tcpTest': <Object?, Object?>{
        'state': 'available',
        'latencyMillis': 48,
        'completedAtMillis': 1100,
        'error': '',
      },
      'natTest': <Object?, Object?>{
        'state': 'available',
        'legacyLabel': 'Full Cone',
        'mappedAddress': '203.0.113.8:51234',
        'mappingBehavior': 'Endpoint-independent',
        'filteringBehavior': 'Endpoint-independent',
        'rttMillis': 31,
        'stunServer': 'stun.cloudflare.com:3478',
        'completedAtMillis': 1150,
        'error': '',
      },
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
    expect(status.activeProfileId, 'p1');
    expect(status.activeProfileName, 'Tokyo');
    expect(status.activeServerEndpoint, '203.0.113.10:8388');
    expect(status.switchPresence, SwitchPresence.connected);
    expect(status.tcpTest.state, DiagnosticRunState.available);
    expect(status.tcpTest.latencyMillis, 48);
    expect(status.natTest.legacyLabel, 'Full Cone');
    expect(status.natTest.mappedAddress, '203.0.113.8:51234');
  });
}
