import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../state/service_status.dart';

class LogsScreen extends StatelessWidget {
  const LogsScreen({super.key, required this.statusStream});

  final Stream<ServiceStatus> statusStream;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<ServiceStatus>(
      stream: statusStream,
      builder: (context, snapshot) {
        final status = snapshot.data;
        final samples =
            status?.samples.reversed.toList(growable: false) ??
            const <PacketSample>[];
        return Column(
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 12, 8),
              child: Row(
                children: <Widget>[
                  Expanded(
                    child: Text(
                      'Packet log',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                  IconButton(
                    tooltip: 'Copy logs',
                    onPressed: status == null
                        ? null
                        : () => _copyLogs(context, status),
                    icon: const Icon(Icons.copy_all_outlined),
                  ),
                ],
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: samples.isEmpty
                  ? const Center(child: Text('No packets captured yet'))
                  : ListView.separated(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      itemCount: samples.length,
                      separatorBuilder: (_, _) =>
                          const Divider(height: 1, indent: 64),
                      itemBuilder: (context, index) =>
                          _PacketRow(sample: samples[index]),
                    ),
            ),
          ],
        );
      },
    );
  }

  Future<void> _copyLogs(BuildContext context, ServiceStatus status) async {
    final lines = <String>[
      if (status.lastError.isNotEmpty) 'ERROR ${status.lastError}',
      for (final sample in status.samples)
        '${sample.timestampMillis} ${sample.direction} ${sample.protocol} '
            '${sample.source} -> ${sample.destination} ${sample.outcome}',
    ];
    await Clipboard.setData(ClipboardData(text: lines.join('\n')));
    if (context.mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Logs copied')));
    }
  }
}

class _PacketRow extends StatelessWidget {
  const _PacketRow({required this.sample});

  final PacketSample sample;

  @override
  Widget build(BuildContext context) {
    final outbound = sample.direction.toLowerCase().contains('out');
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          SizedBox(
            width: 32,
            height: 32,
            child: Icon(
              outbound ? Icons.north_east_rounded : Icons.south_west_rounded,
              size: 20,
              color: outbound
                  ? Theme.of(context).colorScheme.primary
                  : Theme.of(context).colorScheme.tertiary,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(
                      child: Text(
                        '${sample.protocol} · ${sample.outcome}',
                        style: Theme.of(context).textTheme.labelLarge,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    Text(
                      _formatTime(sample.timestampMillis),
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  '${sample.source} → ${sample.destination}',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _formatTime(int timestampMillis) {
    if (timestampMillis <= 0) return '--:--:--';
    final time = DateTime.fromMillisecondsSinceEpoch(timestampMillis).toLocal();
    String two(int value) => value.toString().padLeft(2, '0');
    return '${two(time.hour)}:${two(time.minute)}:${two(time.second)}';
  }
}
