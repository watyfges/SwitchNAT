import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'config/config_store.dart';
import 'config/profile_library.dart';
import 'l10n/app_strings.dart';
import 'native/native_bridge.dart';
import 'screens/config_screen.dart';
import 'screens/home_screen.dart';
import 'screens/logs_screen.dart';
import 'screens/status_screen.dart';
import 'state/app_controller.dart';

class SwitchNatApp extends StatefulWidget {
  const SwitchNatApp({super.key});

  @override
  State<SwitchNatApp> createState() => _SwitchNatAppState();
}

class _SwitchNatAppState extends State<SwitchNatApp> {
  late final AppController controller;
  late final List<Widget> screens;
  int index = 0;

  @override
  void initState() {
    super.initState();
    controller = AppController(
      bridge: NativeBridge(),
      store: const MethodChannelConfigStore(),
    );
    unawaited(controller.initialize());
    screens = <Widget>[
      HomeScreen(controller: controller),
      ConfigScreen(controller: controller),
      StatusScreen(statusStream: controller.statusStream),
      LogsScreen(statusStream: controller.statusStream),
    ];
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: controller,
    builder: (context, _) => MaterialApp(
      title: 'SwitchNAT',
      debugShowCheckedModeBanner: false,
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      supportedLocales: const <Locale>[Locale('en'), Locale('zh')],
      theme: _buildTheme(Brightness.light),
      darkTheme: _buildTheme(Brightness.dark),
      themeMode: _themeMode(controller.library?.themeMode),
      home: Builder(
        builder: (context) => Scaffold(
          appBar: AppBar(
            title: Row(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Icon(
                  Icons.swap_horiz_rounded,
                  color: Theme.of(context).colorScheme.primary,
                ),
                const SizedBox(width: 8),
                const Text('SwitchNAT'),
              ],
            ),
          ),
          body: IndexedStack(index: index, children: screens),
          bottomNavigationBar: NavigationBar(
            selectedIndex: index,
            onDestinationSelected: (value) => setState(() => index = value),
            destinations: <NavigationDestination>[
              NavigationDestination(
                icon: const Icon(Icons.home_outlined),
                selectedIcon: const Icon(Icons.home),
                label: context.tr('Home'),
              ),
              NavigationDestination(
                icon: const Icon(Icons.settings_outlined),
                selectedIcon: const Icon(Icons.settings),
                label: context.tr('Config'),
              ),
              NavigationDestination(
                icon: const Icon(Icons.monitor_heart_outlined),
                selectedIcon: const Icon(Icons.monitor_heart),
                label: context.tr('Status'),
              ),
              NavigationDestination(
                icon: const Icon(Icons.article_outlined),
                selectedIcon: const Icon(Icons.article),
                label: context.tr('Logs'),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

ThemeMode _themeMode(AppThemePreference? preference) => switch (preference) {
  AppThemePreference.light => ThemeMode.light,
  AppThemePreference.dark => ThemeMode.dark,
  _ => ThemeMode.system,
};

ThemeData _buildTheme(Brightness brightness) {
  const seed = Color(0xff087f5b);
  final colors = ColorScheme.fromSeed(seedColor: seed, brightness: brightness);
  final isDark = brightness == Brightness.dark;
  final borderColor = colors.outlineVariant;

  return ThemeData(
    colorScheme: colors,
    brightness: brightness,
    useMaterial3: true,
    scaffoldBackgroundColor: colors.surface,
    dividerColor: colors.outlineVariant,
    appBarTheme: AppBarTheme(
      backgroundColor: colors.surface,
      foregroundColor: colors.onSurface,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 1,
      centerTitle: false,
    ),
    cardTheme: CardThemeData(
      color: colors.surfaceContainerLow,
      surfaceTintColor: Colors.transparent,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: isDark
          ? colors.surfaceContainerHighest
          : colors.surfaceContainerLowest,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(6),
        borderSide: BorderSide(color: borderColor),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(6),
        borderSide: BorderSide(color: borderColor),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(6),
        borderSide: BorderSide(color: colors.primary, width: 2),
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: colors.surfaceContainer,
      indicatorColor: colors.primaryContainer,
      elevation: 2,
      height: 68,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(112, 46),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(112, 46),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        minimumSize: const Size(104, 46),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
      ),
    ),
  );
}
