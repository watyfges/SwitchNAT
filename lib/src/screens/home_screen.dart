import 'package:flutter/material.dart';

import '../config/profile_library.dart';
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
      final endpoint = profile == null ? '' : _endpointLabel(profile);
      final endpointShapedName = profile?.name.trim() == rawEndpoint;
      return ListView(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
        children: <Widget>[
          Text(
            'Acceleration',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 16),
          Card(
            color: on ? const Color(0xffe8f7ef) : null,
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Row(
                children: <Widget>[
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        const Text('MASTER SWITCH'),
                        const SizedBox(height: 6),
                        Text(
                          on ? 'Acceleration is on' : 'Acceleration is off',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        Text(
                          on
                              ? (status.activeProfileName.isEmpty
                                    ? profile?.name ?? ''
                                    : status.activeProfileName)
                              : 'Ready to connect',
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
              title: const Text('Selected node'),
              subtitle: Text(
                profile == null
                    ? 'Loading…'
                    : endpointShapedName
                    ? endpoint
                    : '${profile.name}\n$endpoint',
              ),
              isThreeLine: profile != null && !endpointShapedName,
              trailing: TextButton(
                onPressed: profile == null ? null : () => _pickProfile(context),
                child: const Text('Select'),
              ),
              onTap: on ? controller.runTcpTest : null,
            ),
          ),
          const SizedBox(height: 12),
          _MetricCard(
            key: const Key('switch-card'),
            label: 'SWITCH',
            value: _switchLabel(status.switchPresence),
            detail: status.learnedSwitchIp,
            icon: Icons.sports_esports_outlined,
          ),
          const SizedBox(height: 8),
          _MetricCard(
            key: const Key('nat-card'),
            label: 'SS UDP NAT',
            value: _natLabel(status),
            detail: status.natTest.mappedAddress,
            icon: Icons.public,
            onTap: on && status.natTest.state != DiagnosticRunState.testing
                ? controller.runNatTest
                : null,
          ),
          if (controller.hasPendingProfileChange) ...<Widget>[
            const SizedBox(height: 12),
            const Card(
              color: Color(0xfffff2d8),
              child: Padding(
                padding: EdgeInsets.all(14),
                child: Text('Restart the service to use the selected node.'),
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
    final items = controller.library?.profiles ?? const [];
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
                'Select node',
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
                        title: Text(_displayName(profile)),
                        subtitle: Text(_endpointLabel(profile)),
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

  String _displayName(ServerProfile profile) {
    final endpoint =
        '${profile.config.serverHost}:${profile.config.serverPort}';
    return profile.name.trim() == endpoint ? 'Default node' : profile.name;
  }

  String _endpointLabel(ServerProfile profile) =>
      profile.config.serverHost.trim().isEmpty || profile.config.serverPort <= 0
      ? 'Not configured'
      : '${profile.config.serverHost}:${profile.config.serverPort}';

  String _switchLabel(SwitchPresence value) => switch (value) {
    SwitchPresence.waiting => 'Waiting for Switch',
    SwitchPresence.connected => 'Connected',
    SwitchPresence.inactive => 'Inactive',
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
