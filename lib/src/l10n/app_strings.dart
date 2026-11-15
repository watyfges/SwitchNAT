import 'package:flutter/widgets.dart';

class AppStrings {
  const AppStrings(this.locale);

  final Locale locale;

  bool get isChinese => locale.languageCode.toLowerCase() == 'zh';

  String text(String english) =>
      isChinese ? (_zh[english] ?? english) : english;

  String rules(int count) => isChinese ? '$count 条规则' : '$count rules';
  String servers(int count) => isChinese ? '$count 个服务器' : '$count servers';
  String nodes(int count) =>
      isChinese ? '$count 个节点' : '$count ${count == 1 ? 'node' : 'nodes'}';
  String subscriptions(int count) => isChinese
      ? '$count 个订阅'
      : '$count ${count == 1 ? 'subscription' : 'subscriptions'}';
}

extension AppStringsContext on BuildContext {
  AppStrings get strings => AppStrings(Localizations.localeOf(this));
  String tr(String english) => strings.text(english);
}

const Map<String, String> _zh = <String, String>{
  'Home': '首页',
  'Config': '配置',
  'Status': '状态',
  'Logs': '日志',
  'Acceleration': '加速',
  'MASTER SWITCH': '总开关',
  'Acceleration is on': '加速已开启',
  'Acceleration is off': '加速已关闭',
  'Ready to connect': '可以开始连接',
  'Selected node': '当前节点',
  'Loading…': '正在加载…',
  'Select': '选择',
  'Select node': '选择节点',
  'Default node': '默认节点',
  'New node': '新节点',
  'Saved server': '已保存的服务器',
  'Not configured': '未配置',
  'SWITCH': 'SWITCH',
  'UDP NAT': 'UDP NAT',
  'Waiting for Switch': '等待 Switch 连接',
  'Connected': '已连接',
  'Inactive': '未活动',
  'Testing…': '测试中…',
  'Partial result': '部分结果',
  'Unavailable': '不可用',
  'Not tested': '尚未测试',
  'Restart the service to use the selected node.': '重启服务后才会使用新选择的节点。',
  'Node settings': '节点设置',
  'Subscriptions': '订阅',
  'Manage': '管理',
  'Manage subscriptions': '管理订阅',
  'Add subscription': '添加订阅',
  'Edit subscription': '编辑订阅',
  'Subscription name': '订阅名称',
  'Subscription URL': '订阅地址',
  'Update': '更新',
  'Update all': '全部更新',
  'No subscriptions': '暂无订阅',
  'Add a Clash subscription to import nodes.': '添加 Clash 订阅以导入节点。',
  'Delete subscription': '删除订阅',
  'Delete this subscription and all imported nodes?': '删除此订阅及其导入的全部节点？',
  'Managed by subscription': '由订阅管理',
  'Last updated': '上次更新',
  'Never updated': '尚未更新',
  'Subscription nodes are read-only. Update them from subscription management.':
      '订阅节点为只读，请在订阅管理中更新。',
  'New': '新建',
  'More node actions': '更多节点操作',
  'Rename': '重命名',
  'Delete': '删除',
  'Protocol': '协议',
  'Host': '服务器地址',
  'Port': '端口',
  'Proxy port': '代理端口',
  'Password': '密码',
  'Encryption': '加密方式',
  'UUID': 'UUID',
  'Server name (SNI)': '服务器名称（SNI）',
  'Reality public key': 'Reality 公钥',
  'Reality short ID': 'Reality Short ID',
  'Flow': '流控',
  'Fingerprint': '浏览器指纹',
  'Spider path': 'Spider 路径',
  'Advanced': '高级设置',
  'UDP target rules': 'UDP 目标规则',
  'UDP targets': 'UDP 目标',
  'IPv4:port or IPv4/prefix:port': 'IPv4:端口 或 IPv4/前缀:端口',
  'STUN servers': 'STUN 服务器',
  'Add server': '添加服务器',
  'Edit STUN server': '编辑 STUN 服务器',
  'Save changes': '保存更改',
  'Rename node': '重命名节点',
  'Cancel': '取消',
  'Add STUN server': '添加 STUN 服务器',
  'Save': '保存',
  'Edit': '编辑',
  'Connection': '连接',
  'Hotspot gateway': '热点网关',
  'Proxy address': '代理地址',
  'Switch IP': 'Switch IP',
  'Traffic': '流量',
  'UDP packets read': '读取的 UDP 数据包',
  'UDP packets matched': '匹配的 UDP 数据包',
  'UDP requests relayed': '已转发的 UDP 请求',
  'UDP replies received': '收到的 UDP 回复',
  'UDP replies returned': '返回的 UDP 回复',
  'Active UDP mappings': '活动 UDP 映射',
  'Active relay flows': '活动转发流',
  'Active P2P aliases': '活动 P2P 别名',
  'Aliases created': '已创建别名',
  'Aliases expired': '已过期别名',
  'TCP connections': 'TCP 连接数',
  'Latest error': '最近错误',
  'Stopped': '已停止',
  'VPN permission required': '需要 VPN 权限',
  'Starting': '正在启动',
  'Running': '运行中',
  'Stopping': '正在停止',
  'Failed': '失败',
  'Packet log': '数据包日志',
  'Copy logs': '复制日志',
  'No packets captured yet': '尚未捕获数据包',
  'Logs copied': '日志已复制',
};
