import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/ss_config.dart';

void main() {
  test('valid config serializes and deserializes', () {
    const config = SsConfig(
      serverHost: '203.0.113.10',
      serverPort: 8388,
      password: 'secret',
      method: 'aes-128-gcm',
      httpProxyPort: 7890,
      udpTargets: '203.0.113.10:8123',
    );

    final restored = SsConfig.fromJson(config.toJson());

    expect(restored, config);
    expect(restored.validate(), isEmpty);
  });

  test('invalid config returns exact validation messages', () {
    const config = SsConfig(
      serverHost: '',
      serverPort: 0,
      password: '',
      method: 'rc4-md5',
      httpProxyPort: 70000,
    );

    expect(config.validate(), <String>[
      'Server host is required',
      'Server port must be between 1 and 65535',
      'Password is required',
      'Unsupported method: rc4-md5',
      'HTTP proxy port must be between 1024 and 65535',
    ]);
  });

  test('CIDR UDP target is accepted', () {
    const config = SsConfig(
      serverHost: '203.0.113.10',
      serverPort: 8388,
      password: 'secret',
      method: 'aes-128-gcm',
      httpProxyPort: 7890,
      udpTargets: '203.0.112.0/21:8123',
    );

    expect(config.validate(), isEmpty);
  });

  test('large CIDR UDP target is rejected', () {
    const config = SsConfig(
      serverHost: '203.0.113.10',
      serverPort: 8388,
      password: 'secret',
      method: 'aes-128-gcm',
      httpProxyPort: 7890,
      udpTargets: '10.0.0.0/20:8123',
    );

    expect(config.validate(), <String>[
      'UDP target CIDR prefix must be /21 to /32: 10.0.0.0/20:8123',
    ]);
  });

  test('invalid UDP target returns validation message', () {
    const config = SsConfig(
      serverHost: '203.0.113.10',
      serverPort: 8388,
      password: 'secret',
      method: 'aes-128-gcm',
      httpProxyPort: 7890,
      udpTargets: 'example.com:8123',
    );

    expect(config.validate(), <String>[
      'UDP target must be IPv4:port: example.com:8123',
    ]);
  });
}
