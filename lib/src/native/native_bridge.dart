import 'package:flutter/services.dart';

import '../config/profile_library.dart';
import '../state/service_status.dart';

abstract interface class AppBridge {
  Future<bool> requestVpnPermission();
  Future<bool> requestNotificationPermission();
  Future<bool> start(ServerProfile profile);
  Future<bool> stop();
  Future<bool> runTcpTest();
  Future<bool> runNatTest();
  Stream<ServiceStatus> get statusStream;
}

class NativeBridge implements AppBridge {
  static const controlChannel = MethodChannel('dev.switchnat.app/control');
  static const statusChannel = EventChannel('dev.switchnat.app/status');

  @override
  Future<bool> requestVpnPermission() async {
    return await controlChannel.invokeMethod<bool>('requestVpnPermission') ??
        false;
  }

  @override
  Future<bool> requestNotificationPermission() async =>
      await controlChannel.invokeMethod<bool>(
        'requestNotificationPermission',
      ) ??
      false;

  @override
  Future<bool> start(ServerProfile profile) async {
    final config = profile.config;
    final errors = config.validate();
    if (errors.isNotEmpty) {
      throw ArgumentError(errors.join('; '));
    }
    return await controlChannel.invokeMethod<bool>('start', <String, Object?>{
          ...config.toJson(),
          'profileId': profile.id,
          'profileName': profile.name,
        }) ??
        false;
  }

  @override
  Future<bool> stop() async {
    return await controlChannel.invokeMethod<bool>('stop') ?? false;
  }

  @override
  Future<bool> runTcpTest() async =>
      await controlChannel.invokeMethod<bool>('runTcpTest') ?? false;

  @override
  Future<bool> runNatTest() async =>
      await controlChannel.invokeMethod<bool>('runNatTest') ?? false;

  @override
  Stream<ServiceStatus> get statusStream {
    return statusChannel.receiveBroadcastStream().map((event) {
      return ServiceStatus.fromJson(event as Map<Object?, Object?>);
    });
  }
}
