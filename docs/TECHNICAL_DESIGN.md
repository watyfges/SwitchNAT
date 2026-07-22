# SwitchNAT 技术文档

## 目标

SwitchNAT 是一个面向 Nintendo Switch 热点联网场景的 Android Flutter 应用。核心目标是在不获取 root 权限的前提下，让 Switch 通过手机热点使用 Shadowsocks 服务器转发 TCP 与指定 UDP 流量。

当前实现分为两条路径：

- TCP：Switch 手动配置 HTTP 代理，手机 App 在热点网关地址上监听 HTTP 代理端口，再通过 Shadowsocks TCP 转发。
- UDP：App 通过 Android `VpnService` 建立本地 VPN，并把指定 UDP 目标 IP 注册为本机 VPN 地址和路由，然后在这些 `IP:port` 上绑定本地 UDP socket，接收 Switch 发出的 UDP 包并通过 Shadowsocks UDP 转发。

## 运行入口

Android 原生侧入口是 `SwitchNatVpnService`。

启动流程：

1. Flutter 层收集 Shadowsocks 服务器、HTTP 代理端口、UDP targets 等配置。
2. `NativeBridge` 调用原生控制接口启动 `VpnService`。
3. `SwitchNatVpnService` 校验配置并解析 Shadowsocks 服务器地址。
4. 检测热点网关地址，并写入诊断状态供 UI 显示。
5. 建立 Android VPN：
   - 添加固定 TUN 地址 `10.88.0.2/32`。
   - 对每个 UDP target 的 IP 添加 `addAddress(ip, 32)` 和 `addRoute(ip, 32)`。
6. 启动 TCP HTTP 代理。
7. 如果 UDP targets 非空，启动本地 UDP endpoint proxy。

## TCP 转发路径

TCP 侧由 `HttpConnectProxy` 负责。

Switch 侧配置：

- 代理服务器：App 显示的热点网关地址。
- 代理端口：配置中的 HTTP proxy port。

支持的 HTTP 代理请求：

- `CONNECT host:port HTTP/1.1`
- absolute-form HTTP 请求，例如 `GET http://example.com/path HTTP/1.1`

转发流程：

1. `HttpConnectProxy` 在 `0.0.0.0:httpProxyPort` 监听。
2. 解析目标地址。
3. 使用 `ShadowsocksTcpClient` 建立到 Shadowsocks 服务端的 TCP 连接。
4. 对 TCP socket 调用 `VpnService.protect()`，避免代理自身流量再次进入 VPN。
5. 双向复制 Switch 与 Shadowsocks 连接的数据流。

## UDP 转发路径

UDP 侧由 `LocalUdpEndpointProxy` 和 `ShadowsocksUdpClient` 负责。

### 为什么不直接读 TUN 包

早期实现尝试从 TUN 中解析 IPv4/UDP 包并做 NAT 表转发。这条路径已经删除，原因是当前可用方案并不依赖裸包解析，而是利用 Android VPN 的本地地址和路由能力，把目标 UDP 包投递到 App 自己绑定的 socket。

### 当前无 root UDP 方案

对每个 UDP target：

1. VPN builder 添加目标 IP：`addAddress(targetIp, 32)`。
2. VPN builder 添加目标路由：`addRoute(targetIp, 32)`。
3. App 绑定本地 UDP socket 到 `targetIp:targetPort`。
4. Switch 向该公网 `IP:port` 发 UDP 时，Android 会把数据投递到 App 绑定的本地 socket。
5. App 使用 Shadowsocks UDP 协议把 payload 转发到真实目标。
6. 服务端回包解密后，若源目标地址属于配置的固定 endpoint，App 使用对应固定 socket 回包；若属于动态 P2P 玩家，则 App 在 Switch 可达的手机局域网地址上分配临时高端口别名，再通过该别名把回包发给 Switch。

这个方案的关键点是：目标 IP 同时是 VPN 本地地址、路由目的地址、以及本地 UDP socket 的 bind 地址。

### NAT 等级关键点

UDP relay session 以 Switch 的源 `IP:port` 作为 key，而不是以 `目标 IP:port + Switch 源 IP:port` 作为 key。

这样同一个 Switch UDP 源端口访问多个任天堂服务器时，会复用同一个 Shadowsocks UDP socket。这个行为更接近主机 NAT 的端口保持逻辑，是当前 NAT A 的关键实现点。

### 回包路径与动态 P2P 别名

Shadowsocks UDP 解密后会得到：

- `DecodedUdpPacket.target`：远端响应对应的源目标地址。
- `DecodedUdpPacket.payload`：真实 UDP payload。

对于配置内 endpoint，App 使用同一个 `IP:port` socket 把 payload 发回 Switch，保持固定服务器的真实来源。

对于配置外的动态玩家 endpoint，App 不重建 VPN，也不把该公网 IP 加到 TUN。它以 Switch 的源 `IP:port` 和远端玩家 `IP:port` 为 key，在手机热点或局域网地址上创建一个 OS 分配的高端口 UDP socket。Switch 收到的来源是这个本地别名，回复也先回到别名 socket；App 再通过原有的同一条 Shadowsocks UDP relay session 把回复发往真实玩家 endpoint。

别名空闲 5 分钟回收；每个 Switch UDP flow 最多 128 个别名，整个代理最多 512 个。达到上限时淘汰最旧别名，不会重建 VPN。

## UDP Targets 配置

Flutter UI 中的 `UDP targets` 字段支持空白、换行或逗号分隔。

支持格式：

```text
IPv4:port
IPv4/prefix:port
```

示例：

```text
203.0.113.10:8123
203.0.112.0/21:33334
```

约束：

- UDP target host 必须是 IPv4 字面量，不支持域名。
- CIDR prefix 范围是 `/21` 到 `/32`。
- CIDR 会展开成具体 `IP:port` endpoint。
- 展开后的 endpoint 总数最多 `4096`。
- 端口必须是 `1..65535`。

## Shadowsocks 支持范围

当前支持的加密方法：

- `aes-128-gcm`
- `aes-256-gcm`
- `chacha20-ietf-poly1305`

TCP 使用 Shadowsocks stream 协议；UDP 使用 Shadowsocks UDP packet 协议。TCP/UDP socket 都会先调用 Android `VpnService.protect()`，避免代理流量被 VPN 自身捕获。

## 热点网关检测

App 会检测 Android 热点侧地址候选，并选择热点网关地址展示给用户。用户在 Switch 上手动填写：

- HTTP 代理服务器：App 显示的热点网关 IP。
- HTTP 代理端口：App 配置的 HTTP proxy port。

注意：不应显示或依赖 iPhone 默认热点网关 `172.20.10.1`。Android 侧以本机实际热点地址为准。

## 诊断信息

运行时诊断包括：

- VPN 状态：starting/running/stopped/failed。
- 热点网关与 HTTP proxy port。
- UDP read/accepted/sent/received/written 计数。
- UDP relay flow、当前 P2P alias、alias 创建和回收计数。
- TCP 连接计数。
- 最近错误。
- 最近 packet samples。
- 学习到的 Switch IP。

这些信息用于判断 Switch 是否已经把流量打到 App，以及 TCP/UDP 哪一段出现问题。

## 构建与安装

Debug APK 会很大，主要因为包含 debug/runtime 产物和多 ABI 内容。用于手机测试和分发时应使用 release。

构建 universal release：

```bash
.toolchains/flutter/bin/flutter build apk --release
```

构建 split per ABI release：

```bash
.toolchains/flutter/bin/flutter build apk --release --split-per-abi
```

ARM64 Android 手机推荐安装：

```bash
.toolchains/android-sdk/platform-tools/adb install -r build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
```

当前工作区参考体积：

- Debug APK：约 158 MB。
- Universal release APK：约 47 MB。
- ARM64 split release APK：约 17 MB。

## 当前限制

- UDP 固定服务器仍需提前配置目标 IP/网段和端口；已到达现有 relay session 的未知 P2P 玩家 endpoint 会自动映射为本地 alias。
- UDP target 不支持域名，因为 VPN 地址、路由和 socket bind 都要求确定的本地 IP。
- 大网段会展开为大量 endpoint，受 `4096` 上限限制。
- 当前 release 仍使用 debug signing config，正式分发前应替换为正式签名。
- 此方案依赖 Android `VpnService` 对添加地址、路由和本地 socket bind 的行为；不同厂商系统可能存在兼容性差异。

## 关键源码位置

- Android VPN 入口：`android/app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt`
- TCP HTTP 代理：`android/app/src/main/kotlin/dev/switchnat/app/proxy/HttpConnectProxy.kt`
- UDP 本地 endpoint 代理：`android/app/src/main/kotlin/dev/switchnat/app/udp/LocalUdpEndpointProxy.kt`
- Shadowsocks UDP 编解码和 socket：`android/app/src/main/kotlin/dev/switchnat/app/ss/ShadowsocksUdpClient.kt`
- Shadowsocks 配置和 UDP target 解析：`android/app/src/main/kotlin/dev/switchnat/app/config/ShadowsocksConfig.kt`
- Flutter 配置界面：`lib/src/screens/config_screen.dart`
