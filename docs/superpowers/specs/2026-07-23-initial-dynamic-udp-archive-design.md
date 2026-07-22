# Initial Dynamic UDP Archive Design

**Date:** 2026-07-23

## Objective

Preserve the first device-tested dynamic UDP implementation on a dedicated
branch without changing, resetting, or rebasing the current complete version
on `master`.

## Historical Evidence

- The archive branch starts at commit
  `847201b81736dfb436021ace27d61b567a8680d0`, which contains the dynamic UDP
  peer-alias implementation plan but not the uncommitted implementation.
- Git's unreachable object database contains root tree
  `11db9e14d1b5d10418639b80e96d5aeb06d6e90d`, created at
  2026-07-18 00:49:25 +0800.
- That tree contains `LocalUdpEndpointProxy`, `UdpPeerAliasManager`, their JVM
  tests, dynamic UDP diagnostics, the original Flutter screens, and the
  technical design.
- It does not contain later server profiles, STUN diagnostics, Live Updates,
  or the redesigned four-page UI. The next committed work, one minute later,
  starts the server-profile design phase.

This timing and component boundary make the unreachable tree the closest
recoverable representation of the first successfully tested implementation.

## Recovery Architecture

Work is isolated in `.worktrees/archive-initial-dynamic-udp` on branch
`codex/archive-initial-dynamic-udp`.

The recovered tree is applied over the `847201b` baseline. This design file is
the only intentional addition to the historical tree. The recovery commit
message records the source tree hash so the reconstruction remains auditable.

The current `master` checkout is never switched, reset, or modified.

## Scope Boundary

The archive includes:

- static UDP bootstrap forwarding;
- per-Switch-source Shadowsocks UDP relay reuse;
- dynamic peer alias allocation and reverse forwarding;
- alias expiry and resource limits;
- dynamic UDP diagnostics and the original status-screen additions;
- the tests and technical documentation present in the recovered tree.

The archive excludes:

- multiple Shadowsocks node profiles;
- STUN NAT classification;
- TCP/UDP node health controls;
- Android Live Updates and Xiaomi Super Island integration;
- the later Home/Config/Status/Logs redesign.

## Completion and Error Handling

After recovery, run the Android JVM suite, Flutter tests, and Flutter static
analysis. If toolchain drift or an incomplete historical file prevents the
archive from building, add only the smallest compatibility fix in a separate
commit. Do not copy later product features into the archive.

If tests expose a behavioral failure in dynamic UDP rather than a build-time
compatibility issue, preserve the recovered commit unchanged and document the
failure before deciding whether a second archival-fix commit is appropriate.

## Acceptance Criteria

- The archive branch descends from `847201b`.
- Its recovered product tree differs from
  `11db9e14d1b5d10418639b80e96d5aeb06d6e90d` only by this recovery design file
  and any explicitly documented minimal compatibility commit.
- Dynamic UDP unit tests pass, or any historical failure is recorded without
  silently importing later code.
- `master` remains at its original commit with a clean working tree.
