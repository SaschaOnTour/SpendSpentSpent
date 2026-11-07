import 'dart:core';

import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:logging/logging.dart';
import 'package:spend_spent_spent/globals.dart';
import 'package:spend_spent_spent/recurring_expenses/models/recurring_expense.dart';
import 'package:spend_spent_spent/utils/models/with_error.dart';

part 'recurring_expenses.freezed.dart';

final _log = Logger('RecurringExpensesCubit');

class RecurringExpensesCubit extends Cubit<RecurringExpensesState> {
  RecurringExpensesCubit(super.initialState) {
    getRecurringExpenses();
  }

  /// With [showLoading] false, the list is refreshed in the background: the current one stays shown and a failure is
  /// only logged.
  Future<void> getRecurringExpenses({bool showLoading = true}) async {
    if (showLoading) {
      emit(state.copyWith(loading: true));
    }
    try {
      final expenses = await service.getRecurringExpenses();
      if (!isClosed) {
        emit(state.copyWith(expenses: expenses));
      }
    } catch (e, s) {
      if (!showLoading) {
        _log.warning('Could not refresh the recurring expenses', e, s);
        return;
      }
      emit(state.copyWith(error: e, stackTrace: s));
      rethrow;
    } finally {
      if (showLoading) {
        emit(state.copyWith(loading: false));
      }
    }
  }
}

@freezed
sealed class RecurringExpensesState with _$RecurringExpensesState implements WithError {
  @Implements<WithError>()
  const factory RecurringExpensesState({
    @Default(false) bool loading,
    dynamic error,
    StackTrace? stackTrace,
    @Default([]) List<RecurringExpense> expenses,
  }) = _RecurringExpensesState;
}
