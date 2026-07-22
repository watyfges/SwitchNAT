import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/config_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          MethodChannelConfigStore.controlChannel,
          null,
        );
  });

  test('loads and saves config over the control channel', () async {
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(MethodChannelConfigStore.controlChannel, (
          call,
        ) async {
          calls.add(call);
          if (call.method == 'loadConfig') {
            return <String, Object?>{
              'serverHost': '203.0.113.10',
              'serverPort': 8388,
              'password': 'secret',
              'method': 'aes-128-gcm',
              'httpProxyPort': 7890,
              'udpTargets': '203.0.113.10:8123',
            };
          }
          return true;
        });

    const store = MethodChannelConfigStore();
    final loaded = await store.load();
    await store.save(loaded!);

    expect(loaded.serverHost, '203.0.113.10');
    expect(calls.map((call) => call.method), <String>[
      'loadConfig',
      'saveConfig',
    ]);
    expect(calls.last.arguments, loaded.toJson());
  });
}
