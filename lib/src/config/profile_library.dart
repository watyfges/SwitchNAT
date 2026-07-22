import 'ss_config.dart';

class ServerProfile {
  const ServerProfile({
    required this.id,
    required this.name,
    required this.config,
  });

  final String id;
  final String name;
  final SsConfig config;

  factory ServerProfile.fromJson(Map<String, Object?> json) => ServerProfile(
    id: json['id'] as String? ?? '',
    name: json['name'] as String? ?? '',
    config: SsConfig.fromJson(_stringMap(json['config'])),
  );

  Map<String, Object?> toJson() => <String, Object?>{
    'id': id,
    'name': name,
    'config': config.toJson(),
  };

  ServerProfile copyWith({String? id, String? name, SsConfig? config}) =>
      ServerProfile(
        id: id ?? this.id,
        name: name ?? this.name,
        config: config ?? this.config,
      );
}

class StunServerEndpoint {
  const StunServerEndpoint({
    required this.id,
    required this.host,
    required this.port,
    this.enabled = true,
  });

  final String id;
  final String host;
  final int port;
  final bool enabled;

  factory StunServerEndpoint.fromJson(Map<String, Object?> json) =>
      StunServerEndpoint(
        id: json['id'] as String? ?? '',
        host: json['host'] as String? ?? '',
        port: json['port'] as int? ?? 0,
        enabled: json['enabled'] as bool? ?? true,
      );

  Map<String, Object?> toJson() => <String, Object?>{
    'id': id,
    'host': host,
    'port': port,
    'enabled': enabled,
  };

  StunServerEndpoint copyWith({
    String? id,
    String? host,
    int? port,
    bool? enabled,
  }) => StunServerEndpoint(
    id: id ?? this.id,
    host: host ?? this.host,
    port: port ?? this.port,
    enabled: enabled ?? this.enabled,
  );
}

class ProfileLibrary {
  const ProfileLibrary({
    required this.schemaVersion,
    required this.profiles,
    required this.selectedProfileId,
    required this.stunServers,
  });

  final int schemaVersion;
  final List<ServerProfile> profiles;
  final String selectedProfileId;
  final List<StunServerEndpoint> stunServers;

  ServerProfile get selectedProfile =>
      profiles.firstWhere((profile) => profile.id == selectedProfileId);

  factory ProfileLibrary.fromJson(Map<String, Object?> json) => ProfileLibrary(
    schemaVersion: json['schemaVersion'] as int? ?? 1,
    profiles: _objectList(json['profiles'])
        .map((value) => ServerProfile.fromJson(_stringMap(value)))
        .toList(growable: false),
    selectedProfileId: json['selectedProfileId'] as String? ?? '',
    stunServers: _objectList(json['stunServers'])
        .map((value) => StunServerEndpoint.fromJson(_stringMap(value)))
        .toList(growable: false),
  );

  Map<String, Object?> toJson() => <String, Object?>{
    'schemaVersion': schemaVersion,
    'profiles': profiles.map((profile) => profile.toJson()).toList(),
    'selectedProfileId': selectedProfileId,
    'stunServers': stunServers.map((server) => server.toJson()).toList(),
  };
}

Map<String, Object?> _stringMap(Object? value) {
  if (value is! Map) return const <String, Object?>{};
  return <String, Object?>{
    for (final entry in value.entries)
      if (entry.key is String) entry.key as String: entry.value,
  };
}

List<Object?> _objectList(Object? value) =>
    value is List ? value.cast<Object?>() : const <Object?>[];
