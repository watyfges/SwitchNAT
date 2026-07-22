# SwitchNAT GitHub 开源文档与发布设计

日期：2026-07-23
状态：已获用户确认，待实施

## 目标

将 SwitchNAT 作为技术探索项目发布到 `https://github.com/watyfges/SwitchNAT`，提供结构清晰、内容一致的中英文 README 和中英文技术文档，并以 GNU General Public License v3.0 only 开源。

发布必须满足以下要求：

- 默认分支 `master` 包含完整的中英文项目介绍、技术文档和许可证。
- 早期动态 UDP 实现的产品代码以 `codex/archive-initial-dynamic-udp` 干净快照一并上传；原始提交哈希仅保留在本地备份引用中，不公开其中的本机路径。
- 不上传节点密码、ADB 配对信息、签名材料、本机构建路径、日志、APK 或其他构建产物。
- 远端审计发现用户刚创建的 `main` 只有独立初始提交 `c1d8aac574fe1d906ede38088f8cfede2f8287eb`；用户随后明确授权以本地完整项目覆盖它。覆盖必须使用绑定该提交 ID 的 `--force-with-lease`，不得使用无条件 force push。
- 文档准确描述当前实现及其限制，不将实验性兼容结果宣传为普遍保证。

## 文档结构

采用独立双语文件，而不是在单个文件中顺序堆叠两种语言：

- `README.md`：中文项目主页，顶部链接英文版。
- `README_EN.md`：英文项目主页，顶部链接中文版。
- `docs/TECHNICAL_DESIGN.md`：中文技术文档。
- `docs/TECHNICAL_DESIGN_EN.md`：英文技术文档。
- `LICENSE`：未经修改的 GPLv3 官方英文许可证全文。

独立文件能让 GitHub 首页保持简洁，并使每种语言的目录、锚点和阅读顺序保持连贯。当前 `README.md` 和 `docs/TECHNICAL_DESIGN.md` 将按现有完整版本重写；英文文件为语义一致的对应版本，不是缩减摘要。

## README 内容

两份 README 保持相同章节和信息层级：

1. 语言切换。
2. 项目定位、实验性质和非 Root 特性。
3. 当前主要功能：
   - Switch 手动 HTTP 代理与 Shadowsocks TCP 转发。
   - 固定 UDP endpoint 转发。
   - 动态 P2P endpoint alias 学习与 relay session 复用。
   - 多 Shadowsocks 节点配置。
   - Shadowsocks 出口 STUN NAT 行为测试。
   - Android Live Updates，以及兼容系统可能提供的超级岛展示。
   - 运行状态、连接诊断和日志。
4. 工作原理概览，并链接详细技术文档。
5. 环境与前置条件，包括支持 UDP relay 的 Shadowsocks 服务端。
6. 构建、测试和基本使用步骤。
7. 当前限制与兼容性边界。
8. 隐私和安全说明。
9. 非官方项目免责声明。
10. GPLv3 许可证摘要、完整许可证链接和版权声明。
11. 早期动态 UDP 归档分支链接。

README 不包含真实 Shadowsocks 节点、真实公网地址、测试设备标识或绝对本机路径。构建命令采用仓库无关的标准 Flutter/Gradle 命令；仓库内的 `.toolchains` 仅属于本地环境，不写成用户必须具备的目录结构。

## 技术文档内容

中英文技术文档应覆盖相同的系统边界和关键实现：

- 目标、非目标和 Android 版本范围。
- Flutter UI/状态层与 Kotlin 网络层的职责边界。
- MethodChannel 控制与状态同步。
- `VpnService` 启动、授权、前台服务和关闭生命周期。
- 热点网关与 Switch 客户端检测。
- TCP HTTP CONNECT/absolute-form 请求转发路径。
- 固定 UDP target 的 VPN 地址、路由和本地 socket bind 机制。
- Shadowsocks AEAD TCP/UDP 支持范围和 `VpnService.protect()`。
- 以 Switch 源端口为 key 的 UDP relay session 复用。
- 动态 P2P alias 的创建、回包、容量上限、超时和回收。
- STUN 测试的边界：它测量 Shadowsocks 出口的 UDP 映射/过滤行为，不等同于 Nintendo Switch 的 NAT 等级。
- 节点配置、持久化和节点切换行为。
- Android Live Updates 的启停关系和展示信息。
- 并发、资源清理、错误分类、诊断计数和 packet sample。
- 测试策略、已验证设备记录和当前限制。
- 关键源码索引。

技术文档可使用 Mermaid 描述主要数据流，但不依赖外部图片或专有绘图文件。

## 许可证设计

项目采用 `GPL-3.0-only`，版权声明为：

```text
Copyright (C) 2026 watyfges
```

选择 GPLv3 的原因是项目作者不允许他人对外分发闭源修改版。GPLv3 仍允许使用、研究、修改、商业使用和收费分发；其强 copyleft 要求分发修改版或目标代码时继续提供对应源码、保留许可证，并在适用范围内采用同一许可证。私下修改但不对外分发不会触发公开源码义务。

`LICENSE` 必须使用官方文本，不修改法律条款，也不使用非官方中文翻译替代正式许可证。README 只提供便于理解的中英文摘要，并注明摘要不替代许可证原文。

参考：

- GNU GPLv3：`https://www.gnu.org/licenses/gpl-3.0.html`
- GNU GPLv3 快速指南：`https://www.gnu.org/licenses/quick-guide-gplv3.html`
- SPDX 标识：`https://spdx.org/licenses/GPL-3.0-only.html`

## 品牌与免责声明

README 必须明确说明：

- SwitchNAT 是独立、非官方的技术探索项目。
- 项目与 Nintendo、Shadowsocks 项目及 Android 设备厂商不存在隶属、赞助、认可或背书关系。
- Nintendo Switch 和其他商标属于各自权利人。
- 使用者应遵守所在地法律、网络服务条款以及其 Shadowsocks 服务端提供方的规则。
- 软件按 GPLv3 的无担保条款提供，网络兼容性和 NAT 结果不作保证。

## 安全与隐私审计

首次公开前检查全部 Git 跟踪文件和待提交差异，重点搜索：

- Shadowsocks 密码、真实服务器地址和私有节点名称。
- ADB 无线调试地址、配对端口和配对码。
- Android keystore、签名密码、访问令牌、API key 和私钥。
- 本机用户名、绝对目录、SDK/toolchain 路径和设备序列号。
- 抓包、日志、APK、构建缓存和测试生成物。

示例地址使用 RFC 5737 文档网段，例如 `192.0.2.0/24`、`198.51.100.0/24` 和 `203.0.113.0/24`。若本机标识已经进入尚未公开的 Git 历史，仅从最新提交删除并不充分；发布分支必须改为无父提交的干净快照，同时把原始历史保存在不推送的本地备份引用中。

## 验证策略

提交文档前执行：

- `git diff --check`，检查空白和补丁格式。
- 搜索 `TODO`、`TBD`、失效的仓库内路径和不一致术语。
- 对照中英文标题和功能列表，确认两版语义一致。
- 验证 README 中的相对链接能够指向双语技术文档、许可证和归档分支。
- 运行 `flutter test` 和 `flutter analyze`，记录真实结果，不因文档工作掩盖既有失败。
- 检查 `git status`，确认没有构建产物或本地配置进入提交。

许可证、README 和技术文档作为一个独立提交加入 `master`。除非验证发现文档所述与实现不一致，本任务不修改产品代码。

## GitHub 发布流程

目标远端为 `https://github.com/watyfges/SwitchNAT`。

1. 使用 `git ls-remote` 或等效只读方式检查远端分支和提交。
2. 将仓库配置为 `origin`。
3. 再次 fetch 并确认远端 `main` 仍为已审计的初始提交 `c1d8aac574fe1d906ede38088f8cfede2f8287eb`。
4. 为当前版本和早期动态 UDP 版本分别生成无父提交的干净快照；产品源码保持对应版本不变，只清除公开历史中的本机标识和内部报告。
5. 使用 `--force-with-lease=main:c1d8aac574fe1d906ede38088f8cfede2f8287eb` 将干净的本地 `master` 发布为远端默认 `main`；若远端已变化，命令必须拒绝覆盖。
6. 正常推送同一干净快照到远端 `master`，并推送清理后的 `codex/archive-initial-dynamic-udp`。
7. 验证三个远端分支均指向预期提交。

APK 不进入 Git 历史。未来如需发布安装包，应构建正式签名的 release APK，并通过 GitHub Releases 发布。

## 完成标准

- 中英文 README 和中英文技术文档内容完整且互相链接。
- GitHub 能识别 `GPL-3.0-only` 许可证。
- 文档不包含敏感配置或本机环境信息。
- `master` 仅新增本次文档、许可证和必要的文档替换。
- 归档分支保持原样。
- 远端 `master` 与归档分支均完成非强制推送并核对提交 ID。
