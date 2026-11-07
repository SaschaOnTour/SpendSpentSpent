import 'package:auto_route/auto_route.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:material_loading_indicator/loading_indicator.dart';
import 'package:spend_spent_spent/recurring_expenses/views/components/expense_list.dart';
import 'package:spend_spent_spent/globals.dart';
import 'package:spend_spent_spent/recurring_expenses/state/recurring_expenses.dart';
import 'package:spend_spent_spent/router.dart';
import 'package:spend_spent_spent/utils/views/components/error_listener.dart';

@RoutePage()
class RecurringExpenseListTab extends StatelessWidget {
  const RecurringExpenseListTab({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (context) => RecurringExpensesCubit(const RecurringExpensesState()),
      child: RefreshWhenShown(
        child: ErrorHandler<RecurringExpensesCubit, RecurringExpensesState>(
          child: BlocBuilder<RecurringExpensesCubit, RecurringExpensesState>(
            builder: (context, state) {
              final cubit = context.read<RecurringExpensesCubit>();

              return AnimatedSwitcher(
                duration: panelTransition,
                child: state.loading
                    ? Center(child: LoadingIndicator())
                    : ExpenseList(expenses: state.expenses, refreshExpenses: cubit.getRecurringExpenses),
              );
            },
          ),
        ),
      ),
    );
  }
}

/// The tab stays in memory while the server moves the next date of the recurring expenses forward, so the list is
/// refreshed when the tab is shown again and when the app comes back to the foreground.
class RefreshWhenShown extends StatefulWidget {
  final Widget child;

  const RefreshWhenShown({super.key, required this.child});

  @override
  State<RefreshWhenShown> createState() => _RefreshWhenShownState();
}

class _RefreshWhenShownState extends State<RefreshWhenShown> with WidgetsBindingObserver {
  TabsRouter? _tabsRouter;
  String? _shownTab;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final tabsRouter = TabsRouterScope.of(context)?.controller;
    if (tabsRouter != _tabsRouter) {
      _tabsRouter?.removeListener(_tabChanged);
      _tabsRouter = tabsRouter;
      // the tab is built when it's first shown, the cubit loads the list then
      _shownTab = tabsRouter?.current.name;
      tabsRouter?.addListener(_tabChanged);
    }
  }

  @override
  void dispose() {
    _tabsRouter?.removeListener(_tabChanged);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  bool get _isShown => _tabsRouter == null || _tabsRouter!.current.name == RecurringExpenseListRoute.name;

  void _tabChanged() {
    final shownTab = _tabsRouter?.current.name;
    if (shownTab != _shownTab) {
      _shownTab = shownTab;
      if (_isShown) {
        _refresh();
      }
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _isShown) {
      _refresh();
    }
  }

  void _refresh() {
    context.read<RecurringExpensesCubit>().getRecurringExpenses(showLoading: false);
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
