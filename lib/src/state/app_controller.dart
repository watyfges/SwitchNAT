import 'dart:async';

import 'package:flutter/foundation.dart';

import '../config/config_store.dart';
import '../config/profile_library.dart';
import '../native/native_bridge.dart';
import 'service_status.dart';

class AppController extends ChangeNotifier {
  AppController({required this.bridge, required this.store});

  final AppBridge bridge;
  final ConfigStore store;
  ProfileLibrary? library;
  ServiceStatus status = ServiceStatus.fromJson(const <Object?, Object?>{});
  bool busy = false;
  String feedback = '';
  StreamSubscription<ServiceStatus>? _statusSubscription;
  final _statusEvents = StreamController<ServiceStatus>.broadcast();
  Stream<ServiceStatus> get statusStream => _statusEvents.stream;

  bool get isRunning =>
      status.state == VpnRunState.running ||
      status.state == VpnRunState.starting;
  bool get hasPendingProfileChange =>
      isRunning &&
      status.activeProfileId.isNotEmpty &&
      library?.selectedProfileId != status.activeProfileId;

  Future<void> initialize() async {
    _statusSubscription ??= bridge.statusStream.listen((value) {
      status = value;
      _statusEvents.add(value);
      notifyListeners();
    });
    await _perform(() async {
      library = await store.loadLibrary();
    });
  }

  Future<void> start() => _perform(() async {
    final profile = library?.selectedProfile;
    if (profile == null) throw StateError('No server profile selected');
    final errors = profile.config.validate();
    if (errors.isNotEmpty) throw ArgumentError(errors.join('; '));
    await bridge.requestNotificationPermission();
    if (!await bridge.requestVpnPermission()) {
      feedback = 'VPN permission denied';
      return;
    }
    await bridge.start(profile);
    feedback = '';
  });

  Future<void> stop() => _perform(() async {
    await bridge.stop();
    feedback = '';
  });

  Future<void> selectProfile(String id) =>
      _mutate(() => store.selectProfile(id));
  Future<void> saveProfile(ServerProfile profile) =>
      _mutate(() => store.saveProfile(profile));
  Future<void> createProfile() => _mutate(store.createProfile);
  Future<void> renameProfile(String id, String name) =>
      _mutate(() => store.renameProfile(id, name));
  Future<void> deleteProfile(String id) =>
      _mutate(() => store.deleteProfile(id));
  Future<void> saveStunServers(List<StunServerEndpoint> servers) =>
      _mutate(() => store.saveStunServers(servers));
  Future<void> runTcpTest() => _perform(() async {
    await bridge.runTcpTest();
  });
  Future<void> runNatTest() => _perform(() async {
    await bridge.runNatTest();
  });

  Future<void> _mutate(Future<ProfileLibrary> Function() mutation) =>
      _perform(() async {
        library = await mutation();
      });

  Future<void> _perform(Future<void> Function() action) async {
    busy = true;
    notifyListeners();
    try {
      await action();
    } catch (error) {
      feedback = 'Operation failed: $error';
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _statusSubscription?.cancel();
    _statusEvents.close();
    super.dispose();
  }
}
