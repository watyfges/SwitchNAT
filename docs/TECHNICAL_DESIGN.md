# SwitchNAT 技术设计

[English](TECHNICAL_DESIGN_EN.md) | 简体中文

本文描述当前 <code>main</code> 版本的实现边界。它解释代码如何工作，不承诺所有设备、网络或游戏都得到相同结果。

## 1. 目标与非目标

SwitchNAT 的目标是在无 Root、无特权系统 API 的 Android 应用中，为连接手机热点的 Nintendo Switch 提供两条节点出口路径；节点可以是 Shadowsocks 或 VLESS + Reality：

- 通过手动 HTTP 代理处理 TCP。
- 通过 Android VpnService、本地 UDP endpoint 和动态 alias 处理选定 UDP 流量。

设计重点是保持同一 Switch 源端口对应的 UDP relay association，并在 P2P 玩家 endpoint 变化时延续已有 association。

当前非目标包括：

- 通用全设备 VPN。
- 透明 TCP TUN 拦截。
- IPv6。
- 自动识别所有游戏流量。
- 内置节点服务端或公共节点分发。
- 保证 NAT A、匹配成功率或特定厂商兼容性。

## 2. 系统架构

Flutter 负责 UI、配置编辑和状态展示；Kotlin 负责 Android 权限、持久化、VPN 生命周期和网络转发。

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

主要边界：

- Flutter：<code>AppController</code>、四个页面、配置模型和状态模型。
- 平台桥：<code>NativeBridge</code> 和 <code>MethodChannelConfigStore</code>。
- Android 服务：<code>SwitchNatVpnService</code>。
- TCP：<code>HttpConnectProxy</code>、<code>ShadowsocksTcpClient</code> 或本地 Xray SOCKS5 出站。
- UDP：<code>LocalUdpEndpointProxy</code>、<code>UdpPeerAliasManager</code>、<code>ShadowsocksUdpClient</code> 或 Xray SOCKS5 UDP association。
- 诊断：<code>Diagnostics</code>、<code>ConnectionDiagnosticsCoordinator</code>、TCP probe 和 STUN probe。

## 3. 配置与控制流

Flutter 使用两个 channel：

- <code>dev.switchnat.app/control</code>：请求 VPN/通知权限，加载或保存配置，启动、停止和运行诊断。
- <code>dev.switchnat.app/status</code>：持续发布 <code>DiagnosticsSnapshot</code>。

<code>ProfileRepository</code> 将 schema version 4 的 profile library 保存到 App 私有 SharedPreferences，并兼容读取 version 1 到 3。library 包含：

- 一个或多个 Shadowsocks/VLESS Reality profile。
- 当前选中的 profile ID。
- 全局共享的 STUN server 列表。
- 多个订阅源，以及订阅节点到来源和稳定节点 key 的关联。
- 全局主题偏好：跟随系统、浅色或深色；旧 schema 默认迁移为跟随系统。

profile 包含协议类型、host、port、HTTP proxy port、UDP targets 和协议专用字段。Shadowsocks 使用 password/AEAD method；VLESS Reality 使用 UUID、flow、SNI、Reality 公钥、short ID、fingerprint 和 spider path。首次启动会创建空节点，同时载入预置 UDP targets 和四个默认 STUN endpoint。旧版单节点配置会迁移到 profile library。

<code>SubscriptionService</code> 在 Flutter 层下载并解析 Clash YAML。它只接受 HTTP(S)，超时为 20 秒，响应上限为 2 MiB；当前支持指定 AEAD 的 Shadowsocks，以及 TCP/RAW VLESS + Reality。每个节点使用协议身份字段的 SHA-256 建立稳定 key。下载和完整解析成功后，Android 才原子替换该订阅的缓存节点；失败不会删除旧节点。刷新时匹配 key 的 profile ID 和当前选择保持不变，首次从空白 profile 导入时自动选择第一个订阅节点。

运行时的 profile ID 会写入诊断 session。用户在服务运行期间选择另一节点时，UI 标记“待应用变更”，但不会替换正在使用的 socket；必须停止并重新启动服务。

## 4. VPN 与服务生命周期

启动顺序：

1. Flutter 校验当前 profile，并请求通知和 VPN 权限。
2. Android 启动 <code>SwitchNatVpnService</code> 前台服务，状态进入 <code>starting</code>。
3. 服务解析节点 host，检测热点网关并展开 UDP targets。
4. VpnService Builder 添加 <code>10.88.0.2/32</code>、DNS <code>1.1.1.1</code>、MTU 1500。
5. 每个固定 UDP target IP 同时通过 <code>addAddress(ip, 32)</code> 和 <code>addRoute(ip, 32)</code> 注册。
6. 建立 TUN 后启动 HTTP proxy 和固定 UDP endpoint proxy。
7. 状态进入 <code>running</code>，并行启动 TCP 与 STUN probe。

当前实现不读取或解析 TUN 的裸 IPv4 包。TUN 的作用是注册目标地址与路由，使 Android 把目标 UDP 流量投递到 App 绑定的本地 DatagramSocket。

停止、权限撤销、启动失败和 service destroy 都会关闭诊断 executor、HTTP listener、UDP flows、alias sockets、TUN descriptor 和通知更新线程。服务停止后清除 active profile session 并移除前台通知。

## 5. TCP 转发路径

<code>HttpConnectProxy</code> 在 <code>0.0.0.0:httpProxyPort</code> 监听 Switch 连接，支持：

- <code>CONNECT host:port HTTP/1.1</code>。
- absolute-form HTTP 请求，例如 <code>GET http://example.test/path HTTP/1.1</code>。

它将请求解析为真实 target，再通过 <code>TcpOutboundFactory</code> 选择实际出站：Shadowsocks 节点使用 <code>ShadowsocksTcpClient</code> 并写入 Shadowsocks 地址头；VLESS + Reality 节点使用本地 Xray SOCKS5 inbound 的 <code>Socks5TcpClient</code>。随后 HTTP proxy 在两端双向复制字节流。客户端 header 上限为 16 KiB；出站连接超时为 10 秒。

直连节点的 socket 在连接前调用 <code>VpnService.protect()</code>，避免代理自身流量重新进入 VPN；Xray 仅在 loopback 暴露 SOCKS5 inbound，并负责 VLESS + Reality 出站。连接计数和失败类别写入诊断状态。

## 6. 固定 UDP Endpoint 转发

UDP target 输入支持空白、逗号或换行分隔：

~~~text
IPv4:port
IPv4/prefix:port
~~~

CIDR prefix 仅允许 /21 到 /32。所有表达式展开、去重后最多 4096 个 endpoint；host 必须是 IPv4 字面量。

对每个固定 endpoint：

1. VPN 注册其 IP 的 /32 地址和路由。
2. <code>DatagramLocalUdpSocketFactory</code> 绑定 <code>targetIp:targetPort</code>。
3. Switch 发来的 payload 被识别为一个以 Switch 源 <code>IP:port</code> 为 key 的 flow。
4. flow 通过当前节点的 <code>UdpRelaySession</code> 发往真实 target；具体实现为 Shadowsocks UDP packet，或 Xray SOCKS5 UDP association。
5. 若回包 source 与配置内 endpoint 相同，使用对应固定 socket 写回 Switch。

固定 targets 是 bootstrap 集合。清空它们会让服务缺少捕获初始 UDP 流量的地址，动态学习也没有可用的 relay session。

## 7. 动态 P2P Alias

<code>UdpRelaySession</code> 返回的响应同时包含远端 source endpoint。若该 endpoint 不在固定 targets 中，<code>UdpPeerAliasManager</code> 使用以下 key：

~~~text
(Switch flow IP:port, remote peer IP:port)
~~~

创建流程：

1. 在检测到的热点网关地址上绑定由系统分配的临时 UDP 端口。
2. alias socket 连接到当前 Switch endpoint。
3. 远端 payload 从 alias 发给 Switch；Switch 看到的 source 是热点网关和临时端口。
4. Switch 回复该 alias 后，manager 通过原 flow 的 <code>UdpRelaySender</code> 把 payload 发往真实 remote peer。

同一个 key 重复使用同一 alias。flow 关闭时，其 aliases 一起关闭。默认空闲超时 300 秒，sweeper 每 30 秒运行；每个 flow 最多 128 个 aliases，全局最多 512 个。达到限制时淘汰最旧 alias。

## 8. UDP Relay Session、节点协议与 NAT 行为

<code>LocalUdpEndpointProxy.flows</code> 仅以 Switch sender <code>IP:port</code> 为 key，而不把目标 endpoint 放入 key。同一 Switch 源端口访问多个固定服务器或动态玩家时，会复用同一个 <code>UdpRelaySession</code> 和同一个本地 UDP socket。Shadowsocks 配置实例化 <code>ShadowsocksUdpClient</code>；VLESS + Reality 配置实例化连接本地 Xray inbound 的 <code>Socks5UdpClient</code>。

这能减少 destination 变化导致的节点出口映射变化，更接近 endpoint-independent mapping 所需行为，也是当前实机 NAT 改善的重要条件。但最终公网映射仍由节点服务端所在网络决定；session 复用本身不保证 Full Cone 或 Nintendo NAT A。

Shadowsocks UDP socket 和 TCP socket 都调用 <code>VpnService.protect()</code>。支持的 AEAD methods 为：

- aes-128-gcm
- aes-256-gcm
- chacha20-ietf-poly1305

VLESS + Reality 节点由固定版本的 AndroidLibXrayLite/Xray Core 提供出站。<code>XrayCoreRuntime</code> 启动仅监听 <code>127.0.0.1</code> 的 SOCKS5 inbound；<code>Socks5TcpClient</code> 使用 CONNECT，<code>Socks5UdpClient</code> 使用 UDP ASSOCIATE。Reality 使用 RAW transport、SNI、fingerprint、short ID 和当前 Xray 字段 <code>password</code>（即界面中的 Reality 公钥）。VLESS outbound 显式设置 <code>packetEncoding=xudp</code>；每个 Switch flow 仍使用独立 SOCKS UDP association，当前不启用 XUDP Mux。

Xray AAR 不提交到 Git。<code>scripts/fetch_xray_core.sh</code> 从官方 AndroidLibXrayLite release 下载固定版本并校验 SHA-256；本地构建和 GitHub Actions 都在 Gradle 编译前运行该脚本。

## 9. STUN 诊断

服务进入 running 后，<code>ConnectionDiagnosticsCoordinator</code> 使用两个 worker 并行运行 TCP 和 NAT probe。首页点击相应卡片会取消同类旧任务并启动新 generation，旧结果不会覆盖新结果。

STUN probe 通过当前节点的 <code>UdpRelaySession</code> 向启用的全局 STUN endpoints 发送 Binding requests，因此 Shadowsocks 和 VLESS + Reality 都使用各自真实出口测试。实现先尝试现代格式，必要时回退 legacy 格式，并在服务端提供 OTHER-ADDRESS/CHANGE-REQUEST 能力时比较：

- 映射是否随 STUN destination 改变。
- 过滤是否接受 changed IP/port 或 changed port 响应。

UI 固定使用英文原始标签显示 Full Cone、Restricted Cone、Port-Restricted Cone 和 Symmetric，同时保存 mapped address、mapping behavior、filtering behavior、RTT 和实际响应的 STUN server。

只收到基础 Binding response、但缺少完成行为分类所需属性时，结果为 partial；所有服务器无响应时为 unavailable。

该结果描述 **当前节点出口的 UDP mapping/filtering behavior**，不是手机运营商 NAT，也不是 Switch 系统连接测试给出的 NAT grade。

## 10. Android Live Updates

VpnService 使用 ongoing foreground notification 保持运行。通知通过 AndroidX NotificationCompat：

- 设置 service category、chronometer 和 session start time。
- 尝试 <code>setRequestPromotedOngoing(true)</code>。
- 提供 <code>shortCriticalText</code>，运行时显示已加速分钟数。
- 展示节点、Switch presence、学习到的 Switch IP、UDP NAT 摘要和 TCP latency。
- 每 1 秒以最新 <code>DiagnosticsSnapshot</code> 更新。

启动阶段显示 Connecting；服务停止、失败或被撤销时停止更新并移除通知。系统是否将该通知显示为 Live Update、超级岛或普通前台通知由 Android 版本和厂商实现决定，通知提升失败不会影响网络转发。

## 11. 诊断与错误处理

<code>Diagnostics</code> 保存：

- VPN state、active profile、server endpoint 和 session start time。
- hotspot gateway、Switch proxy address、learned Switch IP 和 presence。
- TCP/STUN test state 与结果。
- UDP read、accepted、sent、received、written。
- active mappings、UDP flows、peer aliases、alias 创建/过期计数。
- TCP connections、last error 和最近 packet samples。

最后一次 Switch UDP 活动在 60 秒内时 presence 为 connected，之后为 inactive；从未观察到时为 waiting。最多保存 50 条 sample，只包含 timestamp、protocol、source、destination、direction 和 outcome，不包含 payload。

错误按边界分类，例如 BAD_CONFIG、VPN_START、LOCAL_UDP_SOCKET、SS_UDP_SEND、SS_UDP_RECEIVE、UDP_ALIAS_BIND、HTTP_PROXY_ACCEPT 和 HTTP_PROXY_OUTBOUND。启动失败会关闭已创建资源并把 state 设为 failed。

## 12. 并发与资源管理

网络层使用受控线程和并发容器：

- 每个固定 UDP endpoint 一个本地 receive thread。
- 每个 Switch UDP flow 一个 relay-session receive thread。
- 每个动态 alias 一个 Switch reply receive thread。
- 一个 alias sweeper thread。
- HTTP accept thread 和每个 TCP connection 的 relay workers。
- 两个诊断 worker。
- 一个每秒更新通知的 daemon thread。

flow map 使用 ConcurrentHashMap；alias map 在 lock 内访问；单个 flow 的 send 使用锁串行化，避免同一 UDP relay session 并发写入。close 操作具有幂等保护，并避免当前线程 join/interrupt 自己。

## 13. 安全与隐私边界

- 不需要 Root、ADB 常驻权限或特权系统 API。
- App outbound socket 必须 protect，防止 VPN routing loop。
- profile 和订阅 URL 保存在 MODE_PRIVATE SharedPreferences，但当前没有额外的 hardware-backed encryption；订阅列表只渲染 URL host。
- Android application 设置 <code>allowBackup=false</code>。
- packet samples 不存 payload，但 endpoint 和 IP 元数据仍可能敏感。
- 项目不内置用户节点、凭据、签名材料或遥测上传。
- 发布日志、issue 和抓包前仍需人工脱敏。

## 14. 测试策略

Flutter tests 覆盖配置模型、profile library、平台桥、controller、状态解析和四个页面。Android JVM tests 覆盖：

- Shadowsocks 地址、AEAD TCP/UDP codec 和 client，以及 VLESS/Xray adapter。
- HTTP proxy 解析与 relay。
- UDP endpoint flows 和 peer alias 生命周期。
- 配置持久化与 host 解析。
- STUN wire protocol、分类与诊断 coordinator。
- VPN 启停、权限协调和 Live Update content。

常用验证命令：

~~~bash
flutter test
flutter analyze
cd android
./gradlew :app:testDebugUnitTest
~~~

实机验证需要检查热点网关、Switch proxy、连接测试、eShop/TCP、UDP counters、真实游戏 P2P、服务停止和厂商通知表现。<code>docs/testing</code> 中的记录是特定设备证据，不替代其他设备测试。

## 15. 当前限制

- Android 11+，IPv4 only。
- TCP 依赖 Switch 手动 HTTP proxy。
- UDP 只捕获已注册的固定 IP/port；动态 alias 只能从现有 relay session 的回包学习。
- UDP target 不支持域名，CIDR 展开上限为 4096。
- profile 切换需要重启 service 才生效。
- STUN 行为分类依赖服务端能力，可能 partial 或 unavailable。
- 当前 release build 仍使用 debug signing config。
- VpnService 热点路由、本地地址 bind、后台运行和 promoted ongoing notification 具有厂商差异。
- 不保证 NAT A、所有 P2P 游戏、延迟或长时间稳定性。

## 16. 关键源码索引

- Flutter app/controller：<code>lib/src/app.dart</code>、<code>lib/src/state/app_controller.dart</code>
- Flutter screens：<code>lib/src/screens/</code>
- Profile models：<code>lib/src/config/</code>
- Android bridge：<code>android/app/src/main/kotlin/dev/switchnat/app/bridge/NativeBridge.kt</code>
- Profile repository：<code>android/app/src/main/kotlin/dev/switchnat/app/config/ProfileRepository.kt</code>
- VPN service：<code>android/app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt</code>
- Live Updates：<code>android/app/src/main/kotlin/dev/switchnat/app/vpn/LiveUpdateContent.kt</code>
- TCP proxy：<code>android/app/src/main/kotlin/dev/switchnat/app/proxy/HttpConnectProxy.kt</code>
- Fixed UDP proxy：<code>android/app/src/main/kotlin/dev/switchnat/app/udp/LocalUdpEndpointProxy.kt</code>
- Dynamic aliases：<code>android/app/src/main/kotlin/dev/switchnat/app/udp/UdpPeerAliasManager.kt</code>
- Shadowsocks clients：<code>android/app/src/main/kotlin/dev/switchnat/app/ss/</code>
- VLESS/Xray adapter：<code>android/app/src/main/kotlin/dev/switchnat/app/xray/</code>
- CI release workflow：<code>.github/workflows/android.yml</code>
- Diagnostics/STUN：<code>android/app/src/main/kotlin/dev/switchnat/app/diagnostics/</code>
