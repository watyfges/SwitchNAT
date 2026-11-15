import 'package:flutter/services.dart';

import 'profile_library.dart';
import 'subscription_service.dart';

abstract interface class ConfigStore {
  Future<ProfileLibrary> loadLibrary();
  Future<ProfileLibrary> saveProfile(ServerProfile profile);
  Future<ProfileLibrary> selectProfile(String id);
  Future<ProfileLibrary> createProfile();
  Future<ProfileLibrary> renameProfile(String id, String name);
  Future<ProfileLibrary> deleteProfile(String id);
  Future<ProfileLibrary> saveStunServers(List<StunServerEndpoint> servers);
  Future<ProfileLibrary> syncSubscription({
    required String id,
    required String name,
    required String url,
    required List<ImportedSubscriptionNode> nodes,
  });
  Future<ProfileLibrary> deleteSubscription(String id);
  Future<String> loadDefaultUdpTargets();
}

class MethodChannelConfigStore implements ConfigStore {
  const MethodChannelConfigStore([this._channel = controlChannel]);

  static const controlChannel = MethodChannel('dev.switchnat.app/control');

  final MethodChannel _channel;

  @override
  Future<ProfileLibrary> loadLibrary() => _libraryCall('loadLibrary');

  @override
  Future<ProfileLibrary> saveProfile(ServerProfile profile) =>
      _libraryCall('saveProfile', profile.toJson());

  @override
  Future<ProfileLibrary> selectProfile(String id) =>
      _libraryCall('selectProfile', <String, Object?>{'id': id});

  @override
  Future<ProfileLibrary> createProfile() => _libraryCall('createProfile');

  @override
  Future<ProfileLibrary> renameProfile(String id, String name) =>
      _libraryCall('renameProfile', <String, Object?>{'id': id, 'name': name});

  @override
  Future<ProfileLibrary> deleteProfile(String id) =>
      _libraryCall('deleteProfile', <String, Object?>{'id': id});

  @override
  Future<ProfileLibrary> saveStunServers(List<StunServerEndpoint> servers) =>
      _libraryCall('saveStunServers', <String, Object?>{
        'servers': servers.map((server) => server.toJson()).toList(),
      });

  @override
  Future<ProfileLibrary> syncSubscription({
    required String id,
    required String name,
    required String url,
    required List<ImportedSubscriptionNode> nodes,
  }) => _libraryCall('syncSubscription', <String, Object?>{
    'id': id,
    'name': name,
    'url': url,
    'nodes': nodes.map((node) => node.toJson()).toList(),
  });

  @override
  Future<ProfileLibrary> deleteSubscription(String id) =>
      _libraryCall('deleteSubscription', <String, Object?>{'id': id});

  @override
  Future<String> loadDefaultUdpTargets() async =>
      await _channel.invokeMethod<String>('loadDefaultUdpTargets') ?? '';

  Future<ProfileLibrary> _libraryCall(
    String method, [
    Map<String, Object?>? arguments,
  ]) async {
    final raw = await _channel.invokeMethod<Map<Object?, Object?>>(
      method,
      arguments,
    );
    if (raw == null) throw StateError('$method returned no profile library');
    return ProfileLibrary.fromJson(<String, Object?>{
      for (final entry in raw.entries)
        if (entry.key is String) entry.key as String: entry.value,
    });
  }
}
