class SsConfig {
  static const supportedMethods = <String>{
    'aes-128-gcm',
    'aes-256-gcm',
    'chacha20-ietf-poly1305',
  };

  const SsConfig({
    required this.serverHost,
    required this.serverPort,
    required this.password,
    required this.method,
    required this.httpProxyPort,
    this.udpTargets = '',
  });

  final String serverHost;
  final int serverPort;
  final String password;
  final String method;
  final int httpProxyPort;
  final String udpTargets;

  Map<String, Object?> toJson() => <String, Object?>{
    'serverHost': serverHost,
    'serverPort': serverPort,
    'password': password,
    'method': method,
    'httpProxyPort': httpProxyPort,
    'udpTargets': udpTargets,
  };

  factory SsConfig.fromJson(Map<String, Object?> json) {
    return SsConfig(
      serverHost: json['serverHost'] as String? ?? '',
      serverPort: json['serverPort'] as int? ?? 0,
      password: json['password'] as String? ?? '',
      method: json['method'] as String? ?? 'aes-128-gcm',
      httpProxyPort: json['httpProxyPort'] as int? ?? 7890,
      udpTargets: json['udpTargets'] as String? ?? '',
    );
  }

  List<String> validate() {
    final errors = <String>[];
    if (serverHost.trim().isEmpty) {
      errors.add('Server host is required');
    }
    if (serverPort < 1 || serverPort > 65535) {
      errors.add('Server port must be between 1 and 65535');
    }
    if (password.isEmpty) {
      errors.add('Password is required');
    }
    if (!supportedMethods.contains(method)) {
      errors.add('Unsupported method: $method');
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
        other.serverHost == serverHost &&
        other.serverPort == serverPort &&
        other.password == password &&
        other.method == method &&
        other.httpProxyPort == httpProxyPort &&
        other.udpTargets == udpTargets;
  }

  @override
  int get hashCode => Object.hash(
    serverHost,
    serverPort,
    password,
    method,
    httpProxyPort,
    udpTargets,
  );
}

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
