# Dynamic UDP Peer Alias Design

## Objective

Allow Nintendo Switch games to exchange UDP with peers whose public `IP:port`
changes at runtime, without root access, without rebuilding the Android VPN,
and without asking the user to leave and rejoin a room.

The existing TCP path and Flutter visual style remain unchanged during this
phase. Multi-server configuration and config-screen status improvements are a
separate follow-up phase after the UDP path has passed real-device P2P tests.

## Feasibility Conclusion

The design is feasible with the current runtime model:

- `LocalUdpEndpointProxy` already learns the Switch source `IP:port` from a
  packet sent to a configured seed endpoint.
- A single `UdpRelaySession` is already reused for all destinations contacted
  from that Switch source endpoint. This preserves the public UDP mapping that
  produced NAT A in the connection test.
- A decoded Shadowsocks UDP response already contains the actual remote source
  endpoint, including sources absent from the configured endpoint list.
- Runtime evidence showed unsolicited packets from a changing player endpoint
  reaching the existing relay session. The only current failure is that no
  local socket exists with which to deliver those packets to the Switch.
- An unprivileged Android process may bind a UDP socket to the phone's own LAN
  or hotspot address on a high port. That socket can exchange traffic directly
  with the Switch on the local network.

The design therefore virtualizes a dynamic public peer as a local phone
address and port. It does not attempt to add the peer's public IP to the TUN.

## Scope

### In scope

- Learn remote P2P endpoints from packets received by an existing Shadowsocks
  UDP relay session.
- Allocate a Switch-reachable local alias for every learned peer.
- Translate packets in both directions between the local alias and the real
  peer endpoint.
- Reuse the existing relay session for all peers of a Switch UDP source flow.
- Expire inactive aliases and enforce resource limits.
- Publish diagnostics that distinguish relay flows from dynamic peer aliases.
- Preserve the existing behavior for configured seed endpoints.

### Out of scope

- Capturing every hotspot packet with a full user-space IP stack.
- Parsing or modifying game payloads.
- Rebuilding the VPN when a peer appears.
- Modifying the Shadowsocks protocol or server.
- Multi-server profile storage and config-screen layout changes. Those form the
  next independently testable phase.

## Architecture

### Static endpoint interception

Configured UDP targets remain the bootstrap path. `SwitchNatVpnService` adds
their public IPs as `/32` VPN addresses and routes, and
`LocalUdpEndpointProxy` binds their exact `IP:port` sockets. This intercepts
login, matchmaking, NAT test, and initial hole-punch traffic.

### Relay flow

A relay flow remains keyed only by the Switch source endpoint:

```text
FlowKey = switchIp:switchPort
Flow = FlowKey + one UdpRelaySession
```

The flow must not be split by destination. Opening a new Shadowsocks UDP socket
for every peer could change the proxy server's public source port and break the
endpoint-independent mapping required for NAT A.

### Dynamic peer alias

A new `UdpPeerAliasManager` owns dynamic aliases. An alias is scoped to both
the relay flow and the real peer endpoint:

```text
AliasKey = FlowKey + remotePeerIp:remotePeerPort

UdpPeerAlias
  switchEndpoint
  remoteEndpoint
  localAliasEndpoint
  aliasSocket
  relaySession reference
  lastActivityMillis
  receiveThread
```

`localAliasEndpoint` uses the phone address already detected as the hotspot or
LAN gateway plus an operating-system-assigned high port. The alias socket is
connected to the Switch endpoint, which restricts received packets to the
expected Switch source and gives outbound packets a stable local source.

The manager is a separate file and lifecycle unit so endpoint allocation,
expiry, limits, and concurrency can be tested without the VPN service.

## Data Flow

### Switch to a configured endpoint

1. The Switch sends from `A1` to configured endpoint `B`.
2. The exact endpoint socket bound at `B` receives the payload.
3. The proxy gets or creates the relay flow for `A1`.
4. The flow's existing `UdpRelaySession` sends the payload to `B`.

This path is unchanged.

### Response from a configured endpoint

1. The relay session receives a decoded packet whose source is configured
   endpoint `B`.
2. The exact endpoint socket bound at `B` sends the payload to `A1`.
3. The Switch continues to see the real configured source `B`.

This path is unchanged.

### First packet from a dynamic peer

Given:

```text
A1 = Switch source endpoint
A2 = real dynamic peer endpoint
A3 = phone LAN address and newly allocated high port
```

1. The existing relay flow for `A1` receives a decoded packet from unknown
   source `A2`.
2. `UdpPeerAliasManager` looks up `(A1, A2)`.
3. If absent, it binds a new UDP socket to `phoneLanIp:0`, connects it to `A1`,
   records the kernel-selected endpoint as `A3`, starts its receive loop, and
   installs the mapping atomically.
4. The alias socket sends the payload to `A1`.
5. The Switch observes the packet source as `A3`, not `A2`.

No VPN address, VPN route, or TUN rebuild is required.

### Switch reply to a dynamic peer

1. The Switch replies from `A1` to local alias `A3`.
2. The alias socket receives the payload.
3. The alias updates its activity timestamp.
4. The alias sends the payload through the original relay session with real
   destination `A2`.

The same mapping is reused for subsequent packets. A changed remote `IP:port`
creates a new alias while the old one remains available until idle expiry.

## Local Address Selection

`SwitchNatVpnService` passes the detected Switch-reachable phone address to the
UDP proxy. In hotspot mode this is the hotspot gateway address; when the phone
and Switch share a LAN it is the phone's address on that LAN.

Alias creation fails closed when this address is blank or no longer bindable.
The failure records `UDP_ALIAS_BIND` diagnostics and drops only the affected
dynamic packet. Static configured endpoint forwarding remains active.

The alias socket is not a Shadowsocks outbound socket and is not sent through
the VPN. It communicates only with the Switch over the local interface. The
existing Shadowsocks relay socket remains protected with `VpnService.protect()`.

## Concurrency and Lifecycle

- Alias installation is atomic per `AliasKey`; concurrent first packets must
  result in one socket and one receive thread.
- An alias is visible in the map only after bind and connect succeed.
- A failed allocation closes its partial socket before reporting the error.
- Closing the UDP proxy first prevents new alias creation, then closes alias
  sockets and their threads, then closes relay sessions and static endpoints.
- Alias receive loops treat socket closure during shutdown as normal.
- Activity is updated for traffic in either direction.
- A single sweeper owned by the alias manager runs every 30 seconds.
- An alias expires after 5 minutes without traffic.
- The initial limits are 128 aliases per Switch relay flow and 512 aliases for
  the entire proxy. When a flow reaches its limit, its oldest inactive alias is
  evicted. When only the global limit is reached, the oldest inactive alias
  across all flows is evicted before a new alias is admitted.
- A closed alias port is not manually reused; the operating system selects the
  next available high port.

## Diagnostics

The current `UDP_RESPONSE_ENDPOINT` error is replaced for unknown remote peers
by successful alias creation. Diagnostics add:

- `activeUdpFlows`: Switch source endpoints with relay sessions.
- `activePeerAliases`: current dynamic peer aliases.
- `peerAliasesCreated`: cumulative successful allocations.
- `peerAliasesExpired`: cumulative expiry or pressure evictions.

Packet samples use these outcomes:

- `alias-created`: a dynamic source received its first mapping.
- `alias-written`: a remote peer payload was delivered to the Switch.
- `alias-forwarded`: the Switch reply was forwarded to the real peer.
- `alias-expired`: an idle or pressure-evicted alias was closed.
- `alias-bind-failed`: a dynamic peer could not obtain a local alias.

The legacy `activeMappings` value remains available during this phase for
Flutter compatibility and continues to report active relay flows.

## Error Handling

- Static endpoint bind failure remains a VPN startup failure because configured
  forwarding would be incomplete.
- Dynamic alias bind failure affects only that remote peer and does not stop
  the VPN or the relay flow.
- A relay receive failure closes that flow and all aliases scoped to it.
- A local alias socket send or receive failure records the appropriate
  diagnostic sample, removes that alias atomically, and closes its socket.
- A relay send failure records `SS_UDP_SEND`, closes the affected relay flow,
  and closes every alias scoped to that flow.
- Packets received on an alias from an unexpected Switch endpoint are rejected;
  connecting the UDP socket performs this filtering at the socket layer.
- Zero-length datagrams are outside the compatibility requirement because the
  Java DatagramSocket implementations used for this app do not reliably emit
  an observable packet for a zero-length send. Game payloads are non-empty.

## Testing

### JVM tests

Loopback addresses model the phone, Switch, seed endpoint, and remote peers.
Tests must prove:

1. Existing configured-endpoint forwarding remains unchanged.
2. An unknown decoded remote source creates a local alias and reaches the
   Switch instead of producing `missing-endpoint`.
3. A Switch reply sent to that alias is forwarded through the same relay
   session to the real remote endpoint.
4. Repeated packets from one remote reuse one alias port.
5. Two remote endpoints in one flow receive different alias ports while sharing
   one relay session.
6. The same remote endpoint under two Switch source flows receives independent
   aliases.
7. Concurrent first packets create only one alias.
8. Idle aliases expire and close their sockets using a controllable clock and
   explicit sweep trigger.
9. Alias limits evict the oldest inactive mapping without disturbing the relay
   flow.
10. Closing the proxy terminates static endpoints, aliases, and relay sessions.

Every production behavior is introduced with a failing test first.

### Android device verification

1. Install a debug APK over the existing app data.
2. Start acceleration and run the Switch connection test; NAT A, public IP, and
   speed must remain correct.
3. Join a Mario Kart 8 room that previously failed.
4. Confirm the formerly unknown remote endpoint now produces `alias-created`,
   `alias-written`, and `alias-forwarded`, with no `UDP_RESPONSE_ENDPOINT`.
5. Verify room join and sustained play while another player joins or leaves.
6. Repeat with Mario Maker 2, including leaving and rejoining so peer endpoints
   change.
7. Stop acceleration and verify all alias and flow counts return to zero.

## Acceptance Criteria

- No root permission or privileged shell command is required at runtime.
- Dynamic player endpoints never trigger a VPN rebuild.
- A player may join, leave, or change endpoint during one running VPN session.
- Unknown peer packets that reach the Shadowsocks relay are delivered to the
  Switch through local aliases.
- Replies from the Switch use the original relay session and reach the correct
  real peer endpoint.
- Existing TCP forwarding, configured UDP endpoints, NAT A connection test,
  and current Flutter screens do not regress.

## Follow-up Phase

After real-device P2P acceptance, a separate design will cover:

- named Shadowsocks server profiles containing the complete server and UDP
  target configuration;
- add, edit, duplicate, delete, and select operations;
- migration of the existing single saved configuration into a default profile;
- a compact live run-state banner on the config screen using the existing
  broadcast status stream;
- disabling profile mutation that would imply a live runtime reconfiguration,
  while still allowing edits to be saved for the next start.
