import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/config_store.dart';
import 'package:switchnat/src/config/profile_library.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          MethodChannelConfigStore.controlChannel,
          null,
        );
  });

  test('loads library and saves a profile over the control channel', () async {
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(MethodChannelConfigStore.controlChannel, (
          call,
        ) async {
          calls.add(call);
          return _libraryJson;
        });

    const store = MethodChannelConfigStore();
    final loaded = await store.loadLibrary();
    await store.saveProfile(loaded.selectedProfile);
    await store.saveThemeMode(AppThemePreference.dark);

    expect(loaded.selectedProfile.config.serverHost, '203.0.113.10');
    expect(calls.map((call) => call.method), <String>[
      'loadLibrary',
      'saveProfile',
      'saveThemeMode',
    ]);
    expect(calls[1].arguments, loaded.selectedProfile.toJson());
    expect(calls.last.arguments, <String, Object?>{'themeMode': 'dark'});
  });
}

const _libraryJson = <String, Object?>{
  'schemaVersion': 1,
  'selectedProfileId': 'p1',
  'profiles': <Object?>[
    <String, Object?>{
      'id': 'p1',
      'name': 'Tokyo',
      'config': <String, Object?>{
        'serverHost': '203.0.113.10',
        'serverPort': 8388,
        'password': 'secret',
        'method': 'aes-128-gcm',
        'httpProxyPort': 7890,
        'udpTargets': '203.0.113.10:8123',
      },
    },
  ],
  'stunServers': <Object?>[
    <String, Object?>{
      'id': 's1',
      'host': 'stun.cloudflare.com',
      'port': 3478,
      'enabled': true,
    },
  ],
};
