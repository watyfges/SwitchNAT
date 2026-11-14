import 'package:flutter/material.dart';

import '../l10n/app_strings.dart';
import '../state/service_status.dart';

class StatusScreen extends StatelessWidget {
  const StatusScreen({super.key, required this.statusStream});

  final Stream<ServiceStatus> statusStream;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<ServiceStatus>(
      stream: statusStream,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return Center(
            child: Text('${context.tr('Status')}: ${snapshot.error}'),
          );
        }
        final status =
            snapshot.data ?? ServiceStatus.fromJson(const <Object?, Object?>{});
        return Align(
          alignment: Alignment.topCenter,
          child: SizedBox(
            width: 760,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
              children: <Widget>[
                _StateBanner(state: status.state),
                const SizedBox(height: 24),
                _SectionTitle(title: context.tr('Connection')),
                _KeyValueRow(
                  label: context.tr('Hotspot gateway'),
                  value: status.hotspotGateway,
                ),
                _KeyValueRow(
                  label: context.tr('Proxy address'),
                  value: status.switchProxyAddress,
                ),
                _KeyValueRow(
                  label: context.tr('Switch IP'),
                  value: status.learnedSwitchIp,
                ),
                const SizedBox(height: 22),
                _SectionTitle(title: context.tr('Traffic')),
                _KeyValueRow(
                  label: context.tr('UDP packets read'),
                  value: '${status.udpRead}',
                ),
                _KeyValueRow(
                  label: context.tr('UDP packets matched'),
                  value: '${status.udpAccepted}',
                ),
                _KeyValueRow(
                  label: context.tr('UDP requests relayed'),
                  value: '${status.udpSent}',
                ),
                _KeyValueRow(
                  label: context.tr('UDP replies received'),
                  value: '${status.udpReceived}',
                ),
                _KeyValueRow(
                  label: context.tr('UDP replies returned'),
                  value: '${status.udpWritten}',
                ),
                _KeyValueRow(
                  label: context.tr('Active UDP mappings'),
                  value: '${status.activeMappings}',
                ),
                _KeyValueRow(
                  label: context.tr('Active relay flows'),
                  value: '${status.activeUdpFlows}',
                ),
                _KeyValueRow(
                  label: context.tr('Active P2P aliases'),
                  value: '${status.activePeerAliases}',
                ),
                _KeyValueRow(
                  label: context.tr('Aliases created'),
                  value: '${status.peerAliasesCreated}',
                ),
                _KeyValueRow(
                  label: context.tr('Aliases expired'),
                  value: '${status.peerAliasesExpired}',
                ),
                _KeyValueRow(
                  label: context.tr('TCP connections'),
                  value: '${status.tcpConnections}',
                ),
                if (status.lastError.isNotEmpty) ...<Widget>[
                  const SizedBox(height: 22),
                  _SectionTitle(title: context.tr('Latest error')),
                  Text(
                    status.lastError,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}

class _StateBanner extends StatelessWidget {
  const _StateBanner({required this.state});

  final VpnRunState state;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final (background, foreground, icon) = switch (state) {
      VpnRunState.running => (
        const Color(0xffdff4ea),
        const Color(0xff075c42),
        Icons.shield_outlined,
      ),
      VpnRunState.failed => (
        colors.errorContainer,
        colors.onErrorContainer,
        Icons.error_outline,
      ),
      VpnRunState.permissionNeeded => (
        const Color(0xfffff1d6),
        const Color(0xff764b00),
        Icons.admin_panel_settings_outlined,
      ),
      VpnRunState.starting || VpnRunState.stopping => (
        const Color(0xffe8eef7),
        const Color(0xff334155),
        Icons.sync,
      ),
      VpnRunState.stopped => (
        colors.surfaceContainerHighest,
        colors.onSurfaceVariant,
        Icons.shield_outlined,
      ),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Row(
        children: <Widget>[
          Icon(icon, color: foreground),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              _stateLabel(context, state),
              style: Theme.of(
                context,
              ).textTheme.titleMedium?.copyWith(color: foreground),
            ),
          ),
        ],
      ),
    );
  }

  String _stateLabel(BuildContext context, VpnRunState state) =>
      switch (state) {
        VpnRunState.stopped => context.tr('Stopped'),
        VpnRunState.permissionNeeded => context.tr('VPN permission required'),
        VpnRunState.starting => context.tr('Starting'),
        VpnRunState.running => context.tr('Running'),
        VpnRunState.stopping => context.tr('Stopping'),
        VpnRunState.failed => context.tr('Failed'),
      };
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Text(title, style: Theme.of(context).textTheme.titleSmall),
    );
  }
}

class _KeyValueRow extends StatelessWidget {
  const _KeyValueRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 46),
      padding: const EdgeInsets.symmetric(vertical: 10),
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(color: Theme.of(context).dividerColor),
        ),
      ),
      child: Row(
        children: <Widget>[
          Expanded(child: Text(label)),
          const SizedBox(width: 16),
          Flexible(
            child: Text(
              value.isEmpty ? '—' : value,
              textAlign: TextAlign.end,
              style: const TextStyle(
                fontFeatures: <FontFeature>[FontFeature.tabularFigures()],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
