import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'screens/profile_screen.dart';
import 'screens/results_screen.dart';
import 'screens/search_screen.dart';

void main() {
  runApp(const ProviderScope(child: LeadFinderApp()));
}

class LeadFinderApp extends StatelessWidget {
  const LeadFinderApp({super.key});
   
  @override
  Widget build(BuildContext context) {
    final router = GoRouter(
      routes: [
        GoRoute(path: '/', builder: (context, state) => const SearchScreen()),
        GoRoute(
          path: '/results',
          builder: (context, state) => const ResultsScreen(),
        ),
        GoRoute(
          path: '/profile',
          builder: (context, state) => const ProfileScreen(),
        ),
      ],
    );

    return MaterialApp.router(
      title: 'LeadFinder',
      theme: ThemeData(primarySwatch: Colors.blue),
      routerConfig: router,
    );
  }
}
