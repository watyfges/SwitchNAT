import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:yaml/yaml.dart';

import 'ss_config.dart';

class ImportedSubscriptionNode {
  const ImportedSubscriptionNode({
    required this.nodeKey,
    required this.name,
    required this.config,
  });

  final String nodeKey;
  final String name;
  final SsConfig config;

  Map<String, Object?> toJson() => <String, Object?>{
    'nodeKey': nodeKey,
    'name': name,
    'config': config.toJson(),
  };
}

class SubscriptionImportResult {
  const SubscriptionImportResult({
    required this.nodes,
    required this.skippedCount,
  });

  final List<ImportedSubscriptionNode> nodes;
  final int skippedCount;
}

class SubscriptionException implements Exception {
  const SubscriptionException(this.message);
  final String message;
  @override
  String toString() => message;
}

class SubscriptionService {
  SubscriptionService({http.Client? client})
    : _client = client ?? http.Client();

  final http.Client _client;

  Future<SubscriptionImportResult> download({
    required String url,
    required String udpTargets,
    int httpProxyPort = 7890,
  }) async {
    final uri = Uri.tryParse(url.trim());
    if (uri == null ||
        !<String>{'http', 'https'}.contains(uri.scheme) ||
        uri.host.isEmpty) {
      throw const SubscriptionException('A valid HTTP(S) URL is required');
    }

    try {
      final response = await _client
          .get(uri, headers: const <String, String>{'User-Agent': 'SwitchNAT'})
          .timeout(const Duration(seconds: 20));
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw SubscriptionException(
          'Subscription server returned HTTP ${response.statusCode}',
        );
      }
      if (response.bodyBytes.length > _maximumSubscriptionBytes) {
        throw const SubscriptionException('Subscription file is too large');
      }
      return parseClashYaml(
        utf8.decode(response.bodyBytes),
        udpTargets: udpTargets,
        httpProxyPort: httpProxyPort,
      );
    } on SubscriptionException {
      rethrow;
    } catch (_) {
      throw const SubscriptionException('Unable to download subscription');
    }
  }

  SubscriptionImportResult parseClashYaml(
    String source, {
    required String udpTargets,
    int httpProxyPort = 7890,
  }) {
    try {
      final root = _map(loadYaml(source));
      final rawNodes = root['proxies'];
      if (rawNodes is! List) {
        throw const SubscriptionException(
          'The subscription is not a Clash YAML document',
        );
      }

      final nodes = <ImportedSubscriptionNode>[];
      final keyCounts = <String, int>{};
      var skipped = 0;
      for (final raw in rawNodes) {
        final node = _parseNode(
          _map(raw),
          udpTargets: udpTargets,
          httpProxyPort: httpProxyPort,
        );
        if (node == null) {
          skipped += 1;
        } else {
          final occurrence = keyCounts.update(
            node.nodeKey,
            (count) => count + 1,
            ifAbsent: () => 0,
          );
          nodes.add(
            occurrence == 0
                ? node
                : ImportedSubscriptionNode(
                    nodeKey: sha256
                        .convert(utf8.encode('${node.nodeKey}|$occurrence'))
                        .toString(),
                    name: node.name,
                    config: node.config,
                  ),
          );
        }
      }
      if (nodes.isEmpty) {
        throw const SubscriptionException(
          'No supported Shadowsocks or VLESS Reality nodes were found',
        );
      }
      return SubscriptionImportResult(
        nodes: List<ImportedSubscriptionNode>.unmodifiable(nodes),
        skippedCount: skipped,
      );
    } on SubscriptionException {
      rethrow;
    } catch (_) {
      throw const SubscriptionException('Unable to parse Clash subscription');
    }
  }

  ImportedSubscriptionNode? _parseNode(
    Map<String, Object?> node, {
    required String udpTargets,
    required int httpProxyPort,
  }) {
    final type = _string(node['type']).toLowerCase();
    final name = _string(node['name']).trim();
    final server = _string(node['server']).trim();
    final port = _integer(node['port']);
    if (name.isEmpty || server.isEmpty || port == null) return null;

    late final SsConfig config;
    late final String identity;
    if (type == 'ss') {
      final cipher = _string(node['cipher']).toLowerCase();
      final password = _string(node['password']);
      if (!SsConfig.supportedMethods.contains(cipher) || password.isEmpty) {
        return null;
      }
      config = SsConfig(
        serverHost: server,
        serverPort: port,
        password: password,
        method: cipher,
        httpProxyPort: httpProxyPort,
        udpTargets: udpTargets,
      );
      identity = 'ss|$server|$port|$cipher';
    } else if (type == 'vless') {
      final reality = _map(node['reality-opts']);
      final network = _string(node['network']).toLowerCase();
      if (reality.isEmpty ||
          (network.isNotEmpty && network != 'tcp' && network != 'raw')) {
        return null;
      }
      final uuid = _string(node['uuid']).trim();
      final spiderPath = _string(reality['spider-x']).trim();
      config = SsConfig(
        protocol: SsConfig.protocolVlessReality,
        serverHost: server,
        serverPort: port,
        password: '',
        method: 'aes-128-gcm',
        httpProxyPort: httpProxyPort,
        udpTargets: udpTargets,
        uuid: uuid,
        flow: _string(node['flow']).trim(),
        serverName: _string(node['servername'] ?? node['sni']).trim(),
        realityPublicKey: _string(
          reality['public-key'] ?? reality['publicKey'],
        ).trim(),
        realityShortId: _string(reality['short-id']).trim(),
        fingerprint: _string(
          node['client-fingerprint'] ?? node['fingerprint'] ?? 'chrome',
        ).trim(),
        spiderX: spiderPath.isEmpty ? '/' : spiderPath,
      );
      identity = 'vless|$server|$port|$uuid';
    } else {
      return null;
    }

    if (config.validate().isNotEmpty) return null;
    return ImportedSubscriptionNode(
      nodeKey: sha256.convert(utf8.encode(identity)).toString(),
      name: name,
      config: config,
    );
  }
}

Map<String, Object?> _map(Object? value) {
  if (value is! Map) return const <String, Object?>{};
  return <String, Object?>{
    for (final entry in value.entries)
      if (entry.key is String) entry.key as String: entry.value,
  };
}

String _string(Object? value) => value?.toString() ?? '';

int? _integer(Object? value) => switch (value) {
  int number when number >= 1 && number <= 65535 => number,
  String text => int.tryParse(text),
  _ => null,
};

const int _maximumSubscriptionBytes = 2 * 1024 * 1024;
