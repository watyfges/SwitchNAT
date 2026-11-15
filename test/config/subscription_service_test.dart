import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:switchnat/src/config/ss_config.dart';
import 'package:switchnat/src/config/subscription_service.dart';

void main() {
  const yaml = '''
proxies:
  - name: SS node
    type: ss
    server: ss.example.com
    port: 8388
    cipher: aes-256-gcm
    password: secret
    udp: true
  - name: Reality node
    type: vless
    server: reality.example.com
    port: 443
    uuid: 7f98ecad-2209-4b9f-89a5-582ea80642e6
    flow: xtls-rprx-vision
    network: tcp
    tls: true
    servername: cover.example.com
    client-fingerprint: chrome
    reality-opts:
      public-key: abcdefghijklmnopqrstuvwxyzABCDEFGH123456789
      short-id: 0123456789abcdef
  - name: Unsupported
    type: trojan
    server: other.example.com
    port: 443
''';

  test('parses supported Clash Shadowsocks and VLESS Reality nodes', () {
    final result = SubscriptionService().parseClashYaml(
      yaml,
      udpTargets: '1.2.3.4:1234',
    );

    expect(result.nodes, hasLength(2));
    expect(result.skippedCount, 1);
    expect(result.nodes.first.config.method, 'aes-256-gcm');
    expect(result.nodes.last.config.protocol, SsConfig.protocolVlessReality);
    expect(result.nodes.last.config.realityPublicKey, isNotEmpty);
    expect(result.nodes.last.nodeKey, hasLength(64));
    expect(result.nodes.last.config.udpTargets, '1.2.3.4:1234');
  });

  test('download rejects an HTTP error without exposing the URL', () async {
    final service = SubscriptionService(
      client: MockClient((_) async => http.Response('no', 403)),
    );

    await expectLater(
      service.download(
        url: 'https://example.com/private-token',
        udpTargets: '',
      ),
      throwsA(
        isA<SubscriptionException>().having(
          (error) => error.toString(),
          'message',
          allOf(contains('HTTP 403'), isNot(contains('private-token'))),
        ),
      ),
    );
  });

  test('node key stays stable when the provider renames a node', () {
    final renamed = yaml.replaceFirst('Reality node', 'Reality renamed');

    final before = SubscriptionService().parseClashYaml(yaml, udpTargets: '');
    final after = SubscriptionService().parseClashYaml(renamed, udpTargets: '');

    expect(before.nodes[1].nodeKey, after.nodes[1].nodeKey);
  });
}
