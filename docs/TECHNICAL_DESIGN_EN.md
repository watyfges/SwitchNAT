# SwitchNAT Technical Design

English | [简体中文](TECHNICAL_DESIGN.md)

This document describes the current <code>master</code> implementation boundary. It explains how the code works and does not promise identical results across devices, networks, or games.

## 1. Goals and Non-Goals

SwitchNAT provides two node-egress paths for a Nintendo Switch connected to an Android hotspot, without root or privileged system APIs. A node can use Shadowsocks or VLESS + Reality:

- A manually configured HTTP proxy for TCP.
- Android VpnService, local UDP endpoints, and dynamic aliases for selected UDP traffic.

The design focuses on keeping one UDP relay association for a Switch source port and continuing that association when P2P player endpoints change.

Current non-goals include:

- A general-purpose whole-device VPN.
- Transparent TCP interception from TUN.
- IPv6.
- Automatic discovery of every game flow.
- A bundled node server or public-node distribution.
- Guaranteed NAT A, matchmaking success, or vendor compatibility.

## 2. System Architecture

Flutter owns UI, configuration editing, and status presentation. Kotlin owns Android permissions, persistence, VPN lifecycle, and packet relay.

~~~mermaid
flowchart LR
    NS["Nintendo Switch"]
    HOTSPOT["Android hotspot"]
    HTTP["HttpConnectProxy"]
    VPN["VpnService + fixed UDP endpoints"]
    ALIAS["Dynamic UDP aliases"]
    SSTCP["SS TCP / VLESS Reality"]
    SSUDP["SS UDP / VLESS Reality + XUDP session"]
    SERVER["Proxy server"]
    DEST["Nintendo services / P2P peers"]

    NS --> HOTSPOT
    HOTSPOT -->|"manual HTTP proxy"| HTTP
    HTTP --> SSTCP --> SERVER --> DEST
    HOTSPOT -->|"selected UDP targets"| VPN
    VPN --> SSUDP --> SERVER
    SERVER --> DEST
    DEST --> SERVER --> SSUDP
    SSUDP -->|"fixed response"| VPN --> NS
    SSUDP -->|"unknown peer response"| ALIAS --> NS
    NS --> ALIAS --> SSUDP
~~~

Primary boundaries:

- Flutter: <code>AppController</code>, four screens, configuration models, and status models.
- Platform bridge: <code>NativeBridge</code> and <code>MethodChannelConfigStore</code>.
- Android service: <code>SwitchNatVpnService</code>.
- TCP: <code>HttpConnectProxy</code>, <code>ShadowsocksTcpClient</code>, or a local Xray SOCKS5 outbound.
- UDP: <code>LocalUdpEndpointProxy</code>, <code>UdpPeerAliasManager</code>, <code>ShadowsocksUdpClient</code>, or an Xray SOCKS5 UDP association.
- Diagnostics: <code>Diagnostics</code>, <code>ConnectionDiagnosticsCoordinator</code>, the TCP probe, and the STUN probe.

## 3. Configuration and Control Flow

Flutter uses two channels:

- <code>dev.switchnat.app/control</code> requests VPN/notification permissions, loads or saves configuration, starts or stops the service, and runs diagnostics.
- <code>dev.switchnat.app/status</code> continuously publishes <code>DiagnosticsSnapshot</code>.

<code>ProfileRepository</code> stores a schema-version-4 profile library in app-private SharedPreferences and can read versions 1 through 3. The library contains:

- One or more Shadowsocks/VLESS Reality profiles.
- The selected profile ID.
- A global shared STUN server list.
- Multiple subscription sources and the source/stable-node-key relationship for imported profiles.
- A global System, Light, or Dark theme preference; older schemas migrate to System.

A profile contains the protocol, host, port, HTTP proxy port, UDP targets, and protocol-specific fields. Shadowsocks uses a password and AEAD method. VLESS Reality uses UUID, flow, SNI, Reality public key, short ID, fingerprint, and spider path. First launch creates a blank profile with bundled UDP targets and four default STUN endpoints. Legacy single-node settings are migrated into the profile library.

<code>SubscriptionService</code> downloads and parses Clash YAML in Flutter. It accepts HTTP(S) only, uses a 20-second timeout, and limits responses to 2 MiB. It currently supports the listed Shadowsocks AEAD ciphers and TCP/RAW VLESS + Reality. A SHA-256 key derived from protocol identity fields gives each imported node a stable identity. Android replaces a subscription's cached profiles only after the download and full parse succeed, so failures retain the previous cache. Refresh preserves matching profile IDs and selection; the first import automatically selects a subscription node when the current profile is blank.

The active profile ID is recorded in the diagnostic session. Selecting another profile while the service is running marks a pending change in the UI but does not replace live sockets. The service must be stopped and restarted.

## 4. VPN and Service Lifecycle

Startup order:

1. Flutter validates the selected profile and requests notification and VPN permissions.
2. Android starts <code>SwitchNatVpnService</code> as a foreground service and enters <code>starting</code>.
3. The service resolves the node host, detects the hotspot gateway, and expands UDP targets.
4. VpnService Builder adds <code>10.88.0.2/32</code>, DNS <code>1.1.1.1</code>, and MTU 1500.
5. Every fixed UDP target IP is registered with <code>addAddress(ip, 32)</code> and <code>addRoute(ip, 32)</code>.
6. After establishing TUN, the service starts the HTTP proxy and fixed UDP endpoint proxy.
7. State becomes <code>running</code>, and TCP and STUN probes start in parallel.

The current implementation does not read or parse raw IPv4 packets from TUN. TUN registers destination addresses and routes so Android delivers target UDP traffic to DatagramSockets bound by the app.

Stop, permission revocation, startup failure, and service destruction close the diagnostic executor, HTTP listener, UDP flows, alias sockets, TUN descriptor, and notification-update thread. Stopping clears the active profile session and removes the foreground notification.

## 5. TCP Relay Path

<code>HttpConnectProxy</code> listens on <code>0.0.0.0:httpProxyPort</code> and supports:

- <code>CONNECT host:port HTTP/1.1</code>.
- Absolute-form HTTP requests such as <code>GET http://example.test/path HTTP/1.1</code>.

It resolves the real target and selects the actual outbound through <code>TcpOutboundFactory</code>. Shadowsocks profiles use <code>ShadowsocksTcpClient</code> and a Shadowsocks destination header; VLESS + Reality profiles use <code>Socks5TcpClient</code> against the local Xray SOCKS5 inbound. The HTTP proxy then copies bytes in both directions. The client-header limit is 16 KiB and the outbound connect timeout is 10 seconds.

Direct node sockets call <code>VpnService.protect()</code> before connecting so proxy traffic does not loop back into the VPN. Xray exposes its SOCKS5 inbound only on loopback and owns the VLESS + Reality outbound. Connection counts and failure categories are published through diagnostics.

## 6. Fixed UDP Endpoint Relay

UDP target input accepts whitespace, commas, or newlines:

~~~text
IPv4:port
IPv4/prefix:port
~~~

CIDR prefixes are limited to /21 through /32. Expanded expressions are deduplicated and limited to 4096 endpoints. Hosts must be IPv4 literals.

For each fixed endpoint:

1. The VPN registers its IP as a /32 address and route.
2. <code>DatagramLocalUdpSocketFactory</code> binds <code>targetIp:targetPort</code>.
3. A Switch payload enters a flow keyed by the Switch source <code>IP:port</code>.
4. The flow sends the payload to the real target through the selected node's <code>UdpRelaySession</code>, implemented as either Shadowsocks UDP packets or an Xray SOCKS5 UDP association.
5. If the response source matches a configured endpoint, the matching fixed socket writes it back to the Switch.

Fixed targets are the bootstrap set. Removing all of them leaves the service without an address that can capture initial UDP traffic, so dynamic learning has no relay session to extend.

## 7. Dynamic P2P Aliases

A response from <code>UdpRelaySession</code> includes its remote source endpoint. When that endpoint is not in the fixed target set, <code>UdpPeerAliasManager</code> uses this key:

~~~text
(Switch flow IP:port, remote peer IP:port)
~~~

Creation flow:

1. Bind an OS-assigned UDP port on the detected hotspot-gateway address.
2. Connect the alias socket to the current Switch endpoint.
3. Send the remote payload from the alias to the Switch. The Switch sees the hotspot gateway and temporary port as the source.
4. When the Switch replies to the alias, use the original flow's <code>UdpRelaySender</code> to send the payload to the real remote peer.

The same key reuses one alias. Closing a flow closes all aliases that belong to it. Default idle timeout is 300 seconds and the sweeper runs every 30 seconds. Limits are 128 aliases per flow and 512 globally; the oldest alias is evicted at capacity.

## 8. UDP Relay Sessions, Node Protocols, and NAT Behavior

<code>LocalUdpEndpointProxy.flows</code> is keyed only by the Switch sender <code>IP:port</code>; the destination endpoint is not part of the key. One Switch source port therefore reuses one <code>UdpRelaySession</code> and one local UDP socket across fixed servers and dynamic peers. A Shadowsocks profile creates <code>ShadowsocksUdpClient</code>; a VLESS + Reality profile creates <code>Socks5UdpClient</code> against the local Xray inbound.

This reduces node-egress mapping changes when destinations change and more closely matches endpoint-independent mapping behavior. It is an important condition in the observed NAT improvement, but the final public mapping still belongs to the node server network. Session reuse alone does not guarantee Full Cone or Nintendo NAT A.

Both Shadowsocks UDP and TCP sockets call <code>VpnService.protect()</code>. Supported AEAD methods are:

- aes-128-gcm
- aes-256-gcm
- chacha20-ietf-poly1305

VLESS + Reality nodes use a pinned AndroidLibXrayLite/Xray Core outbound. <code>XrayCoreRuntime</code> starts a SOCKS5 inbound bound only to <code>127.0.0.1</code>. <code>Socks5TcpClient</code> uses CONNECT and <code>Socks5UdpClient</code> uses UDP ASSOCIATE. Reality uses RAW transport, SNI, fingerprint, short ID, and the current Xray <code>password</code> field (the Reality public key shown by the UI). The VLESS outbound explicitly sets <code>packetEncoding=xudp</code>; each Switch flow still owns a separate SOCKS UDP association, and XUDP Mux is currently disabled.

The Xray AAR is not committed to Git. <code>scripts/fetch_xray_core.sh</code> downloads a pinned official AndroidLibXrayLite release and verifies its SHA-256 checksum. Both local builds and GitHub Actions run it before Gradle compilation.

## 9. STUN Diagnostics

After the service reaches running, <code>ConnectionDiagnosticsCoordinator</code> uses two workers to run the TCP and NAT probes in parallel. Tapping a Home status card cancels the previous probe of that type and starts a new generation, preventing a stale result from replacing a newer one.

The STUN probe sends Binding requests through the selected node's <code>UdpRelaySession</code> to enabled global STUN endpoints, so both Shadowsocks and VLESS + Reality test their actual egress. It attempts the modern format first, falls back to the legacy format when necessary, and, when the server supports OTHER-ADDRESS/CHANGE-REQUEST behavior, compares:

- Whether the mapping changes with the STUN destination.
- Whether filtering accepts changed IP/port or changed-port responses.

The UI always exposes the original English Full Cone, Restricted Cone, Port-Restricted Cone, and Symmetric labels, together with mapped address, mapping behavior, filtering behavior, RTT, and the STUN server that responded.

A basic Binding response without enough attributes for behavior classification produces a partial result. No response from all servers produces unavailable.

The result describes **UDP mapping/filtering behavior at the selected node egress**. It is not the carrier NAT on the phone and is not the NAT grade from the Switch system connection test.

## 10. Android Live Updates

VpnService remains active through an ongoing foreground notification. The NotificationCompat implementation:

- Sets service category, chronometer, and session start time.
- Attempts <code>setRequestPromotedOngoing(true)</code>.
- Supplies <code>shortCriticalText</code> with elapsed acceleration minutes.
- Shows the profile, Switch presence, learned Switch IP, UDP NAT summary, and TCP latency.
- Refreshes every second from the latest <code>DiagnosticsSnapshot</code>.

Startup shows Connecting. Stop, failure, or permission revocation stops updates and removes the notification. Whether the system renders it as a Live Update, a dynamic-island surface, or a normal foreground notification depends on Android and the vendor. Promotion failure never stops traffic forwarding.

## 11. Diagnostics and Error Handling

<code>Diagnostics</code> stores:

- VPN state, active profile, server endpoint, and session start time.
- Hotspot gateway, Switch proxy address, learned Switch IP, and presence.
- TCP/STUN test state and results.
- UDP read, accepted, sent, received, and written counters.
- Active mappings, UDP flows, peer aliases, and alias creation/expiry counters.
- TCP connections, last error, and recent packet samples.

Switch presence is connected when the last observed UDP activity is within 60 seconds, inactive afterward, and waiting before any activity. At most 50 samples are retained. A sample contains timestamp, protocol, source, destination, direction, and outcome, never payload.

Errors are categorized at boundaries, including BAD_CONFIG, VPN_START, LOCAL_UDP_SOCKET, SS_UDP_SEND, SS_UDP_RECEIVE, UDP_ALIAS_BIND, HTTP_PROXY_ACCEPT, and HTTP_PROXY_OUTBOUND. Startup failure closes partially created resources and sets state to failed.

## 12. Concurrency and Resource Management

The network layer uses bounded responsibilities across threads and concurrent containers:

- One local receive thread per fixed UDP endpoint.
- One relay-session receive thread per Switch UDP flow.
- One Switch-reply receive thread per dynamic alias.
- One alias sweeper thread.
- An HTTP accept thread and relay workers per TCP connection.
- Two diagnostic workers.
- One daemon thread for per-second notification updates.

The flow map is a ConcurrentHashMap. The alias map is accessed under a lock. Sends on one flow are serialized so one UDP relay session is not written concurrently. Close operations are guarded for idempotence and avoid interrupting the current thread as if it were a peer worker.

## 13. Security and Privacy Boundaries

- No root, persistent ADB permission, or privileged system API is required.
- App outbound sockets must be protected against VPN routing loops.
- Profiles and subscription URLs are stored in MODE_PRIVATE SharedPreferences without additional hardware-backed encryption; subscription lists render only the URL host.
- The Android application sets <code>allowBackup=false</code>.
- Packet samples omit payloads, but endpoint and IP metadata may still be sensitive.
- The project contains no user node, credential, signing material, or telemetry upload.
- Logs, issues, and captures still require manual redaction before publication.

## 14. Test Strategy

Flutter tests cover configuration models, profile library, platform bridge, controller, status parsing, and all four screens. Android JVM tests cover:

- Shadowsocks addresses, AEAD TCP/UDP codecs and clients, plus the VLESS/Xray adapter.
- HTTP proxy parsing and relay.
- UDP endpoint flows and peer-alias lifecycle.
- Configuration persistence and host resolution.
- STUN wire protocol, classification, and diagnostic coordination.
- VPN startup/shutdown, permission coordination, and Live Update content.

Common verification commands:

~~~bash
flutter test
flutter analyze
cd android
./gradlew :app:testDebugUnitTest
~~~

Physical-device verification checks hotspot gateway, Switch proxy, connection test, eShop/TCP, UDP counters, real game P2P, service shutdown, and vendor notification behavior. Records under <code>docs/testing</code> are device-specific evidence, not a substitute for testing other devices.

## 15. Current Limitations

- Android 11+ and IPv4 only.
- TCP depends on a manually configured HTTP proxy on the Switch.
- UDP captures only registered fixed IP/port pairs; dynamic aliases learn only from responses on an existing relay session.
- UDP target hostnames are unsupported and CIDR expansion is limited to 4096 endpoints.
- Profile changes require a service restart.
- STUN behavior classification depends on server capabilities and can be partial or unavailable.
- The current release build still uses debug signing configuration.
- Hotspot routing through VpnService, local-address binding, background execution, and promoted ongoing notifications vary by vendor.
- NAT A, every P2P game, latency, and long-term stability are not guaranteed.

## 16. Key Source Files

- Flutter app/controller: <code>lib/src/app.dart</code>, <code>lib/src/state/app_controller.dart</code>
- Flutter screens: <code>lib/src/screens/</code>
- Profile models: <code>lib/src/config/</code>
- Android bridge: <code>android/app/src/main/kotlin/dev/switchnat/app/bridge/NativeBridge.kt</code>
- Profile repository: <code>android/app/src/main/kotlin/dev/switchnat/app/config/ProfileRepository.kt</code>
- VPN service: <code>android/app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt</code>
- Live Updates: <code>android/app/src/main/kotlin/dev/switchnat/app/vpn/LiveUpdateContent.kt</code>
- TCP proxy: <code>android/app/src/main/kotlin/dev/switchnat/app/proxy/HttpConnectProxy.kt</code>
- Fixed UDP proxy: <code>android/app/src/main/kotlin/dev/switchnat/app/udp/LocalUdpEndpointProxy.kt</code>
- Dynamic aliases: <code>android/app/src/main/kotlin/dev/switchnat/app/udp/UdpPeerAliasManager.kt</code>
- Shadowsocks clients: <code>android/app/src/main/kotlin/dev/switchnat/app/ss/</code>
- VLESS/Xray adapter: <code>android/app/src/main/kotlin/dev/switchnat/app/xray/</code>
- CI release workflow: <code>.github/workflows/android.yml</code>
- Diagnostics/STUN: <code>android/app/src/main/kotlin/dev/switchnat/app/diagnostics/</code>
