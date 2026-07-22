# vivo Android Compatibility Smoke Test

Date: 2026-07-17

This is supplemental compatibility evidence. It does not replace the Xiaomi 15
Android 16 release gate in `xiaomi15-android16-verification.md`.

## APK

- Variant: debug
- Path: `build/app/outputs/flutter-apk/app-debug.apk`
- SHA-256: `8a12fd9c8edb5e512640844c8b2b58479f32e4415b09ce95a8c82fc030cd3bc0`
- Target SDK: 36
- Minimum SDK: 30

## Devices

### vivo V2458A

- Product/device: `PD2415M` / `PD2415`
- Android: 16, SDK 36
- Resolution: 1260 x 2800
- Result: APK installed and cold-launched; empty configuration validation,
  configuration persistence, status/log tabs, VPN consent callback, foreground
  service startup, and `tun0` creation were verified.
- Finding: runtime initialization originally failed with
  `VPN_START: NetworkOnMainThreadException`. DNS, socket, proxy, and TUN runtime
  initialization were moved to the tested `SwitchNAT-Startup` worker.

### vivo V2072A

- Product/device: `PD2072` / `PD2072`
- Android: 13, SDK 33
- Resolution: 1080 x 2400
- Result: full local lifecycle passed.

Verified flow:

1. Cold launch completed and the first screen rendered without overlap.
2. Empty Start showed `Server host is required` and `Password is required`.
3. Local placeholder configuration `127.0.0.1:8388` was accepted and saved.
4. Android VPN consent stayed visible and approval returned through the
   Activity Result API.
5. Approval automatically continued startup without a second Start tap.
6. Status showed `Running`, the detected hotspot gateway, and the configured
   proxy port.
7. The foreground service was active and `tun0` was up at `10.88.0.2/32` with
   MTU 1500.
8. `UDP read` increased while the VPN was active, proving the TUN read loop was
   processing packets.
9. The first Stop test exposed a lifecycle defect: Android retained a system
   binding, so `stopSelf()` alone left the service foreground and `tun0` up.
10. After the active-shutdown fix, Stop immediately removed the service,
    removed `tun0`, and changed the app status to `Stopped`.

## Automated Checks

- `flutter analyze`: no issues.
- `flutter test`: 9 tests passed after all fixes.
- `./gradlew :app:testDebugUnitTest`: successful after all fixes.
- New JVM coverage verifies permission result coordination, background startup,
  and active shutdown ordering.

## Known Limits

- The local placeholder is not a working Shadowsocks server. UDP relay and HTTP
  CONNECT egress were not validated end to end.
- No Nintendo Switch or hotspot client was connected, so learned Switch IP,
  NAT mapping, and gameplay traffic remain unverified.
- Xiaomi 15-specific behavior, including HyperOS background restrictions, still
  requires the separate release checklist.
- vivo Android 13 emitted vendor graphics and CPU-info warnings in logcat and
  `uiautomator`; no `AndroidRuntime` crash or SwitchNAT exception occurred.
