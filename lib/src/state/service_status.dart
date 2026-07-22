enum VpnRunState {
  stopped,
  permissionNeeded,
  starting,
  running,
  stopping,
  failed,
}

enum DiagnosticRunState { idle, testing, available, partial, unavailable }

enum SwitchPresence { waiting, connected, inactive }

class TcpTestResult {
  const TcpTestResult({
    required this.state,
    required this.latencyMillis,
    required this.completedAtMillis,
    required this.error,
  });

  final DiagnosticRunState state;
  final int latencyMillis;
  final int completedAtMillis;
  final String error;

  factory TcpTestResult.fromJson(Object? value) {
    final json = _map(value);
    return TcpTestResult(
      state: _diagnosticState(json['state']),
      latencyMillis: json['latencyMillis'] as int? ?? 0,
      completedAtMillis: json['completedAtMillis'] as int? ?? 0,
      error: json['error'] as String? ?? '',
    );
  }
}

class NatTestResult {
  const NatTestResult({
    required this.state,
    required this.legacyLabel,
    required this.mappedAddress,
    required this.mappingBehavior,
    required this.filteringBehavior,
    required this.rttMillis,
    required this.stunServer,
    required this.completedAtMillis,
    required this.error,
  });

  final DiagnosticRunState state;
  final String legacyLabel;
  final String mappedAddress;
  final String mappingBehavior;
  final String filteringBehavior;
  final int rttMillis;
  final String stunServer;
  final int completedAtMillis;
  final String error;

  factory NatTestResult.fromJson(Object? value) {
    final json = _map(value);
    return NatTestResult(
      state: _diagnosticState(json['state']),
      legacyLabel: json['legacyLabel'] as String? ?? '',
      mappedAddress: json['mappedAddress'] as String? ?? '',
      mappingBehavior: json['mappingBehavior'] as String? ?? '',
      filteringBehavior: json['filteringBehavior'] as String? ?? '',
      rttMillis: json['rttMillis'] as int? ?? 0,
      stunServer: json['stunServer'] as String? ?? '',
      completedAtMillis: json['completedAtMillis'] as int? ?? 0,
      error: json['error'] as String? ?? '',
    );
  }
}

class PacketSample {
  const PacketSample({
    required this.timestampMillis,
    required this.protocol,
    required this.source,
    required this.destination,
    required this.direction,
    required this.outcome,
  });

  final int timestampMillis;
  final String protocol;
  final String source;
  final String destination;
  final String direction;
  final String outcome;

  factory PacketSample.fromJson(Map<Object?, Object?> json) {
    return PacketSample(
      timestampMillis: json['timestampMillis'] as int? ?? 0,
      protocol: json['protocol'] as String? ?? '',
      source: json['source'] as String? ?? '',
      destination: json['destination'] as String? ?? '',
      direction: json['direction'] as String? ?? '',
      outcome: json['outcome'] as String? ?? '',
    );
  }
}

class ServiceStatus {
  const ServiceStatus({
    required this.state,
    required this.activeProfileId,
    required this.activeProfileName,
    required this.activeServerEndpoint,
    required this.sessionStartedAtMillis,
    required this.lastSwitchActivityMillis,
    required this.switchPresence,
    required this.tcpTest,
    required this.natTest,
    required this.hotspotGateway,
    required this.switchProxyAddress,
    required this.learnedSwitchIp,
    required this.udpRead,
    required this.udpAccepted,
    required this.udpSent,
    required this.udpReceived,
    required this.udpWritten,
    required this.activeMappings,
    required this.activeUdpFlows,
    required this.activePeerAliases,
    required this.peerAliasesCreated,
    required this.peerAliasesExpired,
    required this.tcpConnections,
    required this.lastError,
    this.samples = const <PacketSample>[],
  });

  final VpnRunState state;
  final String activeProfileId;
  final String activeProfileName;
  final String activeServerEndpoint;
  final int sessionStartedAtMillis;
  final int lastSwitchActivityMillis;
  final SwitchPresence switchPresence;
  final TcpTestResult tcpTest;
  final NatTestResult natTest;
  final String hotspotGateway;
  final String switchProxyAddress;
  final String learnedSwitchIp;
  final int udpRead;
  final int udpAccepted;
  final int udpSent;
  final int udpReceived;
  final int udpWritten;
  final int activeMappings;
  final int activeUdpFlows;
  final int activePeerAliases;
  final int peerAliasesCreated;
  final int peerAliasesExpired;
  final int tcpConnections;
  final String lastError;
  final List<PacketSample> samples;

  factory ServiceStatus.fromJson(Map<Object?, Object?> json) {
    final stateName = json['state'] as String? ?? 'stopped';
    return ServiceStatus(
      state: VpnRunState.values.firstWhere(
        (value) => value.name == stateName,
        orElse: () => VpnRunState.stopped,
      ),
      activeProfileId: json['activeProfileId'] as String? ?? '',
      activeProfileName: json['activeProfileName'] as String? ?? '',
      activeServerEndpoint: json['activeServerEndpoint'] as String? ?? '',
      sessionStartedAtMillis: json['sessionStartedAtMillis'] as int? ?? 0,
      lastSwitchActivityMillis: json['lastSwitchActivityMillis'] as int? ?? 0,
      switchPresence: SwitchPresence.values.firstWhere(
        (value) => value.name == (json['switchPresence'] as String? ?? ''),
        orElse: () => SwitchPresence.waiting,
      ),
      tcpTest: TcpTestResult.fromJson(json['tcpTest']),
      natTest: NatTestResult.fromJson(json['natTest']),
      hotspotGateway: json['hotspotGateway'] as String? ?? '',
      switchProxyAddress: json['switchProxyAddress'] as String? ?? '',
      learnedSwitchIp: json['learnedSwitchIp'] as String? ?? '',
      udpRead: json['udpRead'] as int? ?? 0,
      udpAccepted: json['udpAccepted'] as int? ?? 0,
      udpSent: json['udpSent'] as int? ?? 0,
      udpReceived: json['udpReceived'] as int? ?? 0,
      udpWritten: json['udpWritten'] as int? ?? 0,
      activeMappings: json['activeMappings'] as int? ?? 0,
      activeUdpFlows: json['activeUdpFlows'] as int? ?? 0,
      activePeerAliases: json['activePeerAliases'] as int? ?? 0,
      peerAliasesCreated: json['peerAliasesCreated'] as int? ?? 0,
      peerAliasesExpired: json['peerAliasesExpired'] as int? ?? 0,
      tcpConnections: json['tcpConnections'] as int? ?? 0,
      lastError: json['lastError'] as String? ?? '',
      samples: _parseSamples(json['samples']),
    );
  }

  static List<PacketSample> _parseSamples(Object? value) {
    if (value is! List<Object?>) {
      return const <PacketSample>[];
    }
    return value
        .whereType<Map<Object?, Object?>>()
        .map(PacketSample.fromJson)
        .toList(growable: false);
  }
}

Map<Object?, Object?> _map(Object? value) =>
    value is Map<Object?, Object?> ? value : const <Object?, Object?>{};

DiagnosticRunState _diagnosticState(Object? value) =>
    DiagnosticRunState.values.firstWhere(
      (state) => state.name == value,
      orElse: () => DiagnosticRunState.idle,
    );
