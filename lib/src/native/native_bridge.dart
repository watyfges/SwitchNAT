import 'package:flutter/services.dart';

import '../config/ss_config.dart';
import '../state/service_status.dart';

class NativeBridge {
  static const controlChannel = MethodChannel('dev.switchnat.app/control');
  static const statusChannel = EventChannel('dev.switchnat.app/status');

  Future<bool> requestVpnPermission() async {
    return await controlChannel.invokeMethod<bool>('requestVpnPermission') ??
        false;
  }

  Future<bool> start(SsConfig config) async {
    final errors = config.validate();
    if (errors.isNotEmpty) {
      throw ArgumentError(errors.join('; '));
    }
    return await controlChannel.invokeMethod<bool>('start', config.toJson()) ??
        false;
  }

  Future<bool> stop() async {
    return await controlChannel.invokeMethod<bool>('stop') ?? false;
  }

  Stream<ServiceStatus> get statusStream {
    return statusChannel.receiveBroadcastStream().map((event) {
      return ServiceStatus.fromJson(event as Map<Object?, Object?>);
    });
  }
}
