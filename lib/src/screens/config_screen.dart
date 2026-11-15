import 'package:flutter/material.dart';

import '../config/profile_library.dart';
import '../config/ss_config.dart';
import '../l10n/app_strings.dart';
import '../state/app_controller.dart';

class ConfigScreen extends StatefulWidget {
  const ConfigScreen({super.key, required this.controller});
  final AppController controller;
  @override
  State<ConfigScreen> createState() => _ConfigScreenState();
}

class _ConfigScreenState extends State<ConfigScreen> {
  final host = TextEditingController();
  final port = TextEditingController();
  final password = TextEditingController();
  final proxyPort = TextEditingController();
  final targets = TextEditingController();
  final uuid = TextEditingController();
  final serverName = TextEditingController();
  final publicKey = TextEditingController();
  final shortId = TextEditingController();
  final flow = TextEditingController();
  final fingerprint = TextEditingController();
  final spiderX = TextEditingController();
  String protocol = SsConfig.protocolShadowsocks;
  String method = 'aes-128-gcm';
  String loadedId = '';
  bool showTargets = false;
  bool showStun = false;

  @override
  void dispose() {
    host.dispose();
    port.dispose();
    password.dispose();
    proxyPort.dispose();
    targets.dispose();
    uuid.dispose();
    serverName.dispose();
    publicKey.dispose();
    shortId.dispose();
    flow.dispose();
    fingerprint.dispose();
    spiderX.dispose();
    super.dispose();
  }

  void _sync() {
    final profile = widget.controller.library?.selectedProfile;
    if (profile == null || profile.id == loadedId) return;
    loadedId = profile.id;
    host.text = profile.config.serverHost;
    port.text = '${profile.config.serverPort}';
    password.text = profile.config.password;
    proxyPort.text = '${profile.config.httpProxyPort}';
    targets.text = profile.config.udpTargets;
    uuid.text = profile.config.uuid;
    serverName.text = profile.config.serverName;
    publicKey.text = profile.config.realityPublicKey;
    shortId.text = profile.config.realityShortId;
    flow.text = profile.config.flow;
    fingerprint.text = profile.config.fingerprint;
    spiderX.text = profile.config.spiderX;
    protocol = profile.config.protocol;
    method = profile.config.method;
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: widget.controller,
    builder: (context, _) {
      final strings = context.strings;
      _sync();
      final library = widget.controller.library;
      if (library == null) {
        return const Center(child: CircularProgressIndicator());
      }
      final profile = library.selectedProfile;
      final isManaged = profile.isSubscriptionManaged;
      final displayName = _displayName(profile);
      final ruleCount = targets.text
          .split(RegExp(r'\s+'))
          .where((value) => value.isNotEmpty)
          .length;
      return ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        children: <Widget>[
          Text(
            context.tr('Node settings'),
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 12),
          Card(
            key: const Key('subscription-summary'),
            child: ListTile(
              contentPadding: const EdgeInsets.fromLTRB(16, 6, 10, 6),
              leading: const Icon(Icons.cloud_sync_outlined),
              title: Text(context.tr('Subscriptions')),
              subtitle: Text(
                strings.subscriptions(library.subscriptions.length),
              ),
              trailing: const Icon(Icons.chevron_right),
              onTap: widget.controller.busy
                  ? null
                  : () => _manageSubscriptions(),
            ),
          ),
          const SizedBox(height: 10),
          Card(
            key: const Key('profile-summary'),
            child: ListTile(
              contentPadding: const EdgeInsets.fromLTRB(16, 8, 4, 8),
              title: Text(
                displayName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              subtitle: Text(
                _endpointLabel(profile),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              onTap: widget.controller.busy
                  ? null
                  : () => _pickProfile(library),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  TextButton.icon(
                    onPressed: widget.controller.busy
                        ? null
                        : widget.controller.createProfile,
                    icon: const Icon(Icons.add),
                    label: Text(context.tr('New')),
                  ),
                  if (!isManaged)
                    PopupMenuButton<_ProfileAction>(
                      tooltip: context.tr('More node actions'),
                      enabled: !widget.controller.busy,
                      onSelected: (action) =>
                          _runProfileAction(action, profile, library),
                      itemBuilder: (_) => <PopupMenuEntry<_ProfileAction>>[
                        PopupMenuItem<_ProfileAction>(
                          value: _ProfileAction.rename,
                          child: Text(context.tr('Rename')),
                        ),
                        PopupMenuItem<_ProfileAction>(
                          value: _ProfileAction.delete,
                          enabled: library.profiles.length > 1,
                          child: Text(context.tr('Delete')),
                        ),
                      ],
                    ),
                ],
              ),
            ),
          ),
          if (isManaged) ...<Widget>[
            const SizedBox(height: 8),
            Card(
              color: Theme.of(context).colorScheme.secondaryContainer,
              child: ListTile(
                dense: true,
                leading: const Icon(Icons.lock_outline),
                title: Text(context.tr('Managed by subscription')),
                subtitle: Text(
                  context.tr(
                    'Subscription nodes are read-only. Update them from subscription management.',
                  ),
                ),
              ),
            ),
          ],
          const SizedBox(height: 14),
          DropdownButtonFormField<String>(
            key: ValueKey<String>('protocol-$loadedId'),
            initialValue: protocol,
            decoration: _fieldDecoration(context.tr('Protocol')),
            items: const <DropdownMenuItem<String>>[
              DropdownMenuItem(
                value: SsConfig.protocolShadowsocks,
                child: Text('Shadowsocks'),
              ),
              DropdownMenuItem(
                value: SsConfig.protocolVlessReality,
                child: Text('VLESS + Reality'),
              ),
            ],
            onChanged: isManaged
                ? null
                : (value) => setState(
                    () => protocol = value ?? SsConfig.protocolShadowsocks,
                  ),
          ),
          const SizedBox(height: 10),
          TextField(
            controller: host,
            readOnly: isManaged,
            decoration: _fieldDecoration(context.tr('Host')),
          ),
          const SizedBox(height: 10),
          Row(
            children: <Widget>[
              Expanded(
                child: TextField(
                  controller: port,
                  readOnly: isManaged,
                  keyboardType: TextInputType.number,
                  decoration: _fieldDecoration(context.tr('Port')),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextField(
                  controller: proxyPort,
                  readOnly: isManaged,
                  keyboardType: TextInputType.number,
                  decoration: _fieldDecoration(context.tr('Proxy port')),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          if (protocol == SsConfig.protocolShadowsocks) ...<Widget>[
            TextField(
              controller: password,
              readOnly: isManaged,
              obscureText: true,
              decoration: _fieldDecoration(context.tr('Password')),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              key: ValueKey<String>('method-$loadedId'),
              initialValue: method,
              isExpanded: true,
              decoration: _fieldDecoration(context.tr('Encryption')),
              items: SsConfig.supportedMethods
                  .map(
                    (value) => DropdownMenuItem<String>(
                      value: value,
                      child: Text(value),
                    ),
                  )
                  .toList(),
              onChanged: isManaged
                  ? null
                  : (value) => setState(() => method = value ?? method),
            ),
          ] else ...<Widget>[
            TextField(
              controller: uuid,
              readOnly: isManaged,
              decoration: _fieldDecoration(context.tr('UUID')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: serverName,
              readOnly: isManaged,
              decoration: _fieldDecoration(context.tr('Server name (SNI)')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: publicKey,
              readOnly: isManaged,
              decoration: _fieldDecoration(context.tr('Reality public key')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: shortId,
              readOnly: isManaged,
              decoration: _fieldDecoration(context.tr('Reality short ID')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: flow,
              readOnly: isManaged,
              decoration: _fieldDecoration(context.tr('Flow')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: fingerprint,
              readOnly: isManaged,
              decoration: _fieldDecoration(context.tr('Fingerprint')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: spiderX,
              readOnly: isManaged,
              decoration: _fieldDecoration(context.tr('Spider path')),
            ),
          ],
          const SizedBox(height: 18),
          Text(
            context.tr('Advanced'),
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          Card(
            child: ListTile(
              leading: const Icon(Icons.contrast_outlined),
              title: Text(context.tr('Appearance')),
              subtitle: Text(context.tr('Theme')),
              trailing: DropdownButton<AppThemePreference>(
                key: const Key('theme-mode-selector'),
                value: library.themeMode,
                underline: const SizedBox.shrink(),
                onChanged: widget.controller.busy
                    ? null
                    : (value) {
                        if (value != null) {
                          widget.controller.saveThemeMode(value);
                        }
                      },
                items: AppThemePreference.values
                    .map(
                      (value) => DropdownMenuItem<AppThemePreference>(
                        value: value,
                        child: Text(_themeLabel(context, value)),
                      ),
                    )
                    .toList(),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Card(
            child: Column(
              children: <Widget>[
                ListTile(
                  dense: true,
                  title: Text(context.tr('UDP target rules')),
                  subtitle: Text(strings.rules(ruleCount)),
                  trailing: Icon(
                    showTargets ? Icons.expand_less : Icons.expand_more,
                  ),
                  onTap: () => setState(() => showTargets = !showTargets),
                ),
                if (showTargets)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                    child: TextField(
                      controller: targets,
                      readOnly: isManaged,
                      minLines: 8,
                      maxLines: 14,
                      decoration: _fieldDecoration(
                        context.tr('UDP targets'),
                        helperText: context.tr('IPv4:port or IPv4/prefix:port'),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          Card(
            child: Column(
              children: <Widget>[
                ListTile(
                  dense: true,
                  title: Text(context.tr('STUN servers')),
                  subtitle: Text(strings.servers(library.stunServers.length)),
                  trailing: Icon(
                    showStun ? Icons.expand_less : Icons.expand_more,
                  ),
                  onTap: () => setState(() => showStun = !showStun),
                ),
                if (showStun) ...<Widget>[
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton.icon(
                      onPressed: () => _editStun(library),
                      icon: const Icon(Icons.add),
                      label: Text(context.tr('Add server')),
                    ),
                  ),
                  for (final server in library.stunServers)
                    SwitchListTile(
                      dense: true,
                      value: server.enabled,
                      title: Text(
                        '${server.host}:${server.port}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      secondary: IconButton(
                        tooltip: context.tr('Edit STUN server'),
                        onPressed: () => _editStun(library, server),
                        icon: const Icon(Icons.edit_outlined),
                      ),
                      onChanged: (enabled) => widget.controller.saveStunServers(
                        library.stunServers
                            .map(
                              (item) => item.id == server.id
                                  ? item.copyWith(enabled: enabled)
                                  : item,
                            )
                            .toList(),
                      ),
                    ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 14),
          FilledButton.icon(
            onPressed: widget.controller.busy || isManaged
                ? null
                : () => _save(profile),
            icon: const Icon(Icons.save_outlined),
            label: Text(context.tr('Save changes')),
          ),
        ],
      );
    },
  );

  InputDecoration _fieldDecoration(String label, {String? helperText}) =>
      InputDecoration(labelText: label, helperText: helperText, isDense: true);

  String _themeLabel(BuildContext context, AppThemePreference value) =>
      switch (value) {
        AppThemePreference.system => context.tr('Follow system'),
        AppThemePreference.light => context.tr('Light'),
        AppThemePreference.dark => context.tr('Dark'),
      };

  Future<void> _pickProfile(ProfileLibrary library) async {
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
                children: library.profiles
                    .map(
                      (profile) => ListTile(
                        key: ValueKey<String>('profile-option-${profile.id}'),
                        selected: profile.id == library.selectedProfileId,
                        leading: Icon(
                          profile.id == library.selectedProfileId
                              ? Icons.check_circle
                              : Icons.circle_outlined,
                        ),
                        title: Text(_displayName(profile)),
                        subtitle: Text(_profileSubtitle(profile, library)),
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
    if (id != null) await widget.controller.selectProfile(id);
  }

  String _displayName(ServerProfile profile) {
    final endpoint =
        '${profile.config.serverHost}:${profile.config.serverPort}';
    if (profile.name == 'New node' || profile.name == 'Saved server') {
      return context.tr(profile.name);
    }
    return profile.name.trim() == endpoint
        ? context.tr('Default node')
        : profile.name;
  }

  String _endpointLabel(ServerProfile profile) =>
      profile.config.serverHost.trim().isEmpty || profile.config.serverPort <= 0
      ? context.tr('Not configured')
      : '${profile.config.serverHost}:${profile.config.serverPort}';

  String _profileSubtitle(ServerProfile profile, ProfileLibrary library) {
    final endpoint = _endpointLabel(profile);
    if (!profile.isSubscriptionManaged) return endpoint;
    final source = library.subscriptions
        .where((item) => item.id == profile.subscriptionId)
        .firstOrNull;
    return source == null ? endpoint : '$endpoint · ${source.name}';
  }

  Future<void> _manageSubscriptions() => showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => _SubscriptionManager(controller: widget.controller),
  );

  Future<void> _runProfileAction(
    _ProfileAction action,
    ServerProfile profile,
    ProfileLibrary library,
  ) async {
    switch (action) {
      case _ProfileAction.rename:
        await _rename(profile);
      case _ProfileAction.delete:
        if (library.profiles.length > 1) {
          await widget.controller.deleteProfile(profile.id);
        }
    }
  }

  Future<void> _save(ServerProfile profile) => widget.controller.saveProfile(
    profile.copyWith(
      config: SsConfig(
        protocol: protocol,
        serverHost: host.text.trim(),
        serverPort: int.tryParse(port.text) ?? 0,
        password: password.text,
        method: method,
        httpProxyPort: int.tryParse(proxyPort.text) ?? 0,
        udpTargets: targets.text.trim(),
        uuid: uuid.text.trim(),
        flow: flow.text.trim(),
        serverName: serverName.text.trim(),
        realityPublicKey: publicKey.text.trim(),
        realityShortId: shortId.text.trim(),
        fingerprint: fingerprint.text.trim(),
        spiderX: spiderX.text.trim(),
      ),
    ),
  );

  Future<void> _rename(ServerProfile profile) async {
    final name = await showDialog<String>(
      context: context,
      builder: (context) => _RenameProfileDialog(initialName: profile.name),
    );
    if (name != null && name.isNotEmpty) {
      await widget.controller.renameProfile(profile.id, name);
    }
  }

  Future<void> _editStun(
    ProfileLibrary library, [
    StunServerEndpoint? existing,
  ]) async {
    final updated = await showDialog<StunServerEndpoint>(
      context: context,
      builder: (context) => _StunEditorDialog(existing: existing),
    );
    if (updated == null) return;
    final servers = existing == null
        ? <StunServerEndpoint>[...library.stunServers, updated]
        : library.stunServers
              .map((item) => item.id == existing.id ? updated : item)
              .toList();
    await widget.controller.saveStunServers(servers);
  }
}

enum _ProfileAction { rename, delete }

class _RenameProfileDialog extends StatefulWidget {
  const _RenameProfileDialog({required this.initialName});

  final String initialName;

  @override
  State<_RenameProfileDialog> createState() => _RenameProfileDialogState();
}

class _RenameProfileDialogState extends State<_RenameProfileDialog> {
  late final TextEditingController controller;

  @override
  void initState() {
    super.initState();
    controller = TextEditingController(text: widget.initialName);
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(context.tr('Rename node')),
    content: TextField(controller: controller, autofocus: true),
    actions: <Widget>[
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: Text(context.tr('Cancel')),
      ),
      FilledButton(
        onPressed: () => Navigator.pop(context, controller.text.trim()),
        child: Text(context.tr('Rename')),
      ),
    ],
  );
}

class _StunEditorDialog extends StatefulWidget {
  const _StunEditorDialog({this.existing});

  final StunServerEndpoint? existing;

  @override
  State<_StunEditorDialog> createState() => _StunEditorDialogState();
}

class _StunEditorDialogState extends State<_StunEditorDialog> {
  late final TextEditingController host;
  late final TextEditingController port;

  @override
  void initState() {
    super.initState();
    host = TextEditingController(text: widget.existing?.host ?? '');
    port = TextEditingController(text: '${widget.existing?.port ?? 3478}');
  }

  @override
  void dispose() {
    host.dispose();
    port.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(
      context.tr(
        widget.existing == null ? 'Add STUN server' : 'Edit STUN server',
      ),
    ),
    content: Column(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        TextField(
          controller: host,
          autofocus: true,
          decoration: InputDecoration(labelText: context.tr('Host')),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: port,
          keyboardType: TextInputType.number,
          decoration: InputDecoration(labelText: context.tr('Port')),
        ),
      ],
    ),
    actions: <Widget>[
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: Text(context.tr('Cancel')),
      ),
      FilledButton(
        onPressed: () => Navigator.pop(
          context,
          StunServerEndpoint(
            id:
                widget.existing?.id ??
                'custom-${DateTime.now().microsecondsSinceEpoch}',
            host: host.text.trim(),
            port: int.tryParse(port.text) ?? 0,
            enabled: widget.existing?.enabled ?? true,
          ),
        ),
        child: Text(context.tr('Save')),
      ),
    ],
  );
}

class _SubscriptionManager extends StatelessWidget {
  const _SubscriptionManager({required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) => FractionallySizedBox(
    heightFactor: 0.82,
    child: AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final library = controller.library;
        final sources = library?.subscriptions ?? const <SubscriptionSource>[];
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 14, 8, 8),
              child: Row(
                children: <Widget>[
                  Expanded(
                    child: Text(
                      context.tr('Manage subscriptions'),
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                  IconButton(
                    tooltip: context.tr('Update all'),
                    onPressed: controller.busy || sources.isEmpty
                        ? null
                        : controller.refreshAllSubscriptions,
                    icon: const Icon(Icons.refresh),
                  ),
                  IconButton(
                    tooltip: context.tr('Add subscription'),
                    onPressed: controller.busy ? null : () => _edit(context),
                    icon: const Icon(Icons.add),
                  ),
                  IconButton(
                    tooltip: MaterialLocalizations.of(
                      context,
                    ).closeButtonTooltip,
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
            ),
            if (controller.busy) const LinearProgressIndicator(),
            if (controller.feedback.isNotEmpty)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 6, 16, 4),
                child: Text(
                  controller.feedback,
                  style: TextStyle(
                    color: controller.feedback.startsWith('Operation failed:')
                        ? Theme.of(context).colorScheme.error
                        : Theme.of(context).colorScheme.primary,
                  ),
                ),
              ),
            if (sources.isEmpty)
              Expanded(
                child: Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: <Widget>[
                        const Icon(Icons.cloud_off_outlined, size: 42),
                        const SizedBox(height: 12),
                        Text(context.tr('No subscriptions')),
                        const SizedBox(height: 4),
                        Text(
                          context.tr(
                            'Add a Clash subscription to import nodes.',
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 16),
                        FilledButton.icon(
                          onPressed: controller.busy
                              ? null
                              : () => _edit(context),
                          icon: const Icon(Icons.add),
                          label: Text(context.tr('Add subscription')),
                        ),
                      ],
                    ),
                  ),
                ),
              )
            else
              Expanded(
                child: ListView.separated(
                  padding: const EdgeInsets.fromLTRB(12, 4, 12, 24),
                  itemCount: sources.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 6),
                  itemBuilder: (context, index) {
                    final source = sources[index];
                    return Card(
                      child: ListTile(
                        leading: const Icon(Icons.cloud_done_outlined),
                        title: Text(
                          source.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: Text(
                          '${context.strings.nodes(source.nodeCount)} · '
                          '${_subscriptionHost(source.url)}\n'
                          '${context.tr('Last updated')}: '
                          '${_formatUpdated(source.updatedAtMillis)}',
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        isThreeLine: true,
                        trailing: PopupMenuButton<_SubscriptionAction>(
                          enabled: !controller.busy,
                          onSelected: (action) =>
                              _runAction(context, action, source),
                          itemBuilder: (_) =>
                              <PopupMenuEntry<_SubscriptionAction>>[
                                PopupMenuItem<_SubscriptionAction>(
                                  value: _SubscriptionAction.update,
                                  child: Text(context.tr('Update')),
                                ),
                                PopupMenuItem<_SubscriptionAction>(
                                  value: _SubscriptionAction.edit,
                                  child: Text(context.tr('Edit')),
                                ),
                                PopupMenuItem<_SubscriptionAction>(
                                  value: _SubscriptionAction.delete,
                                  child: Text(context.tr('Delete')),
                                ),
                              ],
                        ),
                      ),
                    );
                  },
                ),
              ),
          ],
        );
      },
    ),
  );

  Future<void> _runAction(
    BuildContext context,
    _SubscriptionAction action,
    SubscriptionSource source,
  ) async {
    switch (action) {
      case _SubscriptionAction.update:
        await controller.syncSubscription(
          id: source.id,
          name: source.name,
          url: source.url,
        );
      case _SubscriptionAction.edit:
        await _edit(context, source);
      case _SubscriptionAction.delete:
        final confirmed = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(context.tr('Delete subscription')),
            content: Text(
              context.tr('Delete this subscription and all imported nodes?'),
            ),
            actions: <Widget>[
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: Text(context.tr('Cancel')),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: Text(context.tr('Delete')),
              ),
            ],
          ),
        );
        if (confirmed == true) {
          await controller.deleteSubscription(source.id);
        }
    }
  }

  Future<void> _edit(
    BuildContext context, [
    SubscriptionSource? existing,
  ]) async {
    final draft = await showDialog<_SubscriptionDraft>(
      context: context,
      builder: (context) => _SubscriptionEditorDialog(existing: existing),
    );
    if (draft == null) return;
    await controller.syncSubscription(
      id: existing?.id ?? '',
      name: draft.name,
      url: draft.url,
    );
  }
}

enum _SubscriptionAction { update, edit, delete }

class _SubscriptionDraft {
  const _SubscriptionDraft(this.name, this.url);
  final String name;
  final String url;
}

class _SubscriptionEditorDialog extends StatefulWidget {
  const _SubscriptionEditorDialog({this.existing});
  final SubscriptionSource? existing;

  @override
  State<_SubscriptionEditorDialog> createState() =>
      _SubscriptionEditorDialogState();
}

class _SubscriptionEditorDialogState extends State<_SubscriptionEditorDialog> {
  late final TextEditingController name;
  late final TextEditingController url;

  @override
  void initState() {
    super.initState();
    name = TextEditingController(text: widget.existing?.name ?? '');
    url = TextEditingController(text: widget.existing?.url ?? '');
  }

  @override
  void dispose() {
    name.dispose();
    url.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(
      context.tr(
        widget.existing == null ? 'Add subscription' : 'Edit subscription',
      ),
    ),
    content: Column(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        TextField(
          controller: name,
          autofocus: true,
          onChanged: (_) => setState(() {}),
          decoration: InputDecoration(
            labelText: context.tr('Subscription name'),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: url,
          keyboardType: TextInputType.url,
          autocorrect: false,
          onChanged: (_) => setState(() {}),
          decoration: InputDecoration(
            labelText: context.tr('Subscription URL'),
          ),
        ),
      ],
    ),
    actions: <Widget>[
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: Text(context.tr('Cancel')),
      ),
      FilledButton(
        onPressed: name.text.trim().isEmpty || url.text.trim().isEmpty
            ? null
            : () => Navigator.pop(
                context,
                _SubscriptionDraft(name.text.trim(), url.text.trim()),
              ),
        child: Text(context.tr('Save')),
      ),
    ],
  );
}

String _subscriptionHost(String raw) {
  final uri = Uri.tryParse(raw);
  return uri?.host.isNotEmpty == true ? uri!.host : '••••';
}

String _formatUpdated(int millis) {
  if (millis <= 0) return '—';
  final date = DateTime.fromMillisecondsSinceEpoch(millis).toLocal();
  String two(int value) => value.toString().padLeft(2, '0');
  return '${date.year}-${two(date.month)}-${two(date.day)} '
      '${two(date.hour)}:${two(date.minute)}';
}
