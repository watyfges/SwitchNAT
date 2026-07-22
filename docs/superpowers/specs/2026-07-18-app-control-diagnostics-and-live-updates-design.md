# App Control, Diagnostics, and Live Updates Design

## Goal

Turn the proven no-root SwitchNAT forwarding core into a four-page daily-use
app with named Shadowsocks nodes, reliable bootstrap UDP defaults, proxy-aware
connectivity and NAT diagnostics, accurate Switch presence, and an Android
standard Live Update that exists only while acceleration is connected.

The already validated TCP forwarding, static UDP bootstrap forwarding, and
dynamic UDP peer-alias implementation remain the data plane. This design
changes configuration, diagnostics, presentation, and notification lifecycle;
it does not replace the working forwarding architecture.

## Product Decisions

- The app has four bottom-navigation pages: Home, Config, Status, and Logs.
- Home owns the master switch and everyday status. Config owns configuration.
- Status and Logs retain their current visual character and detailed content.
- Multiple named Shadowsocks profiles are supported.
- Selecting a different profile never restarts an active VPN automatically.
- UDP targets remain per-profile bootstrap rules. Dynamic peer learning handles
  later P2P endpoints but does not replace the bootstrap list.
- The complete captured UDP target list is embedded as the default.
- STUN servers are one global ordered list shared by every profile.
- Starting acceleration automatically runs the SS TCP connectivity test and
  the SS UDP STUN/NAT test.
- Tapping the NAT card reruns only the STUN/NAT test.
- The app implements only Android standard Live Updates. It does not add Xiaomi
  private notification extras, App IDs, MiPush, or platform whitelist logic.
- All runtime behavior remains no-root.

## Information Architecture

### Home

Home is the operational dashboard and contains:

1. A master acceleration card with the current VPN state and a large Start or
   Stop control.
2. A current-node card showing the active profile while running, or the
   selected profile while stopped, plus its server endpoint, most recent SS
   TCP availability result, and latency. There is no separate redundant Node
   Health card.
3. A Switch card showing `Waiting for Switch`, `Connected`, or `No recent
   activity`, plus the learned IP and last-active time when known.
4. An SS UDP NAT card showing `Testing`, a legacy cone label and mapped public
   endpoint, `Partial result`, or `Unavailable`. Tapping the card reruns the
   test while the VPN is running.

If the selected profile differs from the profile that started the active VPN,
Home shows that the change is pending and requires a manual restart. The
displayed runtime endpoint and health always belong to the active profile, not
the profile currently being edited.

### Config

Config contains three focused sections:

1. **Server profiles**: select, add, duplicate, rename, delete, and edit named
   Shadowsocks profiles.
2. **Bootstrap UDP targets**: edit the selected profile's rules, inspect the
   expanded endpoint count, validate input, and restore embedded defaults.
3. **Global STUN servers**: add, edit, delete, enable, disable, and reorder
   shared STUN endpoints. A restore action reinstates the bundled presets.

The server form retains host, server port, password, encryption method, and
local HTTP proxy port. Saving changes the selected profile only. The running
profile cannot be deleted. Changing or saving a profile never mutates the
running native configuration.

### Controls and button behavior

- Home has one visually dominant circular Start/Stop control. It is disabled
  only while STARTING or STOPPING.
- Tapping `Change` on the node card opens profile selection. Selection is
  immediate, but an active mismatch produces a restart-required message rather
  than an automatic restart.
- Tapping the availability/latency area of the current-node card reruns only
  the SS TCP connectivity test while RUNNING.
- The entire NAT card is tappable while RUNNING and not already testing. It
  shows inline progress during a rerun.
- Config exposes `Add`, `Duplicate`, `Rename`, and an overflow menu containing
  Delete. Destructive actions require confirmation and respect active/only
  profile guards.
- `Save profile` is the only action that persists field edits. Navigating away
  with unsaved edits asks whether to discard them.
- UDP targets and global STUN servers each have `Restore defaults`; restoration
  requires a subsequent Save.
- Status is read-only. Logs keeps one `Copy logs` icon action.

### Status

Status keeps the current sectioned diagnostic style. It adds:

- selected and active profile identity;
- connection start time and duration;
- SS TCP test state, latency, completion time, and error;
- STUN classification, mapped endpoint, mapping behavior, filtering behavior,
  RTT, chosen STUN server, completion time, and capability limitations;
- learned Switch IP and last packet time;
- Android Live Update eligibility and last publication state.

Existing gateway, proxy address, UDP counters, relay-flow counts, peer-alias
counts, TCP connection count, and latest error remain.

### Logs

Logs keeps the current recent-packet list and copy action. It adds structured
events for profile activation, test start/result/failure, Switch presence
transitions, and Live Update publication or fallback. Passwords, SS keys, and
packet payloads are never logged.

## Profile Persistence

Android remains the persistent source of truth using the existing
`switchnat_config` SharedPreferences file. A versioned JSON library stores:

- `profiles`: named profile records with stable generated IDs;
- `selectedProfileId`: the profile currently shown in Config and chosen for
  the next start;
- `stunServers`: the global ordered STUN endpoint list and enabled state;
- a schema version for migrations.

Each profile contains its display name and all `ShadowsocksConfig` fields,
including its UDP target text. The default display name for a new unnamed
profile is `host:port`.

When no profile library exists, the existing single saved configuration is
migrated into one profile without changing any saved values. Existing nonempty
UDP target text is preserved. A blank legacy UDP target field is also
preserved, because silently changing an existing user's runtime routes would
be unsafe; the user may explicitly choose Restore Defaults.

New profiles start with the embedded default UDP targets. The embedded file
contains the 516 captured source rules read from the accepted device
configuration, expanding to 573 concrete endpoints. It is stored as an Android
raw text resource so native validation, migration, and Flutter presentation use
one canonical value. Restore Defaults replaces only the editor contents and
does not persist until Save is pressed.

The initial global STUN presets are:

- `stun.cloudflare.com:3478`
- `stun.l.google.com:19302`
- `stun1.l.google.com:19302`
- `global.stun.twilio.com:3478`

Presets are ordinary editable records after creation. At least one valid STUN
endpoint must remain enabled for automatic NAT testing; otherwise startup
records `No STUN server enabled` without failing acceleration.

## Selected Versus Active Profile

The selected profile is configuration intent. The active profile is an
immutable runtime snapshot copied into the VPN start intent. Diagnostics stores
the active profile ID, display name, and server endpoint before initialization
and clears them after a complete stop or failed start.

Selecting another profile while running updates only `selectedProfileId` and
the editor. The master switch continues to control the existing session. To
apply a different profile, the user deliberately stops and starts again. This
avoids interrupting a game and makes the notification, Home, and Status views
truthful about which node carries traffic.

## SS TCP Connectivity Test

The connectivity test must exercise the encrypted Shadowsocks TCP path rather
than merely opening a socket to the SS server port.

1. Resolve the active server using the same resolver as the VPN runtime.
2. Establish a protected TCP socket and complete the Shadowsocks encrypted
   target request through `ShadowsocksTcpClient`.
3. Send `HEAD /` to `1.1.1.1:80` with `Host: one.one.one.one` through the
   tunnel. The target is fixed so the test does not depend on phone-side DNS.
4. Treat receipt of a syntactically valid HTTP status line as success and
   record end-to-end latency.

DNS resolution, SS connection, encrypted relay setup, write, and response read
use separate bounded timeouts. A test failure updates diagnostics and Home but
never stops an already running VPN. Tests are single-flight: a new manual test
cancels or supersedes the prior result instead of running concurrently.

Startup launches TCP and STUN diagnostics after the VPN has reached RUNNING.
They may run concurrently because they use independent TCP and UDP relay
clients. Selecting a different profile while running does not redirect these
tests; they always use the active profile snapshot.

## SS UDP STUN and NAT Classification

Every STUN datagram is sent through a protected Shadowsocks UDP relay using the
active profile. The result therefore describes the SS server's UDP egress
mapping and filtering, not the phone carrier network or local hotspot.
All probes in one classification attempt reuse one UDP relay session; creating
a fresh SS UDP socket for each destination would invalidate mapping comparison.

The tester tries enabled global STUN servers in order. It prefers the first
server that supports the complete RFC 5780 sequence, but retains the first
valid Binding-only response as a partial fallback if no server supports full
classification. It validates transaction IDs, message framing, response class,
and XOR-MAPPED-ADDRESS before accepting a result. Retries and timeouts are
bounded, and only one NAT test may be active. The bundled public presets are
reachability candidates, not a promise that every operator exposes RFC 5780;
the custom list is the authority.

When the server supports the RFC 5780 behaviors needed for a complete test,
the app derives modern mapping and filtering properties and presents the
requested legacy labels:

| Mapping behavior | Filtering behavior | Headline label |
| --- | --- | --- |
| Endpoint-independent | Endpoint-independent | Full Cone |
| Endpoint-independent | Address-dependent | Restricted Cone |
| Endpoint-independent | Address-and-port-dependent | Port-Restricted Cone |
| Address- or address-and-port-dependent | Any measured behavior | Symmetric NAT |

The card also displays the mapped public `IP:port`. Status retains the exact
mapping and filtering terms, RTT, server, and test time.

A basic STUN Binding server may reveal a mapped endpoint without supporting
the alternate-address and changed-response tests required for filtering
classification. In that case the app reports `Partial result`, shows the
mapped endpoint and any mapping evidence, and marks filtering as `Unknown`.
It must never invent a cone label from a Binding response alone.

## Switch Presence

Diagnostics gains `lastSwitchActivityMillis`. Both accepted local HTTP proxy
connections and accepted local UDP datagrams update the learned Switch IP and
activity time, so the app does not depend on UDP traffic alone.

Presentation derives three states:

- `Waiting for Switch`: no local Switch endpoint has been observed in this VPN
  session.
- `Connected`: Switch traffic was observed within the recent-activity window.
- `No recent activity`: an IP was learned but no accepted traffic arrived
  within the window.

The initial recent-activity window is 60 seconds. A new packet immediately
returns the state to Connected. The learned IP and timestamp reset when a new
VPN session starts so stale device data is not presented as current.

## Android Live Update

### Compatibility strategy

The existing VPN foreground notification is reused. No second notification is
created. AndroidX `NotificationCompat` explicitly requests promoted ongoing
presentation; unsupported systems or systems that decline promotion naturally
show the same object as a normal VPN foreground notification.

The manifest declares:

- runtime `android.permission.POST_NOTIFICATIONS` for Android 13 and later;
- non-runtime `android.permission.POST_PROMOTED_NOTIFICATIONS` for Live Update
  eligibility.

Notification permission denial does not block VPN startup. It only limits the
system surfaces on which the foreground notification or Live Update can appear.
The channel remains silent and at least `IMPORTANCE_LOW`, never
`IMPORTANCE_MIN`.

### Component boundary

`ConnectionLiveActivityController` owns notification construction,
deduplication, publication, and cancellation. It accepts a small immutable
presentation model and has no dependency on packet forwarding classes.
`SwitchNatVpnService` invokes lifecycle operations but does not build JSON,
styles, or display strings.

While connected, the controller samples the shared diagnostic snapshot about
every two seconds and republishes only when user-visible state changes. Android
renders elapsed connection time with a chronometer, so there is no per-second
notification update.

### Lifecycle

- STARTING: post the required ordinary foreground notification.
- RUNNING: update the same notification ID with an ongoing Live Update request.
- RUNNING state change: update node, Switch state, or NAT state only when the
  rendered model differs.
- STOPPING, STOPPED, FAILED, permission revocation, or service destruction:
  stop sampling and remove the foreground notification.

The Live Update title is `SwitchNAT acceleration active`. Its compact content
prioritizes the active node and Switch state. Expanded standard content adds
the NAT result. Tapping opens Home. There is no notification Stop action in
this scope; disconnect remains a deliberate in-app action.

No custom `RemoteViews`, Xiaomi private extras, App ID, MiPush dependency,
brand check, or device whitelist logic is included.

## State and Data Flow

```text
Home Start
  -> selected profile snapshot
  -> VPN permission and optional notification permission
  -> SwitchNatVpnService STARTING
  -> TCP proxy + UDP bootstrap + dynamic aliases ready
  -> RUNNING
     -> publish Android Live Update
     -> run SS TCP test
     -> run SS UDP STUN test

TCP/UDP local traffic
  -> learned Switch IP + last activity
  -> DiagnosticsSnapshot
  -> Home / Status / Logs
  -> deduplicated Live Update refresh

Home Stop / failure / revoke
  -> close forwarding resources
  -> stop Live Update sampler
  -> remove foreground notification
  -> clear active session identity
```

Flutter consumes the existing broadcast status stream. Native diagnostics
remains the canonical runtime state so Home, Status, Logs, and the Android
notification cannot disagree about the active session.

## Error Handling

- Invalid profiles or UDP targets are rejected before Save and Start.
- Migration repairs malformed or duplicate IDs while preserving every valid
  profile.
- The only profile and the active profile cannot be deleted.
- A failed start with a newly selected profile leaves that selection intact and
  does not claim it became active.
- TCP/STUN failures remain diagnostic failures, not VPN failures.
- STUN servers that return malformed, mismatched, or unsupported responses are
  skipped or reported without a fabricated NAT classification.
- Notification permission denial and Live Update promotion refusal never stop
  forwarding.
- Notification construction or refresh exceptions are logged and isolated from
  TCP/UDP worker threads.
- Dynamic UDP alias failures retain the per-peer failure isolation defined by
  the existing dynamic peer-alias design.

## Testing

### Dart and widget tests

- Four-destination navigation and state preservation.
- Home master-switch states, active/selected mismatch warning, Switch presence,
  automatic-test states, and tappable NAT retest.
- Profile CRUD, legacy migration presentation, delete guards, validation, and
  Restore Defaults behavior.
- Global STUN list editing and ordering.
- Status additions and existing Logs behavior.

### Android JVM tests

- Versioned profile serialization, legacy migration, and default-target loading.
- Profile identity carried from selection into the immutable runtime snapshot.
- TCP diagnostic success, timeout, malformed response, and single-flight rules
  with fake sockets.
- STUN parsing, transaction validation, retry/fallback, RFC 5780 mapping and
  filtering branches, legacy-label mapping, partial results, and cancellation.
- Switch activity transitions with a controllable clock.
- Live Update presentation mapping, visible-state deduplication, lifecycle,
  permission-independent VPN behavior, and cancellation.

### Regression and device verification

1. Run Flutter analysis, Flutter tests, Android JVM tests, and build a debug APK.
2. Upgrade-install without clearing app data and verify legacy configuration
   migration.
3. Confirm the embedded target list contains 516 rules and expands to 573
   endpoints.
4. Start acceleration and verify the active node, automatic TCP result, STUN
   result, mapped endpoint, and Switch state on Home and Status.
5. Verify NAT A and public IP with the Switch connection test.
6. Join and sustain a Mario Kart 8 room; repeat endpoint-change behavior in a
   P2P game without restarting acceleration.
7. Confirm HyperOS presents the Android Live Update when the system allows it,
   updates it when Switch/NAT state changes, and removes it immediately after
   disconnect.
8. Deny notification permission and confirm forwarding still works.
9. Select another profile while running and confirm no traffic interruption or
   false active-node display occurs until manual stop/start.

## Acceptance Criteria

- Home is sufficient for normal daily start, observation, NAT retest, and stop.
- Config can safely maintain multiple named SS profiles, per-profile bootstrap
  targets, and one global editable STUN list.
- Existing configuration survives migration without silent route changes.
- New profiles receive the captured 516-rule default target list.
- The TCP test proves the encrypted SS TCP path and reports latency.
- The STUN test runs through SS UDP, reports the mapped endpoint, and never
  overstates classification when the server lacks RFC 5780 capability.
- Switch presence reflects current TCP or UDP activity rather than stale saved
  data.
- Starting acceleration automatically begins both diagnostics.
- The NAT card reruns STUN testing when tapped.
- The Android standard Live Update appears only for a RUNNING session when the
  OS permits promotion, degrades safely to the foreground notification, and is
  removed on every disconnect or failure path.
- TCP forwarding, static UDP bootstrap, dynamic UDP peer learning, NAT A, and
  no-root operation do not regress.

## Out of Scope

- Xiaomi private Super Island APIs, MiPush, App IDs, or whitelist enrollment.
- Automatic fastest-node selection or failover.
- Applying a newly selected profile without a manual restart.
- Payload inspection or game-specific protocol parsing.
- Root, hotspot framework hooks, or privileged packet capture.
- Import/export and cloud synchronization of profiles.

## Protocol References

- Android Live Updates:
  <https://developer.android.com/develop/ui/views/notifications/live-update>
- STUN protocol, RFC 8489: <https://www.rfc-editor.org/rfc/rfc8489.html>
- NAT behavior discovery, RFC 5780:
  <https://www.rfc-editor.org/rfc/rfc5780.html>
