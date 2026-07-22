# SwitchNAT Android App Design

Date: 2026-07-16
Status: Draft for user review

## Goal

Build a Flutter-based Android app that improves Nintendo Switch online play when the Switch connects through an Android phone hotspot and the carrier gives a poor NAT type such as C.

The first implementation targets Xiaomi 15 on Android 16 and keeps compatibility down to Android 11. The MVP must use a standard Shadowsocks server that already supports UDP relay.

The product goal for the MVP is:

- Switch connection test NAT improves from C to A when acceleration is enabled.
- Switch TCP traffic, including store and download traffic, improves when the Switch manually configures the app's local HTTP proxy.
- The app exposes enough diagnostics to tell whether UDP failed because hotspot traffic did not enter the VPN, Shadowsocks had no UDP response, or packets were not injected back correctly.

## Core Assumption

The largest technical uncertainty is whether a non-root Android app can receive hotspot client UDP packets through `VpnService` on the target phone.

Android `VpnService.Builder` can create a VPN interface, add routes, and expose routed IP packets through a file descriptor. However, Android does not guarantee that every device routes hotspot-tethered client traffic through a normal app VPN. This must be verified on Xiaomi 15 / Android 16 before treating the UDP path as reliable.

References:

- Android `VpnService.Builder`: https://developer.android.com/reference/android/net/VpnService.Builder
- Apple `NEPacketTunnelProvider`, for future iOS investigation: https://developer.apple.com/documentation/networkextension/nepackettunnelprovider

## Scope

In scope for the MVP:

- Flutter UI for configuration, start/stop, status, and logs.
- Android native `VpnService` foreground service.
- IPv4 UDP transparent forwarding from TUN to Shadowsocks UDP relay.
- Local HTTP CONNECT proxy for Switch manual proxy settings.
- Shadowsocks TCP and UDP outbound support.
- Runtime diagnostics for packet counts, mapping state, and failure points.

Out of scope for the MVP:

- Root, adb-assisted routing, or privileged system APIs.
- Transparent TCP interception.
- IPv6 forwarding.
- Rule subscriptions, QR import, multiple nodes, speed testing, game profiles, and server selection.
- iOS implementation. The architecture leaves a platform boundary for a future iOS native network extension, but Android is implemented first.

## Architecture

Flutter is the product shell. Android networking is native.

Modules:

- `app_flutter`: Flutter screens, Shadowsocks configuration form, service controls, state display, logs, and persisted settings.
- `android_vpn_service`: Kotlin foreground service, VPN authorization, TUN setup, hotspot gateway detection, lifecycle management, and Flutter channel bridge.
- `packet_core`: packet parser and writer for IPv4, UDP, minimal ICMP, checksums, NAT mapping, TUN reads, and TUN writes. Kotlin is acceptable for the MVP; C++ can replace hot paths later if needed.
- `ss_core`: Shadowsocks AEAD TCP and UDP client. This should reuse a mature library where possible and must not reimplement cryptography from scratch.
- `http_proxy`: local HTTP CONNECT proxy listening on the hotspot gateway address and a fixed or user-configurable port such as `7890`.
- `diagnostics`: structured counters, recent packet samples, current mapping count, error categories, and exported logs.

Flutter talks to Android through:

- `MethodChannel` for start, stop, update config, and request VPN permission.
- `EventChannel` or equivalent stream for live status, counters, and logs.

## Android Data Flow

### Service Startup

1. User enters Shadowsocks host, port, password, and encryption method.
2. User taps start.
3. Flutter asks Android native code to prepare `VpnService`.
4. Android requests VPN permission if needed.
5. The native foreground service starts with a persistent notification.
6. The service creates a TUN interface with an IPv4 address, route, DNS, and MTU.
7. The service starts the UDP packet loop, Shadowsocks outbound manager, and local HTTP proxy.

The app's own outbound sockets to the Shadowsocks server must be protected with `VpnService.protect(...)` to avoid routing loops.

### UDP Transparent Path

Switch does not configure a proxy for UDP.

1. Switch sends UDP through the phone hotspot.
2. Android may route that hotspot packet into the app TUN.
3. The packet loop reads an IPv4 packet from TUN.
4. `packet_core` accepts only UDP packets from the hotspot client subnet or a learned Switch IP.
5. `packet_core` extracts source IP, source port, destination IP, destination port, and payload.
6. The NAT table records a mapping from the Switch-side tuple to the original destination tuple.
7. `ss_core` wraps the UDP payload using Shadowsocks UDP relay format and sends it to the configured Shadowsocks server.
8. The Shadowsocks server relays the packet to the real game or Nintendo endpoint.
9. A UDP response returns from the Shadowsocks server.
10. `ss_core` unwraps the response and provides the original remote source address and payload.
11. `packet_core` builds an IPv4 UDP response packet whose source is the original remote endpoint and whose destination is the Switch IP and port.
12. The response packet is written back to TUN.

The Switch should see this as direct UDP communication with the target server, while the carrier-facing path uses the Shadowsocks server as the public endpoint. This is the path expected to improve NAT type.

### TCP Explicit Proxy Path

Switch manually configures a proxy server in system network settings.

1. The app shows the proxy address, typically the phone hotspot gateway IP and a port such as `7890`.
2. Switch sends HTTP proxy and HTTPS CONNECT requests to the app.
3. `http_proxy` parses the request and opens a protected Shadowsocks TCP outbound connection.
4. Bytes are relayed in both directions until close or timeout.

This path is intended for store, download, and connection-test IP/speed behavior. The MVP does not transparently intercept TCP from TUN.

## Diagnostics

The status page must show:

- VPN state: stopped, permission needed, starting, running, stopping, or failed.
- Hotspot gateway IP and suggested Switch proxy address.
- Learned Switch IP candidates.
- UDP packets read from TUN.
- UDP packets accepted as Switch traffic.
- UDP packets sent to Shadowsocks.
- UDP packets received from Shadowsocks.
- UDP packets written back to TUN.
- Active UDP mapping count.
- TCP proxy connection count.
- Last error category and short message.

Recent packet samples should include timestamp, protocol, source, destination, direction, and outcome. Payloads are not shown in the UI.

## Error Handling

VPN startup failures:

- If VPN permission is missing, prompt the system VPN permission flow.
- If another VPN is active, show that Android allows only one active VPN profile for normal apps.
- If TUN creation fails, show the native exception message and selected route configuration.

Hotspot UDP capture failures:

- If no UDP packets from the Switch are seen during a connection test, show "未捕获热点 UDP".
- Record current hotspot gateway IP, candidate subnet, and all recent source IPs observed in TUN.
- This indicates the device may not route hotspot clients through a normal `VpnService`.

Shadowsocks UDP failures:

- If packets are sent but no response returns, separate local send success from remote no-response.
- If response decryption fails, record decrypt failure count.
- If a response has no matching NAT mapping, record mapping miss count.

TUN injection failures:

- If response packets are built but TUN writes fail, record write exceptions.
- If writes succeed but Switch NAT remains C, keep packet samples for source/destination verification.

TCP proxy failures:

- If no TCP connections arrive after the user claims Switch proxy is configured, show the exact proxy address the Switch should use.
- If TCP CONNECT parsing fails, log request-line parse errors.
- If Shadowsocks TCP connection fails, show outbound connection errors separately from local proxy errors.

## MVP Screens

### Configuration

Fields:

- Server host
- Server port
- Password
- Encryption method
- Local HTTP proxy port

Actions:

- Save
- Start
- Stop

### Status

Content:

- Current service state
- VPN permission state
- Hotspot gateway IP
- Switch proxy address
- Learned Switch IP
- UDP and TCP counters
- Active UDP mappings

### Logs

Content:

- Filtered operational logs
- Last packet samples
- Export log action

## Test Plan

1. Start the app with hotspot off. Verify VPN permission, foreground service startup, TUN creation, and no routing loop for app outbound traffic.
2. Start phone hotspot. Connect Switch without configuring proxy. Run Switch connection test. Check whether the app captures UDP from the Switch IP.
3. If UDP is captured, verify Shadowsocks UDP send, Shadowsocks UDP response, TUN write-back, and Switch NAT result.
4. Configure Switch HTTP proxy using the app-displayed gateway IP and port. Run connection test and verify IP/speed behavior changes.
5. Open Nintendo eShop or download test content. Verify TCP proxy connection count and throughput.
6. Test 1-2 actual online games. Record NAT type, matchmaking success, active UDP mappings, timeout behavior, and reconnect behavior.
7. Repeat on Android 11-16 devices when available. Xiaomi 15 / Android 16 is the first target and release gate.

## Success Criteria

The MVP is successful on Xiaomi 15 / Android 16 if:

- The app captures UDP packets from the Switch while the Switch is connected to the phone hotspot and no UDP proxy is configured on the Switch.
- Captured UDP is relayed through the configured Shadowsocks server and responses are injected back to the Switch.
- Switch NAT test changes from C to A or otherwise materially improves compared with the baseline carrier hotspot.
- Switch manual HTTP proxy works through the app and improves TCP-visible IP/speed/store behavior.
- Diagnostics can identify the failing layer when any of the above does not work.

## Future Work

After the Android MVP is verified:

- Improve performance by moving packet hot paths to native C++ if Kotlin is insufficient.
- Add IPv6 handling if Switch or carrier behavior requires it.
- Add UDP keepalive and adaptive mapping timeout tuning for specific games.
- Add node import and multiple profile management.
- Investigate iOS with `NEPacketTunnelProvider`, keeping Flutter UI and configuration model reusable while replacing the native network engine.
