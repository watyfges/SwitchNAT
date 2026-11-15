import 'dart:async';

import 'package:switchnat/src/config/config_store.dart';
import 'package:switchnat/src/config/profile_library.dart';
import 'package:switchnat/src/config/subscription_service.dart';
import 'package:switchnat/src/native/native_bridge.dart';
import 'package:switchnat/src/state/service_status.dart';

class TestBridge implements AppBridge {
  final statuses = StreamController<ServiceStatus>.broadcast();
  bool vpnPermission = true;
  int startCalls = 0;

  void emitStatus(Map<Object?, Object?> json) {
    statuses.add(ServiceStatus.fromJson(json));
  }

  @override
  Stream<ServiceStatus> get statusStream => statuses.stream;
  @override
  Future<bool> requestNotificationPermission() async => true;
  @override
  Future<bool> requestVpnPermission() async => vpnPermission;
  @override
  Future<bool> start(ServerProfile profile) async {
    startCalls++;
    return true;
  }

  @override
  Future<bool> stop() async => true;
  @override
  Future<bool> runTcpTest() async => true;
  @override
  Future<bool> runNatTest() async => true;
}

class MemoryConfigStore implements ConfigStore {
  MemoryConfigStore(this.value);
  ProfileLibrary value;
  int createCalls = 0;
  @override
  Future<ProfileLibrary> loadLibrary() async => value;
  @override
  Future<ProfileLibrary> saveProfile(ServerProfile profile) async {
    value = ProfileLibrary(
      schemaVersion: value.schemaVersion,
      profiles: value.profiles
          .map((p) => p.id == profile.id ? profile : p)
          .toList(),
      selectedProfileId: value.selectedProfileId,
      stunServers: value.stunServers,
      subscriptions: value.subscriptions,
      themeMode: value.themeMode,
    );
    return value;
  }

  @override
  Future<ProfileLibrary> selectProfile(String id) async {
    value = ProfileLibrary(
      schemaVersion: value.schemaVersion,
      profiles: value.profiles,
      selectedProfileId: id,
      stunServers: value.stunServers,
      subscriptions: value.subscriptions,
      themeMode: value.themeMode,
    );
    return value;
  }

  @override
  Future<ProfileLibrary> createProfile() async {
    createCalls++;
    return value;
  }

  @override
  Future<ProfileLibrary> renameProfile(String id, String name) async => value;
  @override
  Future<ProfileLibrary> deleteProfile(String id) async => value;
  @override
  Future<ProfileLibrary> saveStunServers(
    List<StunServerEndpoint> servers,
  ) async {
    value = ProfileLibrary(
      schemaVersion: value.schemaVersion,
      profiles: value.profiles,
      selectedProfileId: value.selectedProfileId,
      stunServers: servers,
      subscriptions: value.subscriptions,
      themeMode: value.themeMode,
    );
    return value;
  }

  @override
  Future<ProfileLibrary> saveThemeMode(AppThemePreference themeMode) async {
    value = ProfileLibrary(
      schemaVersion: value.schemaVersion,
      profiles: value.profiles,
      selectedProfileId: value.selectedProfileId,
      stunServers: value.stunServers,
      subscriptions: value.subscriptions,
      themeMode: themeMode,
    );
    return value;
  }

  @override
  Future<ProfileLibrary> syncSubscription({
    required String id,
    required String name,
    required String url,
    required List<ImportedSubscriptionNode> nodes,
  }) async => value;

  @override
  Future<ProfileLibrary> deleteSubscription(String id) async => value;

  @override
  Future<String> loadDefaultUdpTargets() async => '';
}
