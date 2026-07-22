# Xiaomi 15 Android 16 Verification

Status: Pending physical-device verification

Date:
Device: Xiaomi 15
Android build:
Carrier:
App commit: d17bf41
APK SHA-256: c6d88677cdadad943738a8da1aff3cdca11721d9aef9cb9336ae26635df2f2b1
Shadowsocks server label:
Shadowsocks method:

Do not record the Shadowsocks password in this document.

## Preflight

- [ ] Install `build/app/outputs/flutter-apk/app-debug.apk` with `adb install -r`.
- [ ] Confirm the Shadowsocks server accepts both TCP and UDP relay traffic.
- [ ] Disable any other VPN on the phone.
- [ ] Keep mobile data enabled and Wi-Fi client mode disabled during hotspot tests.
- [ ] Reset the app counters by stopping and starting SwitchNAT before each measured run.

## Baseline

- Hotspot enabled:
- Switch connected to hotspot:
- Switch proxy disabled:
- Baseline NAT type:
- Baseline connection-test IP:
- Baseline download speed:
- Last app/VPN state before baseline:

## App Startup

- VPN permission prompt appeared:
- VPN status reached running:
- Foreground notification remained visible:
- Hotspot gateway shown:
- Switch proxy address shown:
- Last error:
- Result: pass/fail

Pass conditions:

- VPN state reaches `running` without a routing loop.
- The displayed hotspot gateway is the phone address used by the Switch.
- The displayed proxy port matches the configured local HTTP proxy port.

## UDP Without Switch Proxy

- Switch proxy disabled:
- UDP read count before test:
- UDP read count after test:
- UDP accepted count after test:
- UDP sent count after test:
- UDP received count after test:
- UDP written count after test:
- Learned Switch IP:
- Active mappings:
- Recent outbound/inbound packet samples visible:
- NAT type after app enabled:
- Result: pass/fail

Pass conditions:

- `udpRead`, `udpAccepted`, `udpSent`, `udpReceived`, and `udpWritten` all increase.
- The learned Switch IP belongs to the hotspot client subnet.
- Recent packet samples show both `outbound/sent` and `inbound/written` outcomes.
- NAT type improves from C to A, or otherwise materially improves from baseline.

If `udpRead` remains zero, record the Xiaomi tethering limitation as `HOTSPOT_NOT_IN_VPN` and stop UDP investigation before changing the Shadowsocks codec.

## TCP With Switch Proxy

- Switch proxy host:
- Switch proxy port:
- TCP connection count before test:
- TCP connection count after test:
- Connection-test IP changed:
- Download speed changed:
- eShop load improved:
- Result: pass/fail

Pass conditions:

- `tcpConnections` increases after the Switch proxy is enabled.
- The connection-test IP or speed changes through the Shadowsocks server.
- eShop or download behavior improves compared with baseline.

## Game Test

- Game:
- Matchmaking success:
- Session duration:
- Disconnects:
- UDP mappings peak:
- Last error:
- Result: pass/fail

## Failure Classification

Use the first matching category:

- `HOTSPOT_NOT_IN_VPN`: no Switch UDP reaches TUN.
- `SS_UDP_SEND`: accepted packets cannot be sent to the Shadowsocks server.
- `SS_UDP_RECEIVE`: sent packets receive no valid/decryptable response.
- `NAT_MAPPING_MISS`: a remote response has no active Switch mapping.
- `TUN_READ` or `TUN_PACKET`: TUN packet processing failed.
- `HTTP_PROXY_ACCEPT`: the local proxy cannot accept Switch connections.
- `HTTP_PROXY_OUTBOUND`: CONNECT arrived, but Shadowsocks TCP setup failed.
- `VPN_START`: foreground service or TUN setup failed.

## Notes

Record exact reproduction steps, counter deltas, and the latest error string. Do not include passwords or packet payloads.
