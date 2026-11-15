import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/config_store.dart';
import 'package:switchnat/src/config/profile_library.dart';
import 'package:switchnat/src/config/subscription_service.dart';
import 'package:switchnat/src/config/ss_config.dart';
import 'package:switchnat/src/native/native_bridge.dart';
import 'package:switchnat/src/state/app_controller.dart';
import 'package:switchnat/src/state/service_status.dart';

void main() {
  test('start uses selected profile and requests both permissions', () async {
    final bridge = FakeBridge();
    final controller = AppController(bridge: bridge, store: FakeStore(library));
    await controller.initialize();

    await controller.start();

    expect(bridge.notificationRequests, 1);
    expect(bridge.vpnRequests, 1);
    expect(bridge.startedProfileId, 'p1');
    controller.dispose();
  });

  test(
    'selecting while running records pending mismatch without restart',
    () async {
      final bridge = FakeBridge();
      final controller = AppController(
        bridge: bridge,
        store: FakeStore(library),
      );
      await controller.initialize();
      bridge.emit(<Object?, Object?>{
        'state': 'running',
        'activeProfileId': 'p1',
      });
      await Future<void>.delayed(Duration.zero);

      await controller.selectProfile('p2');

      expect(controller.hasPendingProfileChange, isTrue);
      expect(bridge.startCalls, 0);
      expect(bridge.stopCalls, 0);
      controller.dispose();
    },
  );
}

final library = ProfileLibrary(
  schemaVersion: 1,
  selectedProfileId: 'p1',
  profiles: const <ServerProfile>[
    ServerProfile(id: 'p1', name: 'Tokyo', config: validConfig),
    ServerProfile(id: 'p2', name: 'Osaka', config: validConfig),
  ],
  stunServers: const <StunServerEndpoint>[],
);

const validConfig = SsConfig(
  serverHost: '203.0.113.10',
  serverPort: 8388,
  password: 'secret',
  method: 'aes-128-gcm',
  httpProxyPort: 7890,
);

class FakeBridge implements AppBridge {
  final _statuses = StreamController<ServiceStatus>.broadcast();
  int notificationRequests = 0;
  int vpnRequests = 0;
  int startCalls = 0;
  int stopCalls = 0;
  String startedProfileId = '';

  void emit(Map<Object?, Object?> json) =>
      _statuses.add(ServiceStatus.fromJson(json));
  @override
  Stream<ServiceStatus> get statusStream => _statuses.stream;
  @override
  Future<bool> requestNotificationPermission() async {
    notificationRequests++;
    return true;
  }

  @override
  Future<bool> requestVpnPermission() async {
    vpnRequests++;
    return true;
  }

  @override
  Future<bool> start(ServerProfile profile) async {
    startCalls++;
    startedProfileId = profile.id;
    return true;
  }

  @override
  Future<bool> stop() async {
    stopCalls++;
    return true;
  }

  @override
  Future<bool> runTcpTest() async => true;
  @override
  Future<bool> runNatTest() async => true;
}

class FakeStore implements ConfigStore {
  FakeStore(this.value);
  ProfileLibrary value;
  @override
  Future<ProfileLibrary> loadLibrary() async => value;
  @override
  Future<ProfileLibrary> selectProfile(String id) async =>
      value = ProfileLibrary(
        schemaVersion: value.schemaVersion,
        profiles: value.profiles,
        selectedProfileId: id,
        stunServers: value.stunServers,
        subscriptions: value.subscriptions,
      );
  @override
  Future<ProfileLibrary> saveProfile(ServerProfile profile) async => value;
  @override
  Future<ProfileLibrary> createProfile() async => value;
  @override
  Future<ProfileLibrary> renameProfile(String id, String name) async => value;
  @override
  Future<ProfileLibrary> deleteProfile(String id) async => value;
  @override
  Future<ProfileLibrary> saveStunServers(
    List<StunServerEndpoint> servers,
  ) async => value;
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
