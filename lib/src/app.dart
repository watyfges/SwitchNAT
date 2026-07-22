import 'package:flutter/material.dart';

import 'config/config_store.dart';
import 'native/native_bridge.dart';
import 'screens/config_screen.dart';
import 'screens/logs_screen.dart';
import 'screens/status_screen.dart';
import 'state/service_status.dart';

class SwitchNatApp extends StatefulWidget {
  const SwitchNatApp({super.key});

  @override
  State<SwitchNatApp> createState() => _SwitchNatAppState();
}

class _SwitchNatAppState extends State<SwitchNatApp> {
  late final NativeBridge bridge;
  late final Stream<ServiceStatus> statusStream;
  late final List<Widget> screens;
  int index = 0;

  @override
  void initState() {
    super.initState();
    bridge = NativeBridge();
    statusStream = bridge.statusStream.asBroadcastStream();
    screens = <Widget>[
      ConfigScreen(bridge: bridge, store: const MethodChannelConfigStore()),
      StatusScreen(statusStream: statusStream),
      LogsScreen(statusStream: statusStream),
    ];
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
      home: Scaffold(
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
          destinations: const <NavigationDestination>[
            NavigationDestination(
              icon: Icon(Icons.settings_outlined),
              selectedIcon: Icon(Icons.settings),
              label: 'Config',
            ),
            NavigationDestination(
              icon: Icon(Icons.monitor_heart_outlined),
              selectedIcon: Icon(Icons.monitor_heart),
              label: 'Status',
            ),
            NavigationDestination(
              icon: Icon(Icons.article_outlined),
              selectedIcon: Icon(Icons.article),
              label: 'Logs',
            ),
          ],
        ),
      ),
    );
  }
}
