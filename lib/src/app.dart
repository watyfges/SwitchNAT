import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'config/config_store.dart';
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
  Widget build(BuildContext context) {
    const primary = Color(0xff087f5b);
    final colorScheme =
        ColorScheme.fromSeed(
          seedColor: primary,
          brightness: Brightness.light,
          surface: Colors.white,
        ).copyWith(
          primary: primary,
          secondary: const Color(0xff365f8d),
          tertiary: const Color(0xffa9560b),
          surface: Colors.white,
        );

    return MaterialApp(
      title: 'SwitchNAT',
      debugShowCheckedModeBanner: false,
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      supportedLocales: const <Locale>[Locale('en'), Locale('zh')],
      theme: ThemeData(
        colorScheme: colorScheme,
        scaffoldBackgroundColor: Colors.white,
        useMaterial3: true,
        dividerColor: const Color(0xffe5e7eb),
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.white,
          foregroundColor: Color(0xff17211d),
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          scrolledUnderElevation: 1,
          centerTitle: false,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xfff7f9f8),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(6),
            borderSide: const BorderSide(color: Color(0xffcfd8d4)),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(6),
            borderSide: const BorderSide(color: Color(0xffcfd8d4)),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(6),
            borderSide: const BorderSide(color: primary, width: 2),
          ),
        ),
        navigationBarTheme: const NavigationBarThemeData(
          backgroundColor: Colors.white,
          indicatorColor: Color(0xffdff4ea),
          elevation: 2,
          height: 68,
        ),
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(
            minimumSize: const Size(112, 46),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(6),
            ),
          ),
        ),
        outlinedButtonTheme: OutlinedButtonThemeData(
          style: OutlinedButton.styleFrom(
            minimumSize: const Size(112, 46),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(6),
            ),
          ),
        ),
        textButtonTheme: TextButtonThemeData(
          style: TextButton.styleFrom(
            minimumSize: const Size(104, 46),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(6),
            ),
          ),
        ),
      ),
      home: Builder(
        builder: (context) => Scaffold(
          appBar: AppBar(
            title: const Row(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Icon(Icons.swap_horiz_rounded, color: primary),
                SizedBox(width: 8),
                Text('SwitchNAT'),
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
    );
  }
}
