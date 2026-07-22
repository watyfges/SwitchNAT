import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/config/ss_config.dart';
import 'package:switchnat/src/native/native_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(NativeBridge.controlChannel, null);
  });

  test('start sends config over control channel', () async {
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(NativeBridge.controlChannel, (call) async {
          calls.add(call);
          return true;
        });

    final bridge = NativeBridge();
    await bridge.start(
      const SsConfig(
        serverHost: '203.0.113.10',
        serverPort: 8388,
        password: 'secret',
        method: 'aes-128-gcm',
        httpProxyPort: 7890,
      ),
    );

    expect(calls.single.method, 'start');
    expect(calls.single.arguments, <String, Object?>{
      'serverHost': '203.0.113.10',
      'serverPort': 8388,
      'password': 'secret',
      'method': 'aes-128-gcm',
      'httpProxyPort': 7890,
      'udpTargets': '',
    });
  });
}
