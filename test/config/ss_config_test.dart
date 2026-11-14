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

  test(
    'VLESS Reality config round-trips and does not require an SS password',
    () {
      const config = SsConfig(
        protocol: SsConfig.protocolVlessReality,
        serverHost: 'vless.example.com',
        serverPort: 443,
        password: '',
        method: 'aes-128-gcm',
        httpProxyPort: 7890,
        uuid: 'd342d11e-d424-4583-b36e-524ab1f0afa4',
        serverName: 'www.example.com',
        realityPublicKey: 'public-key',
        realityShortId: '0123abcd',
        fingerprint: 'chrome',
        flow: 'xtls-rprx-vision',
        spiderX: '/',
      );

      expect(config.validate(), isEmpty);
      expect(SsConfig.fromJson(config.toJson()), config);
    },
  );

  test('VLESS Reality validates UUID and Reality fields', () {
    const config = SsConfig(
      protocol: SsConfig.protocolVlessReality,
      serverHost: 'vless.example.com',
      serverPort: 443,
      password: '',
      method: 'aes-128-gcm',
      httpProxyPort: 7890,
      uuid: 'not-a-uuid',
      serverName: '',
      realityPublicKey: '',
      realityShortId: 'xyz',
      fingerprint: '',
    );

    expect(config.validate(), contains('A valid VLESS UUID is required'));
    expect(config.validate(), contains('Reality server name is required'));
    expect(config.validate(), contains('Reality public key is required'));
    expect(config.validate(), contains('Reality fingerprint is required'));
  });
}
