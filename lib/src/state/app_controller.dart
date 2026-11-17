import 'dart:async';

import 'package:flutter/foundation.dart';

import '../config/config_store.dart';
import '../config/profile_library.dart';
import '../config/subscription_service.dart';
import '../native/native_bridge.dart';
import 'service_status.dart';

class AppController extends ChangeNotifier {
  AppController({
    required this.bridge,
    required this.store,
    SubscriptionService? subscriptionService,
  }) : subscriptionService = subscriptionService ?? SubscriptionService();

  final AppBridge bridge;
  final ConfigStore store;
  final SubscriptionService subscriptionService;
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
  bool get isTransitioning =>
      status.state == VpnRunState.starting ||
      status.state == VpnRunState.stopping;
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
  Future<void> saveThemeMode(AppThemePreference themeMode) =>
      _mutate(() => store.saveThemeMode(themeMode));
  Future<void> syncSubscription({
    required String id,
    required String name,
    required String url,
  }) => _perform(() async {
    feedback = '';
    final result = await _downloadSubscription(url);
    library = await store.syncSubscription(
      id: id,
      name: name,
      url: url,
      nodes: result.nodes,
    );
    feedback = result.skippedCount == 0
        ? 'Imported ${result.nodes.length} nodes'
        : 'Imported ${result.nodes.length} nodes; skipped ${result.skippedCount}';
  });
  Future<void> refreshAllSubscriptions() => _perform(() async {
    feedback = '';
    final sources = List<SubscriptionSource>.from(
      (library?.subscriptions ?? const <SubscriptionSource>[]).where(
        (source) => source.enabled,
      ),
    );
    var imported = 0;
    for (final source in sources) {
      final result = await _downloadSubscription(source.url);
      library = await store.syncSubscription(
        id: source.id,
        name: source.name,
        url: source.url,
        nodes: result.nodes,
      );
      imported += result.nodes.length;
    }
    feedback = 'Updated ${sources.length} subscriptions, $imported nodes';
  });
  Future<void> setSubscriptionEnabled(String id, bool enabled) =>
      _mutate(() => store.setSubscriptionEnabled(id, enabled));
  Future<void> deleteSubscription(String id) => _perform(() async {
    feedback = '';
    library = await store.deleteSubscription(id);
    feedback = 'Subscription deleted';
  });
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

  Future<SubscriptionImportResult> _downloadSubscription(String url) async {
    final profile = library?.selectedProfile;
    final targets = profile?.config.udpTargets.trim().isNotEmpty == true
        ? profile!.config.udpTargets
        : await store.loadDefaultUdpTargets();
    return subscriptionService.download(
      url: url,
      udpTargets: targets,
      httpProxyPort: profile?.config.httpProxyPort ?? 7890,
    );
  }

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
