# SwitchNAT

English | [简体中文](README.md)

> An experimental, unofficial, rootless Android hotspot traffic relay for Nintendo Switch.

SwitchNAT relays TCP and UDP traffic from a Nintendo Switch connected to an Android phone hotspot through a user-provided Shadowsocks server. It is not a general-purpose VPN. The project explores Switch online play, NAT mapping, and dynamic P2P endpoints within the permissions available to a regular Android app.

## Overview

SwitchNAT uses separate paths for TCP and UDP:

- TCP: the Switch uses an HTTP proxy on the phone's hotspot gateway, and the app relays the connection through Shadowsocks TCP.
- UDP: Android VpnService delivers configured destination addresses to local sockets bound by the app, which relays each payload through Shadowsocks UDP.

Fixed UDP targets bootstrap the initial traffic path. When a response comes from an unconfigured player endpoint, the app creates a local UDP alias reachable by the Switch and reuses the existing Shadowsocks UDP session for two-way traffic. This supports P2P scenarios in which endpoints change as players join, leave, or match again.

The project has been technically validated on specific devices and networks. Results can differ across Android vendors, OS versions, hotspot implementations, carriers, Shadowsocks servers, and games.

## Features

- Rootless Android VpnService.
- HTTP CONNECT and absolute-form HTTP proxying over Shadowsocks TCP.
- Fixed IPv4/CIDR UDP targets over Shadowsocks UDP relay.
- Dynamic P2P endpoint alias learning, reuse, timeout, and capacity eviction.
- UDP relay-session reuse keyed by the Switch source IP:port, reducing public-port changes when destinations change.
- Multiple Shadowsocks profiles with create, rename, select, save, and delete operations.
- aes-128-gcm, aes-256-gcm, and chacha20-ietf-poly1305.
- Automatic TCP connectivity and STUN NAT behavior tests after service startup, with manual retesting.
- A global, customizable STUN server list.
- Switch presence, hotspot gateway, proxy address, UDP/TCP counters, dynamic flows, peer aliases, and recent errors.
- Android foreground-service notification and Live Updates metadata; compatible systems may render it as a live activity or an equivalent dynamic-island surface.

## How It Works

~~~text
Nintendo Switch
  ├─ TCP → hotspot HTTP proxy → Shadowsocks TCP → destination
  └─ UDP → Android VpnService/local endpoint → Shadowsocks UDP → fixed server or P2P peer
                                                        ↑
                                  dynamic peers return through a local UDP alias
                                  backed by the same relay session
~~~

The STUN result describes UDP mapping and filtering behavior at the **Shadowsocks egress**, such as Full Cone, Restricted Cone, Port-Restricted Cone, or Symmetric. It is not a direct measurement of the Nintendo Switch NAT grade and cannot by itself guarantee that a game will connect.

Dynamic learning does not completely replace bootstrap UDP targets. The app must first receive a response through a fixed target and an existing relay session before it can learn an unknown player endpoint.

See the [English technical design](docs/TECHNICAL_DESIGN_EN.md) or [Chinese technical design](docs/TECHNICAL_DESIGN.md) for details.

## Requirements

- Android 11 (API 30) or later.
- An Android phone that supports hotspot mode and standard VpnService.
- A Nintendo Switch connected to that hotspot.
- A user-provided Shadowsocks server with working TCP and UDP relay.
- Build environment:
  - Flutter stable with a Dart SDK satisfying ^3.12.2 in pubspec.yaml.
  - JDK 17.
  - Android SDK.

Vendors may handle hotspot traffic, VPN address binding, notification Live Updates, and background execution differently. The repository includes Xiaomi Android 16 and vivo Android compatibility notes, but they are not universal compatibility guarantees.

## Build and Test

~~~bash
flutter pub get
flutter test
flutter analyze
flutter build apk --debug
~~~

Build split release APKs:

~~~bash
flutter build apk --release --split-per-abi
~~~

The current release build still uses the debug signing configuration and is intended only for personal testing. Configure a dedicated release keystore before distribution, and never commit the keystore or its passwords.

Run Android JVM tests:

~~~bash
cd android
./gradlew :app:testDebugUnitTest
~~~

## Usage

1. On the Config page, create or select a profile and enter the Shadowsocks host, port, password, encryption method, and HTTP proxy port.
2. Review the bundled UDP targets. They bootstrap fixed service traffic and can be adjusted from packet-capture evidence.
3. Edit the global STUN server list if necessary, then save the profile.
4. Enable the phone hotspot and connect the Switch.
5. In the Switch network settings, set the HTTP proxy host to the hotspot gateway shown by the app and use the profile's proxy port.
6. Start SwitchNAT from the Home page and grant VPN and notification permissions.
7. Wait for the automatic TCP/STUN tests, or tap the corresponding status cards to run them again.
8. Run the Switch connection test or enter a game, then use Status and Logs to inspect UDP flows, peer aliases, errors, and recent packet metadata.

Selecting another profile while the service is running does not silently replace the active session. Stop and restart the service to apply it.

## Current Limitations

- Android only; no iOS implementation.
- IPv4 only.
- UDP targets must be IPv4 literals or constrained CIDRs; hostnames are not supported.
- CIDR prefixes are limited to /21 through /32, with at most 4,096 expanded endpoints.
- Each Switch UDP flow supports up to 128 dynamic aliases and the proxy supports 512 globally; the default idle timeout is five minutes.
- TCP requires a manually configured HTTP proxy on the Switch and is not transparent TCP interception.
- Dynamic aliases require an existing Shadowsocks UDP relay session and cannot discover traffic that has never reached the app.
- STUN servers must support the attributes needed by the behavior test; a basic Binding-only response can produce a partial result.
- Android vendors may restrict hotspot-to-VPN routing, local address binding, foreground services, or Live Updates.
- NAT A, matchmaking success, latency, and stability depend on the server, carrier, game protocol, and other external conditions.

## Security and Privacy

- Shadowsocks profiles are stored in app-private SharedPreferences, but the app does not currently add hardware-backed encryption.
- The app does not provide or upload public proxy nodes; users manage their own server and credentials.
- Diagnostic packet samples contain only timestamp, protocol, source/destination, direction, and outcome metadata. They do not store payloads.
- Remove passwords, server addresses, device identifiers, public IP addresses, and other personal data before publishing issues, logs, or captures.

## Technical Documentation

- [Technical Design (English)](docs/TECHNICAL_DESIGN_EN.md)
- [技术设计（中文）](docs/TECHNICAL_DESIGN.md)
- [Xiaomi Android 16 verification template](docs/testing/xiaomi15-android16-verification.md)
- [vivo Android compatibility notes](docs/testing/vivo-android-compatibility-smoke.md)

## Archived Version

The first device-tested implementation of dynamic UDP endpoint aliases is preserved on:

[codex/archive-initial-dynamic-udp](https://github.com/watyfges/SwitchNAT/tree/codex/archive-initial-dynamic-udp)

This branch is a historical snapshot, not the currently recommended version.

## Disclaimer

SwitchNAT is an independent, unofficial technical exploration project. It is not affiliated with, sponsored by, approved by, or endorsed by Nintendo, the Shadowsocks project, Google, Xiaomi, vivo, or any other device vendor. Nintendo Switch and other trademarks belong to their respective owners.

Users are responsible for complying with applicable laws, network-provider terms, game-service terms, and their Shadowsocks provider's rules. The software is provided without warranty as described by the license. No particular NAT grade, game compatibility, network quality, or continued availability is guaranteed.

## License

This project is licensed under the [GNU General Public License v3.0 only](LICENSE) (SPDX: GPL-3.0-only).

You may use, study, modify, and distribute the project, including for a fee. If you distribute a modified version or object code, you must follow GPLv3 requirements for Corresponding Source, notices, and the same license terms. This summary does not replace the terms in LICENSE.

Copyright (C) 2026 watyfges
