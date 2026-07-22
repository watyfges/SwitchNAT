# SwitchNAT Compact UI and Live Update Polish Design

**Date:** 2026-07-18
**Status:** Approved

## Goal

Polish the existing four-page app without changing its forwarding architecture. The update fixes the Home metric-card mismatch and truncation, removes the Config overflow and visual clutter, and makes the standard Android Live Update concise enough for HyperOS to present cleanly.

## Chosen Visual Direction

Use the selected **A: compact layered** direction. Keep the current green Material language and card-based hierarchy, but reduce oversized headings, excessive vertical padding, and competing controls. Status and Logs keep their current structure.

## Home

- Keep the master switch and current-node card as the primary controls.
- Render the Switch and SS UDP NAT cards as two stacked, full-width horizontal cards.
- Give both cards the same internal structure: icon on the left, then label, main state, and secondary detail.
- Allow the NAT type and mapped endpoint to use up to two lines with soft wrapping; they must never use a single-line ellipsis.
- Keep the NAT card tappable while connected so it reruns the STUN test.
- Keep the current-node card tappable while connected so it reruns the SS TCP test.
- On narrow screens, each card uses the full available width and grows vertically rather than clipping content.

## Config

- Replace the large `Server profiles` heading with the smaller page title `Node config`.
- Replace the overflowing profile dropdown with a compact profile summary card:
  - profile name is the primary line;
  - server endpoint is a secondary, safely ellipsized line;
  - Add remains a visible icon button;
  - Duplicate, Rename, and Delete move into one overflow menu.
- Keep server host, port, HTTP proxy, password, and encryption directly editable on the page, grouped under a `Server` section.
- Use compact field density while preserving touch targets and password masking.
- Present bootstrap UDP targets and global STUN servers as compact `Advanced` rows. Tapping a row expands its existing editor in place; no new navigation level is introduced.
- Keep one full-width `Save node` button after the advanced section.
- Long profile names and endpoints may ellipsize only in the summary card. Editable field values remain accessible through normal text-field scrolling.

## Standard Android Live Update

- Continue using the promoted ongoing notification path; do not add Xiaomi-private APIs.
- Replace the generic Android upload status icon with a dedicated monochrome SwitchNAT transfer icon.
- Use a stable short title: `SwitchNAT connecting` or `SwitchNAT connected`. Never put a hostname in the title.
- Use the content line for the two most useful runtime states: Switch presence and NAT type.
- Use the notification chronometer, based on `sessionStartedAtMillis`, for session duration.
- Use expanded text for secondary details only: selected profile name, learned Switch IP, mapped SS UDP endpoint, and SS TCP latency.
- Stopping the VPN still removes the ongoing notification immediately.
- HyperOS controls the final island rendering. The app supplies concise standard Android metadata so the island receives a short title, proper icon, state text, and duration without relying on vendor extensions.

## Error Handling

- Missing diagnostic values use short stable states such as `Waiting`, `Testing`, `Partial`, or `Unavailable`; empty fields do not create blank lines.
- Profile actions remain disabled while the controller is busy.
- Delete remains disabled when only one profile exists.
- Notification formatting failures must never interrupt packet forwarding.

## Verification

- Add a Flutter widget regression test at a narrow phone width that asserts equal Home metric-card heights and non-ellipsized NAT detail.
- Add Config widget tests with a long profile name and hostname and assert there are no Flutter overflow exceptions.
- Update `LiveUpdateContent` unit tests for stable titles, concise state text, optional detail lines, and session start time.
- Run all Flutter tests, Flutter analysis, Android unit tests, and a debug APK build.
- Install the final APK on the paired phone and verify Home, Config, expanded notification, HyperOS island promotion metadata, and connect/disconnect lifecycle.

## Out of Scope

- No forwarding, dynamic UDP learning, profile persistence, or STUN server behavior changes are part of this visual revision.
- No custom `RemoteViews`, Xiaomi notification protocol, or root-only integration.
