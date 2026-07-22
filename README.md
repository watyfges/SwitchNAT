# SwitchNAT

[English](README_EN.md) | 简体中文

> 实验性、非官方、无 Root 的 Nintendo Switch Android 热点网络转发工具。

SwitchNAT 让连接到 Android 手机热点的 Nintendo Switch 通过自备 Shadowsocks 节点转发 TCP 和 UDP 流量。它不是通用 VPN，而是在普通 Android 应用权限范围内探索 Switch 联机、NAT 映射和动态 P2P endpoint 的可行实现。

## 项目简介

SwitchNAT 将 TCP 和 UDP 分成两条路径：

- TCP：Switch 手动配置手机热点网关上的 HTTP 代理，App 再通过 Shadowsocks TCP 转发。
- UDP：Android VpnService 将预配置的目标地址投递给 App 绑定的本地 socket，App 使用 Shadowsocks UDP relay 转发 payload。

固定 UDP target 用于建立初始通信路径。当服务器返回未预配置的玩家 endpoint 时，App 会动态创建 Switch 可达的本地 UDP alias，并复用原有 Shadowsocks UDP session 继续双向通信。该机制适用于玩家加入、退出或重新匹配时远端地址变化的 P2P 场景。

本项目已经在特定设备和网络环境中完成技术验证，但不同 Android 厂商、系统版本、热点实现、运营商网络、Shadowsocks 服务端和游戏可能产生不同结果。

## 主要功能

- 无 Root Android VpnService。
- HTTP CONNECT 和 absolute-form HTTP 代理，经 Shadowsocks TCP 转发。
- 固定 IPv4/CIDR UDP targets，经 Shadowsocks UDP relay 转发。
- 动态 P2P endpoint alias 学习、复用、超时和容量回收。
- 按 Switch 源 IP:port 复用 UDP relay session，减少目标变化导致的公网端口变化。
- 多 Shadowsocks 节点的创建、重命名、选择、保存和删除。
- 支持 aes-128-gcm、aes-256-gcm 和 chacha20-ietf-poly1305。
- 服务启动后自动执行 TCP 连通性和 STUN NAT 行为测试，也可手动重新测试。
- 可自定义全局 STUN 服务器列表。
- 展示 Switch 连接状态、热点网关、代理地址、UDP/TCP 计数、动态 flow、peer alias 和最近错误。
- Android 前台服务通知及 Live Updates 元数据；兼容系统可将其展示为实时活动或类似超级岛的界面。

## 工作原理

~~~text
Nintendo Switch
  ├─ TCP → 手机热点 HTTP 代理 → Shadowsocks TCP → 目标服务
  └─ UDP → Android VpnService/local endpoint → Shadowsocks UDP → 固定服务器或 P2P 玩家
                                                        ↑
                                      动态玩家通过本地 UDP alias 回到同一 relay session
~~~

STUN 页面显示的是 **Shadowsocks 出口** 的 UDP 映射和过滤行为，例如 Full Cone、Restricted Cone、Port-Restricted Cone 或 Symmetric。它不是 Nintendo Switch NAT 等级的直接检测结果，也不能单独保证某个游戏能够联机。

动态学习不能完全替代 bootstrap UDP targets：App 必须先通过固定 target 收到远端响应，才能从已有 relay session 中学习未知玩家 endpoint。

详细实现见[中文技术文档](docs/TECHNICAL_DESIGN.md)或[英文技术文档](docs/TECHNICAL_DESIGN_EN.md)。

## 环境要求

- Android 11（API 30）或更高版本。
- 支持热点和标准 VpnService 的 Android 手机。
- Nintendo Switch 连接到该手机热点。
- 自备 Shadowsocks 服务端，并确认 TCP 和 UDP relay 均可用。
- 构建环境：
  - Flutter stable，Dart SDK 满足 pubspec.yaml 中的 ^3.12.2。
  - JDK 17。
  - Android SDK。

不同厂商对热点流量、VPN 地址绑定、通知 Live Updates 和后台运行的处理可能不同。当前仓库包含 Xiaomi Android 16 和 vivo Android 兼容性验证记录，但这些记录不构成通用兼容保证。

## 构建与测试

~~~bash
flutter pub get
flutter test
flutter analyze
flutter build apk --debug
~~~

构建按 ABI 拆分的 release APK：

~~~bash
flutter build apk --release --split-per-abi
~~~

当前 release 构建仍使用 debug signing config，仅适合个人测试。正式分发前应配置独立 release keystore，且不得将 keystore 或密码提交到仓库。

Android JVM 测试：

~~~bash
cd android
./gradlew :app:testDebugUnitTest
~~~

## 使用方法

1. 在 Config 页面创建或选择一个节点，填写 Shadowsocks 地址、端口、密码、加密方法和 HTTP proxy port。
2. 检查预置 UDP targets；它们负责 bootstrap 固定服务端流量，可按实际抓包结果修改。
3. 按需编辑全局 STUN 服务器列表并保存节点。
4. 打开手机热点，让 Switch 连接该热点。
5. 在 Switch 网络设置中，把 HTTP 代理地址设为 App 显示的热点网关，端口设为节点配置的 proxy port。
6. 回到首页启动 SwitchNAT，并授予 VPN 和通知权限。
7. 服务启动后等待自动 TCP/STUN 测试；点击对应状态卡可以重新测试。
8. 在 Switch 上执行连接测试或进入游戏，并通过 Status/Logs 页面观察 UDP flow、peer alias、错误和最近包元数据。

运行期间选择其他节点不会静默切换当前 session；停止并重新启动服务后才会应用新节点。

## 当前限制

- 仅实现 Android；没有 iOS 版本。
- 仅处理 IPv4。
- UDP target 必须是 IPv4 字面量或受限 CIDR，不支持域名。
- CIDR 仅允许 /21 到 /32，展开后的 endpoint 总数最多 4096。
- 每个 Switch UDP flow 最多 128 个动态 alias，全局最多 512 个；默认空闲 5 分钟回收。
- TCP 需要在 Switch 上手动配置 HTTP 代理，不是透明 TCP VPN。
- 动态 alias 依赖已经建立的 Shadowsocks UDP relay session，不能从完全未知且从未到达的流量中凭空发现玩家。
- STUN 服务端必须支持测试所需属性；只返回基础 Binding 响应时可能显示 partial result。
- Android 厂商可能限制热点流量进入 VPN、本地地址绑定、前台服务或 Live Updates。
- NAT A、匹配成功率、延迟和稳定性取决于服务端、运营商、游戏协议及其他外部条件。

## 安全与隐私

- Shadowsocks 配置保存在 App 私有的 SharedPreferences 中，但当前未额外使用硬件密钥加密。
- App 不提供或上传公共代理节点；用户需要自行管理服务端和凭据。
- 诊断 packet sample 只记录时间、协议、源/目标、方向和结果，不记录 payload。
- 发布 issue、日志或抓包前，请删除密码、服务器地址、设备标识、公网 IP 和其他个人信息。

## 技术文档

- [技术设计（中文）](docs/TECHNICAL_DESIGN.md)
- [Technical Design (English)](docs/TECHNICAL_DESIGN_EN.md)
- [Xiaomi Android 16 验证模板](docs/testing/xiaomi15-android16-verification.md)
- [vivo Android 兼容性记录](docs/testing/vivo-android-compatibility-smoke.md)

## 归档版本

最初完成动态 UDP endpoint alias、且已进行设备联机验证的早期实现保存在分支：

[codex/archive-initial-dynamic-udp](https://github.com/watyfges/SwitchNAT/tree/codex/archive-initial-dynamic-udp)

该分支用于保存历史实现，不代表当前推荐版本。

## 免责声明

SwitchNAT 是独立、非官方的技术探索项目，与 Nintendo、Shadowsocks 项目、Google、小米、vivo 或其他设备厂商不存在隶属、赞助、认可或背书关系。Nintendo Switch 及其他商标属于各自权利人。

请遵守所在地法律、网络服务条款、游戏服务条款和 Shadowsocks 服务端提供方规则。软件按许可证中的无担保条款提供，不保证特定 NAT 等级、游戏兼容性、网络质量或持续可用性。

## 许可证

本项目采用 [GNU General Public License v3.0 only](LICENSE)（SPDX：GPL-3.0-only）。

你可以使用、研究、修改和分发本项目，也可以收费分发；但对外分发修改版或目标代码时，必须遵守 GPLv3 对对应源码、许可证和相同许可条件的要求。此摘要不替代 LICENSE 中的正式条款。

Copyright (C) 2026 watyfges
