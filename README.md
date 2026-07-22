# SwitchNAT

SwitchNAT is an Android Flutter app that exposes a local HTTP proxy for Nintendo Switch TCP traffic and forwards selected UDP endpoints through a Shadowsocks server without requiring root.

## Android Builds

Debug APKs are large because they include debug/runtime artifacts and multiple ABIs. Use release builds for distribution or phone testing.

Build a universal release APK:

```bash
.toolchains/flutter/bin/flutter build apk --release
```

Build per-ABI release APKs:

```bash
.toolchains/flutter/bin/flutter build apk --release --split-per-abi
```

For modern Android phones, install the ARM64 artifact:

```bash
.toolchains/android-sdk/platform-tools/adb install -r build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
```

Current reference sizes from this workspace:

- Debug APK: about 158 MB
- Universal release APK: about 47 MB
- ARM64 split release APK: about 17 MB
