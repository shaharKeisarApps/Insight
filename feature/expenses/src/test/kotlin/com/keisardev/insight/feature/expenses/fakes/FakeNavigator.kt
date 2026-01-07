package com.keisardev.insight.feature.expenses.fakes

/**
 * Note: For testing, use Circuit's built-in FakeNavigator from circuit-test:
 * `com.slack.circuit.test.FakeNavigator`
 *
 * Example:
 * ```
 * val navigator = FakeNavigator(ExpensesScreen)
 * // ... trigger event that navigates ...
 * val screen = navigator.awaitNextScreen()
 * assertThat(screen).isInstanceOf(AddEditExpenseScreen::class.java)
 * ```
 *
 * The presenter tests in this module use Circuit's FakeNavigator directly.
 */
