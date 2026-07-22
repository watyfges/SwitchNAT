import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/profile_library.dart';

void main() {
  test('profile library round trips selected profile and global stun list', () {
    final library = ProfileLibrary.fromJson(<String, Object?>{
      'schemaVersion': 1,
      'selectedProfileId': 'p1',
      'profiles': <Object?>[
        <String, Object?>{
          'id': 'p1',
          'name': 'Tokyo',
          'config': <String, Object?>{
            'serverHost': '203.0.113.10',
            'serverPort': 8388,
            'password': 'secret',
            'method': 'aes-128-gcm',
            'httpProxyPort': 7890,
            'udpTargets': '203.0.113.10:8123',
          },
        },
      ],
      'stunServers': <Object?>[
        <String, Object?>{
          'id': 's1',
          'host': 'stun.cloudflare.com',
          'port': 3478,
          'enabled': true,
        },
      ],
    });

    expect(library.selectedProfile.name, 'Tokyo');
    expect(library.stunServers.single.enabled, isTrue);
    expect(library.toJson()['selectedProfileId'], 'p1');
  });
}
