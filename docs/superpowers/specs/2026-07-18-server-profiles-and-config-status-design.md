# Server Profiles and Config Status Design

## Goal

Allow SwitchNAT to keep multiple named Shadowsocks configurations without
interrupting an active game when the user selects another profile. The Config
page must also show whether the VPN is running and which profile started it.

## Scope

- Store, select, create, duplicate, rename, and delete named server profiles.
- Preserve the existing single configuration by migrating it on first load.
- Show VPN state, active profile, and edited profile in Config.
- Make a profile change take effect only after a deliberate Start or Restart.

Import/export, synchronization, and automatic server health selection are out
of scope.

## Persistence

Android remains the source of truth. A JSON profile library is stored in the
existing `switchnat_config` SharedPreferences file:

- `profiles`: list of profile records.
- `selectedProfileId`: profile shown in Config.
- Existing individual SS preference keys are read once if no profile library
  exists, converted into one profile, then retained only for compatibility.

Each profile contains a generated stable ID, a non-empty display name, and the
existing `ShadowsocksConfig` fields. The initial display name is
`serverHost:serverPort`.

The native bridge exposes profile-oriented operations: load library, save a
profile, select a profile, create by copying an optional source profile,
rename, and delete. Invalid profile data is rejected using the existing
validation rules.

## VPN State

Diagnostics gains the ID and display name of the profile used by the currently
running VPN. The values are set before starting the service and cleared only
after it fully stops or fails to start. They are included in the existing
status event payload.

The bridge passes the selected profile identity with the configuration to the
VPN controller. Runtime status therefore remains accurate even if Config is
subsequently switched to edit another profile.

## Config Page

The top of Config contains a compact status card:

- VPN run state.
- Running profile, when one exists.
- Current editing profile.
- A warning when the two differ: the edit has not been applied.

Below it, a profile selector opens the profile list. Each row shows the name
and server endpoint. The user can add a blank profile, duplicate the selected
profile, rename it, or delete it. The running profile cannot be deleted.

Changing the selected profile only loads that profile into the form; it does
not affect the VPN. Saving updates the selected profile only. Start starts the
selected profile when stopped. While another profile is active, the control is
labelled “Restart with this profile”; selecting it explicitly stops and starts
the VPN with the selected profile.

## Error Handling

- Missing, malformed, or duplicate stored IDs are repaired during load without
  discarding valid profile configurations.
- A profile cannot be deleted if it is the only profile or the active VPN
  profile.
- Save, profile mutation, and restart failures remain visible in the Config
  feedback area.
- Profile selection is preserved if a restart request fails.

## Tests

- Dart model and store tests cover profile serialization and selected-profile
  behavior.
- Android tests cover legacy migration, CRUD persistence, validation, and
  active-profile diagnostics.
- Widget tests cover the status card, mismatch warning, manual restart label,
  and destructive-action guards.

