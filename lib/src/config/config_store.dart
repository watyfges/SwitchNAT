import 'package:flutter/services.dart';

import 'ss_config.dart';

abstract interface class ConfigStore {
  Future<SsConfig?> load();
  Future<void> save(SsConfig config);
  Future<void> clear();
}

class MethodChannelConfigStore implements ConfigStore {
  const MethodChannelConfigStore([this._channel = controlChannel]);

  static const controlChannel = MethodChannel('dev.switchnat.app/control');

  final MethodChannel _channel;

  @override
  Future<SsConfig?> load() async {
    final raw = await _channel.invokeMethod<Map<Object?, Object?>>(
      'loadConfig',
    );
    if (raw == null) return null;
    final json = <String, Object?>{
      for (final entry in raw.entries)
        if (entry.key is String) entry.key! as String: entry.value,
    };
    return SsConfig.fromJson(json);
  }

  @override
  Future<void> save(SsConfig config) async {
    await _channel.invokeMethod<bool>('saveConfig', config.toJson());
  }

  @override
  Future<void> clear() async {
    await _channel.invokeMethod<bool>('clearConfig');
  }
}
