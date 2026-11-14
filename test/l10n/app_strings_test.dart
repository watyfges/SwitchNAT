import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switchnat/src/l10n/app_strings.dart';

void main() {
  test('Chinese locale translates app navigation and configuration labels', () {
    const strings = AppStrings(Locale('zh', 'CN'));

    expect(strings.text('Home'), '首页');
    expect(strings.text('Node settings'), '节点设置');
    expect(strings.text('Save changes'), '保存更改');
    expect(strings.rules(3), '3 条规则');
  });

  test('English and unknown labels fall back safely', () {
    const strings = AppStrings(Locale('en'));

    expect(strings.text('Home'), 'Home');
    expect(strings.text('Unknown label'), 'Unknown label');
  });
}
