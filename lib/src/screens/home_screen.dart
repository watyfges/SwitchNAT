import 'package:flutter/material.dart';

import '../config/profile_library.dart';
import '../l10n/app_strings.dart';
import '../state/app_controller.dart';
import '../state/service_status.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key, required this.controller});
  final AppController controller;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: controller,
    builder: (context, _) {
      final status = controller.status;
      final profile = controller.library?.selectedProfile;
      final on = controller.isRunning;
      final rawEndpoint = profile == null
          ? ''
          : '${profile.config.serverHost}:${profile.config.serverPort}';
      final endpoint = profile == null ? '' : _endpointLabel(context, profile);
      final endpointShapedName = profile?.name.trim() == rawEndpoint;
      return ListView(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
        children: <Widget>[
          Text(
            context.tr('Acceleration'),
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 16),
          Card(
            color: on ? Theme.of(context).colorScheme.primaryContainer : null,
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Row(
                children: <Widget>[
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text(context.tr('MASTER SWITCH')),
                        const SizedBox(height: 6),
                        Text(
                          context.tr(
                            on ? 'Acceleration is on' : 'Acceleration is off',
                          ),
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        Text(
                          on
                              ? (status.activeProfileName.isEmpty
                                    ? profile?.name ?? ''
                                    : status.activeProfileName)
                              : context.tr('Ready to connect'),
                        ),
                      ],
                    ),
                  ),
                  IconButton.filled(
                    key: const Key('master-switch'),
                    onPressed: controller.busy
                        ? null
                        : (on ? controller.stop : controller.start),
                    iconSize: 32,
                    padding: const EdgeInsets.all(16),
                    icon: Icon(
                      on ? Icons.power_settings_new : Icons.play_arrow_rounded,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: ListTile(
              title: Text(context.tr('Selected node')),
              subtitle: Text(
                profile == null
                    ? context.tr('Loading…')
                    : endpointShapedName
                    ? endpoint
                    : '${profile.name}\n$endpoint',
              ),
              isThreeLine: profile != null && !endpointShapedName,
              trailing: TextButton(
                onPressed: profile == null ? null : () => _pickProfile(context),
                child: Text(context.tr('Select')),
              ),
              onTap: on ? controller.runTcpTest : null,
            ),
          ),
          const SizedBox(height: 12),
          _MetricCard(
            key: const Key('switch-card'),
            label: context.tr('SWITCH'),
            value: _switchLabel(context, status.switchPresence),
            detail: status.learnedSwitchIp,
            icon: Icons.sports_esports_outlined,
          ),
          const SizedBox(height: 8),
          _MetricCard(
            key: const Key('nat-card'),
            label: context.tr('UDP NAT'),
            value: _natLabel(status),
            detail: status.natTest.mappedAddress,
            icon: Icons.public,
            onTap: on && status.natTest.state != DiagnosticRunState.testing
                ? controller.runNatTest
                : null,
          ),
          if (controller.hasPendingProfileChange) ...<Widget>[
            const SizedBox(height: 12),
            Card(
              color: Theme.of(context).colorScheme.tertiaryContainer,
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Text(
                  context.tr('Restart the service to use the selected node.'),
                ),
              ),
            ),
          ],
          if (controller.feedback.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(controller.feedback),
            ),
        ],
      );
    },
  );

  Future<void> _pickProfile(BuildContext context) async {
    final items =
        controller.library?.enabledProfiles ?? const <ServerProfile>[];
    final id = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 20, 8),
              child: Text(
                context.tr('Select node'),
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ),
            Flexible(
              child: ListView(
                shrinkWrap: true,
                children: items
                    .map(
                      (profile) => ListTile(
                        key: ValueKey<String>(
                          'home-profile-option-${profile.id}',
                        ),
                        selected:
                            profile.id == controller.library?.selectedProfileId,
                        leading: Icon(
                          profile.id == controller.library?.selectedProfileId
                              ? Icons.check_circle
                              : Icons.circle_outlined,
                        ),
                        title: Text(_displayName(context, profile)),
                        subtitle: Text(_endpointLabel(context, profile)),
                        onTap: () => Navigator.pop(context, profile.id),
                      ),
                    )
                    .toList(),
              ),
            ),
          ],
        ),
      ),
    );
    if (id != null) await controller.selectProfile(id);
  }

  String _displayName(BuildContext context, ServerProfile profile) {
    final endpoint =
        '${profile.config.serverHost}:${profile.config.serverPort}';
    if (profile.name == 'New node' || profile.name == 'Saved server') {
      return context.tr(profile.name);
    }
    return profile.name.trim() == endpoint
        ? context.tr('Default node')
        : profile.name;
  }

  String _endpointLabel(BuildContext context, ServerProfile profile) =>
      profile.config.serverHost.trim().isEmpty || profile.config.serverPort <= 0
      ? context.tr('Not configured')
      : '${profile.config.serverHost}:${profile.config.serverPort}';

  String _switchLabel(BuildContext context, SwitchPresence value) =>
      switch (value) {
        SwitchPresence.waiting => context.tr('Waiting for Switch'),
        SwitchPresence.connected => context.tr('Connected'),
        SwitchPresence.inactive => context.tr('Inactive'),
      };

  String _natLabel(ServiceStatus status) => switch (status.natTest.state) {
    DiagnosticRunState.testing => 'Testing…',
    DiagnosticRunState.available => status.natTest.legacyLabel,
    DiagnosticRunState.partial => 'Partial result',
    DiagnosticRunState.unavailable => 'Unavailable',
    DiagnosticRunState.idle => 'Not tested',
  };
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({
    super.key,
    required this.label,
    required this.value,
    required this.detail,
    required this.icon,
    this.onTap,
  });
  final String label, value, detail;
  final IconData icon;
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context) => Card(
    child: InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: <Widget>[
            SizedBox(
              width: 44,
              height: 44,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(icon),
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(label, style: Theme.of(context).textTheme.labelMedium),
                  const SizedBox(height: 4),
                  Text(
                    value,
                    maxLines: 2,
                    softWrap: true,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  if (detail.isNotEmpty)
                    Text(
                      detail,
                      maxLines: 2,
                      softWrap: true,
                      overflow: TextOverflow.visible,
                    ),
                ],
              ),
            ),
            if (onTap != null) ...<Widget>[
              const SizedBox(width: 8),
              const Icon(Icons.refresh_rounded),
            ],
          ],
        ),
      ),
    ),
  );
}
