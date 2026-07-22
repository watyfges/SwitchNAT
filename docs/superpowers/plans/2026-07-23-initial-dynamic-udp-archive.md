# Initial Dynamic UDP Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover the first device-tested dynamic UDP implementation onto a dedicated branch descending from `847201b`, while leaving the current complete `master` checkout unchanged.

**Architecture:** Use the Git root tree `11db9e14d1b5d10418639b80e96d5aeb06d6e90d` as the historical source of truth. Apply the exact delta between `847201b` and that tree inside the isolated archive worktree, preserve only the archive design and plan as additional files, then verify the recovered Android and Flutter projects without installing the archive APK over the current phone app.

**Tech Stack:** Git worktrees and object database, Kotlin/JVM 17, Android Gradle, Flutter/Dart, JUnit 4.

## Global Constraints

- Work only in `.worktrees/archive-initial-dynamic-udp`.
- Keep the primary checkout on `master`; do not reset, restore, rebase, or edit it.
- The archive branch is `codex/archive-initial-dynamic-udp` and descends from `847201b81736dfb436021ace27d61b567a8680d0`.
- The recovered product files come from root tree `11db9e14d1b5d10418639b80e96d5aeb06d6e90d`.
- Do not import later profiles, STUN, Live Updates, or four-page UI code.
- Do not install the archive APK on the phone because that would replace the current complete application.
- Any compatibility fix must be minimal, test-backed, and committed separately from the recovered snapshot.

---

### Task 1: Materialize the Historical Working Tree

**Files:**
- Modify/Delete/Create: exactly the paths in `git diff --name-status 847201b^{tree} 11db9e14d1b5d10418639b80e96d5aeb06d6e90d`
- Preserve: `docs/superpowers/specs/2026-07-23-initial-dynamic-udp-archive-design.md`
- Preserve: `docs/superpowers/plans/2026-07-23-initial-dynamic-udp-archive.md`

**Interfaces:**
- Consumes: baseline commit tree `847201b^{tree}` and recovered root tree `11db9e14…`.
- Produces: one recovery commit whose product files match the recovered root tree exactly.

- [ ] **Step 1: Verify isolation and clean inputs**

Run:

```bash
git branch --show-current
git status --short
git merge-base --is-ancestor 847201b81736dfb436021ace27d61b567a8680d0 HEAD
git cat-file -t 11db9e14d1b5d10418639b80e96d5aeb06d6e90d
```

Expected: branch is `codex/archive-initial-dynamic-udp`, status contains only this uncommitted plan, the ancestry command exits 0, and the object type is `tree`.

- [ ] **Step 2: Commit this implementation plan**

Run:

```bash
git add docs/superpowers/plans/2026-07-23-initial-dynamic-udp-archive.md
git commit -m "docs: plan initial dynamic UDP archive"
```

Expected: one documentation-only commit.

- [ ] **Step 3: Apply the recovered delta without touching archive docs**

Run from the archive worktree:

```bash
git diff --binary 847201b81736dfb436021ace27d61b567a8680d0^{tree} 11db9e14d1b5d10418639b80e96d5aeb06d6e90d | git apply --index
```

Expected: the index contains the historical implementation changes while the archive design and plan remain present.

- [ ] **Step 4: Verify the staged file boundary**

Run:

```bash
git diff --cached --check
git diff --cached --name-status
```

Expected: no whitespace errors. The staged paths match the historical delta and contain no `ProfileRepository.kt`, `StunNatTester.kt`, `LiveUpdateContent.kt`, or `lib/src/screens/home_screen.dart`.

- [ ] **Step 5: Commit the recovered snapshot**

Run:

```bash
git commit -m "feat: recover initial dynamic UDP implementation"
```

Expected: a commit containing the recovered dynamic UDP implementation.

- [ ] **Step 6: Prove historical tree fidelity**

Run:

```bash
git diff --name-status 11db9e14d1b5d10418639b80e96d5aeb06d6e90d HEAD
```

Expected: only the two 2026-07-23 archive design/plan files are listed.

---

### Task 2: Verify the Recovered Android and Flutter Baseline

**Files:**
- Inspect: `android/app/src/main/kotlin/dev/switchnat/app/udp/LocalUdpEndpointProxy.kt`
- Inspect: `android/app/src/main/kotlin/dev/switchnat/app/udp/UdpPeerAliasManager.kt`
- Test: `android/app/src/test/kotlin/dev/switchnat/app/udp/LocalUdpEndpointProxyTest.kt`
- Test: `android/app/src/test/kotlin/dev/switchnat/app/udp/UdpPeerAliasManagerTest.kt`
- Test: `test/screens/status_screen_test.dart`

**Interfaces:**
- Consumes: the recovery commit from Task 1.
- Produces: verified native tests, Flutter tests, static analysis, and a buildable debug APK inside the archive worktree.

- [ ] **Step 1: Run the Android JVM suite**

Run:

```bash
cd android
JAVA_HOME=.toolchains/jdk17 PATH=.toolchains/jdk17/bin:/usr/bin:/bin ./gradlew :app:testDebugUnitTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run all Flutter tests**

Run from the archive worktree root:

```bash
.toolchains/flutter/bin/flutter test
```

Expected: all tests pass.

- [ ] **Step 3: Run Flutter static analysis**

Run:

```bash
.toolchains/flutter/bin/flutter analyze
```

Expected: `No issues found!`.

- [ ] **Step 4: Build without installing**

Run:

```bash
JAVA_HOME=.toolchains/jdk17 ANDROID_HOME=.toolchains/android-sdk PATH=.toolchains/jdk17/bin:.toolchains/android-sdk/platform-tools:/usr/bin:/bin .toolchains/flutter/bin/flutter build apk --debug
```

Expected: `build/app/outputs/flutter-apk/app-debug.apk` is created in the archive worktree. Do not run `adb install`.

---

### Task 3: Characterize an Historical Build Blocker if One Exists

**Files:**
- No product file changes during this task.
- Append: `docs/superpowers/plans/2026-07-23-initial-dynamic-udp-archive.md` only after the failing path and exact correction are known.

**Interfaces:**
- Consumes: a concrete failure from Task 2.
- Produces: concrete evidence and an exact test-first addendum before any compatibility edit.

- [ ] **Step 1: Record the exact failing command and first actionable error**

Run the failing Task 2 command again with full output. Expected: the same error reproduces consistently.

- [ ] **Step 2: Confirm whether the failure is historical behavior or toolchain compatibility**

Confirm that the recovered product files still match the historical tree:

```bash
git diff --name-status 11db9e14d1b5d10418639b80e96d5aeb06d6e90d HEAD
```

Expected: only the two archive documentation files differ. If the problem is a behavioral test failure rather than compilation/toolchain drift, stop and preserve it as historical evidence instead of changing code automatically.

- [ ] **Step 3: Write an exact compatibility addendum when justified**

Append a new task to this plan naming the exact failing file, exact regression test, minimal code change, verification command, and separate commit message. Review that addendum against the archive scope before editing product code.

- [ ] **Step 4: Execute the addendum test-first and re-run Task 2**

Expected: the focused test fails before the compatibility edit, passes afterward, and Android tests, Flutter tests, analysis, and the debug build all succeed.

- [ ] **Step 5: Commit compatibility work separately**

Stage only the exact files listed in the addendum and commit with message `fix: keep initial dynamic UDP archive buildable`. Expected: the compatibility fix is distinct from the recovered snapshot commit.

If Task 2 passes without changes, skip Task 3 entirely.

---

### Task 4: Verify Archive and Current-Version Isolation

**Files:**
- No product file changes.

**Interfaces:**
- Consumes: the completed archive branch.
- Produces: final evidence that the archive is usable and `master` stayed untouched.

- [ ] **Step 1: Verify archive history and cleanliness**

Run in the archive worktree:

```bash
git status --short --branch
git log --oneline --decorate -5
git merge-base --is-ancestor 847201b81736dfb436021ace27d61b567a8680d0 HEAD
```

Expected: branch is clean, recent history contains the archive docs and recovered implementation, and ancestry exits 0.

- [ ] **Step 2: Verify the current complete checkout**

Run in the primary checkout:

```bash
git branch --show-current
git status --short --branch
git rev-parse HEAD
```

Expected: branch is `master`, working tree is clean, and HEAD remains `8fa184be791d8f856b096c020db763d532f3ead9`.

- [ ] **Step 3: Report the archive branch and artifact**

Report branch `codex/archive-initial-dynamic-udp`, the recovered snapshot commit, any separate compatibility commit, test results, and the archive debug APK path. State explicitly that the APK was not installed over the current phone app.
