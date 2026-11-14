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
            onChanged: (value) => setState(
              () => protocol = value ?? SsConfig.protocolShadowsocks,
            ),
          ),
          const SizedBox(height: 10),
          TextField(
            controller: host,
            decoration: _fieldDecoration(context.tr('Host')),
          ),
          const SizedBox(height: 10),
          Row(
            children: <Widget>[
              Expanded(
                child: TextField(
                  controller: port,
                  keyboardType: TextInputType.number,
                  decoration: _fieldDecoration(context.tr('Port')),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextField(
                  controller: proxyPort,
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
              onChanged: (value) => setState(() => method = value ?? method),
            ),
          ] else ...<Widget>[
            TextField(
              controller: uuid,
              decoration: _fieldDecoration(context.tr('UUID')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: serverName,
              decoration: _fieldDecoration(context.tr('Server name (SNI)')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: publicKey,
              decoration: _fieldDecoration(context.tr('Reality public key')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: shortId,
              decoration: _fieldDecoration(context.tr('Reality short ID')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: flow,
              decoration: _fieldDecoration(context.tr('Flow')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: fingerprint,
              decoration: _fieldDecoration(context.tr('Fingerprint')),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: spiderX,
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
            onPressed: widget.controller.busy ? null : () => _save(profile),
            icon: const Icon(Icons.save_outlined),
            label: Text(context.tr('Save changes')),
          ),
        ],
      );
    },
  );

  InputDecoration _fieldDecoration(String label, {String? helperText}) =>
      InputDecoration(labelText: label, helperText: helperText, isDense: true);

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
