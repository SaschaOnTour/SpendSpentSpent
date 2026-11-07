import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_loading_indicator/loading_indicator.dart';
import 'package:nock/nock.dart';
import 'package:spend_spent_spent/categories/models/category.dart';
import 'package:spend_spent_spent/recurring_expenses/models/recurring_expense.dart';
import 'package:spend_spent_spent/recurring_expenses/views/components/recurring_expense_list.dart';

import '../helper_widget/test_app_setup_widget.dart';
import '../utils/test_utils.dart';

void main() {
  final category = Category(categoryOrder: 0, icon: 'house', id: 1, user: testUser);

  String rent(String nextOccurrence) => jsonEncode([
    RecurringExpense(
      id: 1,
      name: 'Rent',
      amount: 1200,
      type: 2,
      typeParam: 5,
      category: category,
      nextOccurrence: nextOccurrence,
    ).toJson(),
  ]);

  setUpAll(() {
    nock.init();
  });

  setUp(() async {
    await setupTests(loggedIn: true, categories: [category]);
    // the app settings read the server config
    nock(validServerUrl).get('/config')
      ..reply(200, loadFixture('valid_server_config.json'))
      ..persist(true);
  });

  tearDown(() {
    nock.cleanAll();
  });

  Future<void> resumeApp(WidgetTester tester) async {
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
  }

  testWidgets('The list is refreshed when the app comes back to the foreground', (tester) async {
    nock(validServerUrl).get('/API/RecurringExpense').reply(200, rent('2026-11-05'));
    await tester.pumpWidget(TestSetup(child: RecurringExpenseListTab()));
    await tester.pumpAndSettle();
    expect(find.text('Next: 2026-11-05'), findsOneWidget);

    // the server processed the recurring expense in the meantime
    final refresh = nock(validServerUrl).get('/API/RecurringExpense')..reply(200, rent('2026-12-05'));
    await resumeApp(tester);
    await tester.pump();
    // refreshed in the background, the list stays
    expect(find.byType(LoadingIndicator), findsNothing);
    await tester.pumpAndSettle();

    expect(refresh.isDone, true);
    expect(find.text('Next: 2026-12-05'), findsOneWidget);
  });

  testWidgets('A failed background refresh keeps the list', (tester) async {
    nock(validServerUrl).get('/API/RecurringExpense').reply(200, rent('2026-11-05'));
    await tester.pumpWidget(TestSetup(child: RecurringExpenseListTab()));
    await tester.pumpAndSettle();

    final refresh = nock(validServerUrl).get('/API/RecurringExpense')..reply(500, 'Server error');
    await resumeApp(tester);
    await tester.pumpAndSettle();

    expect(refresh.isDone, true);
    expect(find.text('Next: 2026-11-05'), findsOneWidget);
    expect(find.byType(AlertDialog), findsNothing);
  });
}
