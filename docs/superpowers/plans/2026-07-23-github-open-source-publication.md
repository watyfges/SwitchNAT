# SwitchNAT GitHub Open Source Publication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish SwitchNAT to `watyfges/SwitchNAT` with complete Chinese and English project documentation, a GPLv3-only license, a clean public source tree, and both the current and initial dynamic-UDP archive branches.

**Architecture:** Keep the default `master` branch as the canonical current version, with Chinese landing documents and one-to-one English companions. Publish both current and initial dynamic-UDP versions as clean root snapshots, retain their original histories only in unpushed local backup refs, and push only after local verification and a non-destructive comparison with the GitHub remote.

**Tech Stack:** Markdown, Mermaid, GNU GPL v3.0, Git, GitHub, Flutter/Dart, Android/Kotlin, Gradle/JUnit.

## Global Constraints

- Target repository: `https://github.com/watyfges/SwitchNAT`.
- Copyright: `Copyright (C) 2026 watyfges`.
- License identifier: `GPL-3.0-only`; use the unmodified official GPLv3 English text.
- Chinese canonical files: `README.md` and `docs/TECHNICAL_DESIGN.md`.
- English companion files: `README_EN.md` and `docs/TECHNICAL_DESIGN_EN.md`.
- Keep the archived product source unchanged, sanitize publication-only documents, and publish it as a clean root snapshot alongside `master`.
- Preserve the exact pre-publication histories in local backup refs that are never pushed.
- Do not publish Shadowsocks credentials, ADB pairing information, signing material, access tokens, local toolchain paths, logs, APKs, or build output.
- Do not claim universal Android, game, NAT, Live Updates, or vendor compatibility.
- The user explicitly authorized replacement of the newly created remote `main` root commit `c1d8aac574fe1d906ede38088f8cfede2f8287eb`. Replace it only with `--force-with-lease=main:c1d8aac574fe1d906ede38088f8cfede2f8287eb`; never use unconditional force push.
- Product source code is outside this task unless documentation verification proves a factual error that cannot be corrected in documentation.

---

### Task 1: Establish a Safe Publication Baseline

**Files:**
- Inspect: all paths returned by `git ls-files`
- Inspect: `.gitignore`
- Inspect: local and remote Git refs
- Modify: `.gitignore`
- Delete: `.superpowers/sdd/task-1-report.md`
- Sanitize: historical planning and device-verification Markdown files reported by the audit

**Interfaces:**
- Consumes: local `master`, `codex/archive-initial-dynamic-udp`, and GitHub repository URL.
- Produces: a verified clean baseline and confirmation that remote `main` is still the user-authorized initial commit before replacement.

- [ ] **Step 1: Verify the local branch and worktree**

Run:

```bash
git branch --show-current
git status --short --branch
git log --oneline --decorate -5
git worktree list --porcelain
```

Expected: the main checkout is on `master`; only the committed design and plan differ from earlier history; the archive worktree remains registered at `codex/archive-initial-dynamic-udp`.

- [ ] **Step 2: Inspect the GitHub remote without changing it**

Run:

```bash
git ls-remote --heads --tags https://github.com/watyfges/SwitchNAT.git
```

Expected: remote `main` is `c1d8aac574fe1d906ede38088f8cfede2f8287eb`. Any other remote commit requires a new user decision before publication.

- [ ] **Step 3: Audit tracked file names and sensitive-value patterns**

Run:

```bash
git ls-files
git grep -n -I -E '(password|passwd|secret|token|api[_-]?key|private[_-]?key|BEGIN (RSA|OPENSSH|EC|PRIVATE) KEY|adb pair|pairing code)' -- ':!pubspec.lock'
git grep -n -I -E '(/home/|/Users/|[A-Za-z]:\\\\Users\\\\|192\.168\.[0-9]+\.[0-9]+:[0-9]{4,5})'
git ls-files | rg -i '(\.jks$|\.keystore$|\.p12$|\.pem$|\.key$|\.apk$|local\.properties$|\.log$)'
```

Expected: matches are field names, test fixtures, validation messages, or documentation warnings only. No credential value, ADB pairing code, signing material, APK, log, or absolute local path is tracked.

- [ ] **Step 4: Verify ignore coverage**

Run:

```bash
git check-ignore -v .toolchains .worktrees build .dart_tool android/local.properties
```

Expected: every path is ignored by a repository rule or an appropriate nested Android rule.

- [ ] **Step 5: Commit publication-safety remediation when the audit finds local metadata**

Remove tracked local-only reports, add `.toolchains/` to `.gitignore`, replace concrete LAN device endpoints with placeholders, replace absolute local toolchain commands with standard repository-independent commands, and record the user-authorized remote override in this plan and its design spec.

Run:

```bash
git add .gitignore .superpowers docs
git commit -m "docs: sanitize repository for publication"
```

Expected: the commit changes documentation and ignore metadata only; no product source file is modified.

---

### Task 2: Add the GPLv3-Only License and Bilingual READMEs

**Files:**
- Create: `LICENSE`
- Modify: `README.md`
- Create: `README_EN.md`

**Interfaces:**
- Consumes: the confirmed project features and `GPL-3.0-only` decision.
- Produces: GitHub landing pages with matching structure and an OSI-recognized license file.

- [ ] **Step 1: Add the canonical license text**

Create `LICENSE` from the unmodified GNU GPL version 3 plain-text source at:

```text
https://www.gnu.org/licenses/gpl-3.0.txt
```

Verify the file begins with:

```text
GNU GENERAL PUBLIC LICENSE
Version 3, 29 June 2007
```

and contains the complete section 17 warranty disclaimer and the final “How to Apply These Terms to Your New Programs” instructions. Do not insert project-specific text into the license body.

- [ ] **Step 2: Rewrite the Chinese landing page**

Replace `README.md` with these exact top-level sections:

```markdown
# SwitchNAT

[English](README_EN.md) | 简体中文

> 实验性、非官方、无 Root 的 Nintendo Switch Android 热点网络转发工具。

## 项目简介
## 主要功能
## 工作原理
## 环境要求
## 构建与测试
## 使用方法
## 当前限制
## 安全与隐私
## 技术文档
## 归档版本
## 免责声明
## 许可证
```

The content must distinguish fixed UDP targets, dynamic P2P aliases, STUN-observed Shadowsocks egress behavior, and Nintendo NAT results. Use standard commands `flutter pub get`, `flutter test`, `flutter analyze`, and `flutter build apk --release`; do not reference `.toolchains`.

- [ ] **Step 3: Create the English landing page**

Create `README_EN.md` with the same section order and equivalent technical meaning:

```markdown
# SwitchNAT

English | [简体中文](README.md)

> An experimental, unofficial, rootless Android hotspot traffic relay for Nintendo Switch.

## Overview
## Features
## How It Works
## Requirements
## Build and Test
## Usage
## Current Limitations
## Security and Privacy
## Technical Documentation
## Archived Version
## Disclaimer
## License
```

Use neutral engineering language. Do not translate “Full Cone”, “Restricted Cone”, “Port-Restricted Cone”, “Symmetric”, Shadowsocks method names, Kotlin identifiers, or file paths.

- [ ] **Step 4: Validate README parity and links**

Run:

```bash
rg -n '^## ' README.md README_EN.md
rg -n 'TECHNICAL_DESIGN(_EN)?\.md|LICENSE|codex/archive-initial-dynamic-udp' README.md README_EN.md
git diff --check -- README.md README_EN.md LICENSE
```

Expected: each README has twelve matching top-level content sections; language, technical-document, license, and archive links are present; no whitespace errors are reported.

---

### Task 3: Rewrite the Bilingual Technical Design

**Files:**
- Modify: `docs/TECHNICAL_DESIGN.md`
- Create: `docs/TECHNICAL_DESIGN_EN.md`

**Interfaces:**
- Consumes: current Kotlin/Flutter implementation and terminology established by the READMEs.
- Produces: equivalent Chinese and English explanations of architecture, data flow, lifecycle, diagnostics, testing, and limitations.

- [ ] **Step 1: Re-read the implementation boundaries**

Inspect these sources before writing:

```text
lib/src/state/app_controller.dart
lib/src/config/profile_library.dart
android/app/src/main/kotlin/dev/switchnat/app/vpn/SwitchNatVpnService.kt
android/app/src/main/kotlin/dev/switchnat/app/proxy/HttpConnectProxy.kt
android/app/src/main/kotlin/dev/switchnat/app/udp/LocalUdpEndpointProxy.kt
android/app/src/main/kotlin/dev/switchnat/app/udp/UdpPeerAliasManager.kt
android/app/src/main/kotlin/dev/switchnat/app/diagnostics/ConnectionDiagnosticsCoordinator.kt
android/app/src/main/kotlin/dev/switchnat/app/diagnostics/StunNatTester.kt
android/app/src/main/kotlin/dev/switchnat/app/vpn/LiveUpdateContent.kt
```

Expected: every public documentation claim maps to current code or an explicitly labeled compatibility observation.

- [ ] **Step 2: Rewrite the Chinese technical document**

Use these exact top-level sections in `docs/TECHNICAL_DESIGN.md`:

```markdown
# SwitchNAT 技术设计

## 1. 目标与非目标
## 2. 系统架构
## 3. 配置与控制流
## 4. VPN 与服务生命周期
## 5. TCP 转发路径
## 6. 固定 UDP Endpoint 转发
## 7. 动态 P2P Alias
## 8. Shadowsocks UDP Session 与 NAT 行为
## 9. STUN 诊断
## 10. Android Live Updates
## 11. 诊断与错误处理
## 12. 并发与资源管理
## 13. 安全与隐私边界
## 14. 测试策略
## 15. 当前限制
## 16. 关键源码索引
```

Include one Mermaid flowchart showing Switch → hotspot → TCP/UDP paths → Shadowsocks server → destination. Explicitly state that STUN measures the SS egress mapping/filtering behavior and does not directly determine the Switch console’s NAT grade.

- [ ] **Step 3: Create the English technical document**

Create `docs/TECHNICAL_DESIGN_EN.md` with the equivalent numbered sections:

```markdown
# SwitchNAT Technical Design

## 1. Goals and Non-Goals
## 2. System Architecture
## 3. Configuration and Control Flow
## 4. VPN and Service Lifecycle
## 5. TCP Relay Path
## 6. Fixed UDP Endpoint Relay
## 7. Dynamic P2P Aliases
## 8. Shadowsocks UDP Sessions and NAT Behavior
## 9. STUN Diagnostics
## 10. Android Live Updates
## 11. Diagnostics and Error Handling
## 12. Concurrency and Resource Management
## 13. Security and Privacy Boundaries
## 14. Test Strategy
## 15. Current Limitations
## 16. Key Source Files
```

Keep identifiers, protocol terms, numerical limits, supported AEAD methods, and relative source paths identical to the Chinese version.

- [ ] **Step 4: Validate technical-document parity**

Run:

```bash
rg -n '^## [0-9]+\.' docs/TECHNICAL_DESIGN.md docs/TECHNICAL_DESIGN_EN.md
rg -n '4096|128|512|300|aes-128-gcm|aes-256-gcm|chacha20-ietf-poly1305|VpnService\.protect' docs/TECHNICAL_DESIGN.md docs/TECHNICAL_DESIGN_EN.md
git diff --check -- docs/TECHNICAL_DESIGN.md docs/TECHNICAL_DESIGN_EN.md
```

Expected: both documents have sixteen numbered sections and contain the same limits, methods, and core API terms.

---

### Task 4: Verify the Public Documentation Set

**Files:**
- Inspect: `README.md`
- Inspect: `README_EN.md`
- Inspect: `LICENSE`
- Inspect: `docs/TECHNICAL_DESIGN.md`
- Inspect: `docs/TECHNICAL_DESIGN_EN.md`
- Inspect: all tracked files and pending changes

**Interfaces:**
- Consumes: the complete bilingual documentation set.
- Produces: evidence that documentation is internally consistent, sensitive-data-free, and does not break the existing Flutter project.

- [ ] **Step 1: Scan for placeholders and local-only content**

Run:

```bash
rg -n '\b(TODO|TBD|FIXME|XXX)\b' README.md README_EN.md docs/TECHNICAL_DESIGN.md docs/TECHNICAL_DESIGN_EN.md
rg -n '(/home/|\.toolchains|192\.168\.[0-9]+\.[0-9]+:[0-9]{4,5})' README.md README_EN.md docs/TECHNICAL_DESIGN.md docs/TECHNICAL_DESIGN_EN.md
```

Expected: both commands produce no output.

- [ ] **Step 2: Re-run the full tracked-content security audit**

Run the Task 1 sensitive-value commands again and inspect every match. Expected: no publish-blocking secret or device pairing detail.

- [ ] **Step 3: Run Flutter tests**

Run:

```bash
flutter test
```

Expected: all Flutter tests pass. If an existing unrelated test fails, report it precisely and do not alter product behavior as part of this documentation task.

- [ ] **Step 4: Run Flutter static analysis**

Run:

```bash
flutter analyze
```

Expected: `No issues found!`.

- [ ] **Step 5: Verify the final diff boundary**

Run:

```bash
git diff --check
git status --short
git diff --stat
git diff --name-only
```

Expected: only `README.md`, `README_EN.md`, `LICENSE`, `docs/TECHNICAL_DESIGN.md`, and `docs/TECHNICAL_DESIGN_EN.md` are changed or created.

- [ ] **Step 6: Commit the public documentation set**

Run:

```bash
git add README.md README_EN.md LICENSE docs/TECHNICAL_DESIGN.md docs/TECHNICAL_DESIGN_EN.md
git commit -m "docs: prepare SwitchNAT for open source release"
```

Expected: one documentation-and-license commit with no product source changes.

---

### Task 5: Integrate and Publish Both Branches

**Files:**
- Modify: local Git remote configuration only
- Publish: `master`
- Publish: `codex/archive-initial-dynamic-udp`

**Interfaces:**
- Consumes: verified documentation commit, clean current and archive trees, local backup refs, and a non-conflicting GitHub remote.
- Produces: GitHub branches whose commit IDs match the verified local refs.

- [ ] **Step 1: Preserve private history and verify archive source integrity**

Run:

```bash
git status --short --branch
git rev-parse master
git rev-parse codex/archive-initial-dynamic-udp
git -C .worktrees/archive-initial-dynamic-udp status --short --branch
```

Expected: both worktrees are clean before sanitization. Create local-only backup refs for the exact current and archive histories; never include those refs in any push command.

- [ ] **Step 2: Create clean public root snapshots**

Remove internal reports and replace local absolute paths in the archive documentation, then create parentless commits for the current tree and archive tree. Confirm `git rev-list --parents -n 1` prints only each new commit ID and that the public refs contain no local identifiers.

- [ ] **Step 3: Configure `origin` safely**

Run:

```bash
git remote get-url origin
```

If `origin` does not exist, run:

```bash
git remote add origin https://github.com/watyfges/SwitchNAT.git
```

If `origin` exists with a different URL, stop and report it. Do not replace it automatically.

- [ ] **Step 4: Fetch and compare before pushing**

Run:

```bash
git fetch origin --prune
git ls-remote --heads origin
```

Expected: remote `main` still equals `c1d8aac574fe1d906ede38088f8cfede2f8287eb`. Any other value stops publication because the lease is no longer valid.

- [ ] **Step 5: Replace the authorized initial `main` with a lease**

Run:

```bash
git push --force-with-lease=main:c1d8aac574fe1d906ede38088f8cfede2f8287eb origin master:main
```

Expected: remote `main` is replaced only if its previous value is the audited initial commit.

- [ ] **Step 6: Push local `master` as a matching remote branch**

Run:

```bash
git push -u origin master
```

Expected: remote `master` is created without force and upstream tracking is configured.

- [ ] **Step 7: Push the sanitized archive snapshot without force**

Run:

```bash
git push -u origin codex/archive-initial-dynamic-udp
```

Expected: the archive branch is created at its new parentless clean snapshot commit; the original archive commit remains available only through its local backup ref.

- [ ] **Step 8: Verify remote commit IDs and root history**

Run:

```bash
git ls-remote --heads origin main master codex/archive-initial-dynamic-udp
git status --short --branch
```

Expected: remote `main` and `master` exactly match `git rev-parse master`, remote archive exactly matches `git rev-parse codex/archive-initial-dynamic-udp`, both public histories are parentless snapshots, and the local worktree remains clean.
