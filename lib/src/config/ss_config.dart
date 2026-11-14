class SsConfig {
  static const protocolShadowsocks = 'shadowsocks';
  static const protocolVlessReality = 'vless-reality';
  static const supportedProtocols = <String>{
    protocolShadowsocks,
    protocolVlessReality,
  };
  static const supportedMethods = <String>{
    'aes-128-gcm',
    'aes-256-gcm',
    'chacha20-ietf-poly1305',
  };

  const SsConfig({
    this.protocol = protocolShadowsocks,
    required this.serverHost,
    required this.serverPort,
    required this.password,
    required this.method,
    required this.httpProxyPort,
    this.udpTargets = '',
    this.uuid = '',
    this.flow = 'xtls-rprx-vision',
    this.serverName = '',
    this.realityPublicKey = '',
    this.realityShortId = '',
    this.fingerprint = 'chrome',
    this.spiderX = '/',
  });

  final String protocol;
  final String serverHost;
  final int serverPort;
  final String password;
  final String method;
  final int httpProxyPort;
  final String udpTargets;
  final String uuid;
  final String flow;
  final String serverName;
  final String realityPublicKey;
  final String realityShortId;
  final String fingerprint;
  final String spiderX;

  bool get isVlessReality => protocol == protocolVlessReality;

  Map<String, Object?> toJson() => <String, Object?>{
    'protocol': protocol,
    'serverHost': serverHost,
    'serverPort': serverPort,
    'password': password,
    'method': method,
    'httpProxyPort': httpProxyPort,
    'udpTargets': udpTargets,
    'uuid': uuid,
    'flow': flow,
    'serverName': serverName,
    'realityPublicKey': realityPublicKey,
    'realityShortId': realityShortId,
    'fingerprint': fingerprint,
    'spiderX': spiderX,
  };

  factory SsConfig.fromJson(Map<String, Object?> json) {
    return SsConfig(
      protocol: json['protocol'] as String? ?? protocolShadowsocks,
      serverHost: json['serverHost'] as String? ?? '',
      serverPort: json['serverPort'] as int? ?? 0,
      password: json['password'] as String? ?? '',
      method: json['method'] as String? ?? 'aes-128-gcm',
      httpProxyPort: json['httpProxyPort'] as int? ?? 7890,
      udpTargets: json['udpTargets'] as String? ?? '',
      uuid: json['uuid'] as String? ?? '',
      flow: json['flow'] as String? ?? 'xtls-rprx-vision',
      serverName: json['serverName'] as String? ?? '',
      realityPublicKey: json['realityPublicKey'] as String? ?? '',
      realityShortId: json['realityShortId'] as String? ?? '',
      fingerprint: json['fingerprint'] as String? ?? 'chrome',
      spiderX: json['spiderX'] as String? ?? '/',
    );
  }

  List<String> validate() {
    final errors = <String>[];
    if (!supportedProtocols.contains(protocol)) {
      errors.add('Unsupported protocol: $protocol');
    }
    if (serverHost.trim().isEmpty) {
      errors.add('Server host is required');
    }
    if (serverPort < 1 || serverPort > 65535) {
      errors.add('Server port must be between 1 and 65535');
    }
    if (protocol == protocolShadowsocks) {
      if (password.isEmpty) {
        errors.add('Password is required');
      }
      if (!supportedMethods.contains(method)) {
        errors.add('Unsupported method: $method');
      }
    } else if (protocol == protocolVlessReality) {
      if (!_uuidPattern.hasMatch(uuid)) {
        errors.add('A valid VLESS UUID is required');
      }
      if (serverName.trim().isEmpty) {
        errors.add('Reality server name is required');
      }
      if (realityPublicKey.trim().isEmpty) {
        errors.add('Reality public key is required');
      }
      if (realityShortId.isNotEmpty &&
          (!_hexPattern.hasMatch(realityShortId) ||
              realityShortId.length.isOdd ||
              realityShortId.length > 16)) {
        errors.add('Reality short ID must be even-length hex up to 16 chars');
      }
      if (fingerprint.trim().isEmpty) {
        errors.add('Reality fingerprint is required');
      }
    }
    if (httpProxyPort < 1024 || httpProxyPort > 65535) {
      errors.add('HTTP proxy port must be between 1024 and 65535');
    }
    for (final target in _parseUdpTargets(udpTargets)) {
      final separator = target.lastIndexOf(':');
      final host = separator <= 0 ? '' : target.substring(0, separator);
      final port = separator <= 0
          ? 0
          : int.tryParse(target.substring(separator + 1)) ?? 0;
      if (port < 1 || port > 65535) {
        errors.add('UDP target must be IPv4:port or IPv4/prefix:port: $target');
        continue;
      }
      if (host.contains('/')) {
        final cidr = host.split('/');
        final prefix = cidr.length == 2 ? int.tryParse(cidr[1]) : null;
        if (cidr.length != 2 || !_isIpv4Literal(cidr[0])) {
          errors.add(
            'UDP target must be IPv4:port or IPv4/prefix:port: $target',
          );
        } else if (prefix == null || prefix < 21 || prefix > 32) {
          errors.add('UDP target CIDR prefix must be /21 to /32: $target');
        }
      } else if (!_isIpv4Literal(host)) {
        errors.add('UDP target must be IPv4:port: $target');
      }
    }
    return errors;
  }

  @override
  bool operator ==(Object other) {
    return other is SsConfig &&
        other.protocol == protocol &&
        other.serverHost == serverHost &&
        other.serverPort == serverPort &&
        other.password == password &&
        other.method == method &&
        other.httpProxyPort == httpProxyPort &&
        other.udpTargets == udpTargets &&
        other.uuid == uuid &&
        other.flow == flow &&
        other.serverName == serverName &&
        other.realityPublicKey == realityPublicKey &&
        other.realityShortId == realityShortId &&
        other.fingerprint == fingerprint &&
        other.spiderX == spiderX;
  }

  @override
  int get hashCode => Object.hash(
    protocol,
    serverHost,
    serverPort,
    password,
    method,
    httpProxyPort,
    udpTargets,
    uuid,
    flow,
    serverName,
    realityPublicKey,
    realityShortId,
    fingerprint,
    spiderX,
  );
}

final RegExp _uuidPattern = RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$',
);
final RegExp _hexPattern = RegExp(r'^[0-9a-fA-F]+$');

List<String> _parseUdpTargets(String raw) {
  return raw
      .split(RegExp(r'[\s,]+'))
      .map((value) => value.trim())
      .where((value) => value.isNotEmpty)
      .toList(growable: false);
}

bool _isIpv4Literal(String host) {
  final parts = host.split('.');
  if (parts.length != 4) return false;
  for (final part in parts) {
    if (part.isEmpty || part.contains(RegExp(r'[^0-9]'))) return false;
    final value = int.tryParse(part);
    if (value == null || value < 0 || value > 255) return false;
  }
  return true;
}
