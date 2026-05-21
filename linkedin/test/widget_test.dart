// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:linkedin/main.dart';

void main() {
  testWidgets('App starts on search screen', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: LeadFinderApp()));
    await tester.pumpAndSettle();

    expect(find.text('Search LinkedIn candidates'), findsOneWidget);
    expect(find.text('Search'), findsOneWidget);
  });
}
