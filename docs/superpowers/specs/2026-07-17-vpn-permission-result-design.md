# VPN Permission Result Handling

## Problem

`NativeBridge` launches the intent returned by `VpnService.prepare()` with
`startActivity()` and immediately reports `false` to Flutter. On the vivo
V2458A running Android 16, the system VPN confirmation activity exits without
granting `ACTIVATE_VPN` because the app does not launch it for a result.

## Scope

Change only the Android permission request path and the corresponding Flutter
feedback. Do not change VPN service startup, TUN configuration, Shadowsocks,
HTTP proxy, NAT, or packet handling.

## Design

`MainActivity` registers an Activity Result launcher using
`ActivityResultContracts.StartActivityForResult`. A small
`VpnPermissionCoordinator` owns at most one pending callback:

- If `VpnService.prepare()` returns `null`, complete immediately with `true`.
- Otherwise retain the callback and launch the returned system intent.
- Complete with `true` only for `Activity.RESULT_OK`; complete with `false` for
  cancellation or rejection.
- Reject a second request while one result is pending so no method-channel
  result can be leaked or overwritten.

`NativeBridge` delegates `requestVpnPermission` to the coordinator. The existing
Flutter start flow saves the configuration, awaits permission, and starts the
VPN automatically after approval. A denied request displays a denial message
and does not start the service.

## Verification

Android JVM tests cover already-prepared, approved, denied, and overlapping
requests. Existing Flutter and Android tests must remain green. The rebuilt APK
is then installed on the vivo Android 16 device and checked for:

1. A persistent system VPN confirmation dialog.
2. Automatic VPN startup after approval without a second tap.
3. Foreground service, TUN status, and in-app running status.
4. Clean stop with no remaining service.

Real Shadowsocks relay traffic remains outside this test because no working
server credentials are available.
