import 'dart:async';

import 'package:flutter/material.dart';

import '../config/config_store.dart';
import '../config/ss_config.dart';
import '../native/native_bridge.dart';

class ConfigScreen extends StatefulWidget {
  const ConfigScreen({super.key, this.bridge, this.store});

  final NativeBridge? bridge;
  final ConfigStore? store;

  @override
  State<ConfigScreen> createState() => _ConfigScreenState();
}

class _ConfigScreenState extends State<ConfigScreen> {
  final host = TextEditingController();
  final port = TextEditingController(text: '8388');
  final password = TextEditingController();
  final proxyPort = TextEditingController(text: '7890');
  final udpTargets = TextEditingController();
  late final NativeBridge bridge;
  late final ConfigStore store;
  String method = 'aes-128-gcm';
  List<String> errors = const <String>[];
  String feedback = '';
  bool busy = false;
  bool obscurePassword = true;

  @override
  void initState() {
    super.initState();
    bridge = widget.bridge ?? NativeBridge();
    store = widget.store ?? const MethodChannelConfigStore();
    unawaited(_loadConfig());
  }

  @override
  void dispose() {
    host.dispose();
    port.dispose();
    password.dispose();
    proxyPort.dispose();
    udpTargets.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: LayoutBuilder(
        builder: (context, constraints) {
          return Align(
            alignment: Alignment.topCenter,
            child: SizedBox(
              width: constraints.maxWidth > 680 ? 680 : constraints.maxWidth,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
                children: <Widget>[
                  Text(
                    'Connection',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Shadowsocks server',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 20),
                  TextField(
                    controller: host,
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(
                      labelText: 'Server host',
                      prefixIcon: Icon(Icons.dns_outlined),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Expanded(
                        child: TextField(
                          controller: port,
                          textInputAction: TextInputAction.next,
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(
                            labelText: 'Server port',
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: TextField(
                          controller: proxyPort,
                          textInputAction: TextInputAction.next,
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(
                            labelText: 'HTTP proxy port',
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: password,
                    obscureText: obscurePassword,
                    textInputAction: TextInputAction.done,
                    decoration: InputDecoration(
                      labelText: 'Password',
                      prefixIcon: const Icon(Icons.key_outlined),
                      suffixIcon: IconButton(
                        tooltip: obscurePassword
                            ? 'Show password'
                            : 'Hide password',
                        onPressed: () {
                          setState(() => obscurePassword = !obscurePassword);
                        },
                        icon: Icon(
                          obscurePassword
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: udpTargets,
                    minLines: 3,
                    maxLines: 6,
                    decoration: const InputDecoration(
                      labelText: 'UDP targets',
                      helperText:
                          'Optional. IPv4:port or IPv4/prefix:port (/21-/32), e.g. 203.0.112.0/21:8123',
                      prefixIcon: Icon(Icons.route_outlined),
                    ),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    initialValue: method,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'Encryption method',
                      prefixIcon: Icon(Icons.lock_outline),
                    ),
                    items: SsConfig.supportedMethods
                        .map(
                          (value) => DropdownMenuItem<String>(
                            value: value,
                            child: Text(value, overflow: TextOverflow.ellipsis),
                          ),
                        )
                        .toList(),
                    onChanged: busy
                        ? null
                        : (value) => setState(() => method = value ?? method),
                  ),
                  if (errors.isNotEmpty) ...<Widget>[
                    const SizedBox(height: 16),
                    _ErrorList(errors: errors),
                  ],
                  if (feedback.isNotEmpty) ...<Widget>[
                    const SizedBox(height: 12),
                    Text(
                      feedback,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                  const SizedBox(height: 20),
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: <Widget>[
                      FilledButton.icon(
                        onPressed: busy ? null : _start,
                        icon: const Icon(Icons.play_arrow_rounded),
                        label: const Text('Start'),
                      ),
                      OutlinedButton.icon(
                        onPressed: busy ? null : _save,
                        icon: const Icon(Icons.save_outlined),
                        label: const Text('Save'),
                      ),
                      TextButton.icon(
                        onPressed: busy ? null : _stop,
                        icon: const Icon(Icons.stop_rounded),
                        label: const Text('Stop'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Future<void> _loadConfig() async {
    try {
      final config = await store.load();
      if (config == null || !mounted) return;
      setState(() {
        host.text = config.serverHost;
        port.text = '${config.serverPort}';
        password.text = config.password;
        proxyPort.text = '${config.httpProxyPort}';
        udpTargets.text = config.udpTargets;
        method = config.method;
      });
    } catch (_) {
      // A missing platform channel in widget tests behaves like no saved config.
    }
  }

  SsConfig _currentConfig() {
    return SsConfig(
      serverHost: host.text.trim(),
      serverPort: int.tryParse(port.text) ?? 0,
      password: password.text,
      method: method,
      httpProxyPort: int.tryParse(proxyPort.text) ?? 0,
      udpTargets: udpTargets.text.trim(),
    );
  }

  SsConfig? _validatedConfig() {
    final config = _currentConfig();
    final validation = config.validate();
    setState(() {
      errors = validation;
      feedback = '';
    });
    return validation.isEmpty ? config : null;
  }

  Future<void> _save() async {
    final config = _validatedConfig();
    if (config == null) return;
    await _runAction(() async {
      await store.save(config);
      if (mounted) setState(() => feedback = 'Configuration saved');
    });
  }

  Future<void> _start() async {
    final config = _validatedConfig();
    if (config == null) return;
    await _runAction(() async {
      await store.save(config);
      final permissionGranted = await bridge.requestVpnPermission();
      if (!permissionGranted) {
        if (mounted) {
          setState(() => feedback = 'VPN permission denied');
        }
        return;
      }
      final started = await bridge.start(config);
      if (mounted) {
        setState(
          () => feedback = started
              ? 'VPN start requested'
              : 'VPN permission required',
        );
      }
    });
  }

  Future<void> _stop() async {
    await _runAction(() async {
      await bridge.stop();
      if (mounted) setState(() => feedback = 'VPN stop requested');
    });
  }

  Future<void> _runAction(Future<void> Function() action) async {
    setState(() => busy = true);
    try {
      await action();
    } catch (error) {
      if (mounted) setState(() => feedback = 'Operation failed: $error');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }
}

class _ErrorList extends StatelessWidget {
  const _ErrorList({required this.errors});

  final List<String> errors;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: colorScheme.errorContainer,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: errors
            .map(
              (error) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Text(
                  error,
                  style: TextStyle(color: colorScheme.onErrorContainer),
                ),
              ),
            )
            .toList(),
      ),
    );
  }
}
