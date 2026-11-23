package com.example.ui.viewmodel

import com.example.data.model.FinancialTransaction
import com.example.data.model.ProfileType
import com.example.data.model.TransactionType
import com.example.ui.components.computeNiceAxisMax
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialChartAggregationTest {

  @Test
  fun buildMonthlyHistory_aggregatesIncomeAndExpensesAcrossYearBoundary() {
    val txs =
      listOf(
        FinancialTransaction(
          id = "1",
          profileType = ProfileType.COMMON.id,
          type = TransactionType.INCOME.id,
          title = "Cliente A",
          amount = 15_000.0,
          monthKey = "2025-12",
        ),
        FinancialTransaction(
          id = "2",
          profileType = ProfileType.COMMON.id,
          type = TransactionType.EXPENSE.id,
          title = "Servidor",
          amount = 5_000.0,
          monthKey = "2025-12",
        ),
        FinancialTransaction(
          id = "3",
          profileType = ProfileType.COMMON.id,
          type = TransactionType.INCOME.id,
          title = "Cliente B",
          amount = 24_000.0,
          monthKey = "2026-02",
        ),
      )

    val history = MainViewModel.buildMonthlyHistory(txs, endMonthKey = "2026-02", monthsCount = 4)
    assertEquals(listOf("2025-11", "2025-12", "2026-01", "2026-02"), history.map { it.monthKey })

    // Nov 2025 empty -> 0.0
    assertEquals(0.0, history[0].totalIncome, 0.01)
    assertEquals(0.0, history[0].totalExpenses, 0.01)

    // Dec 2025 -> 15k income, 5k expense, 10k net
    assertEquals("Dic", history[1].shortLabel)
    assertEquals("Diciembre 2025", history[1].fullLabel)
    assertEquals(15_000.0, history[1].totalIncome, 0.01)
    assertEquals(5_000.0, history[1].totalExpenses, 0.01)
    assertEquals(10_000.0, history[1].netBalance, 0.01)

    // Feb 2026 -> 24k income, 0 expense
    assertEquals(24_000.0, history[3].totalIncome, 0.01)
    assertEquals(24_000.0, history[3].netBalance, 0.01)
  }

  @Test
  fun formatCompactCurrency_andNiceAxisMax_produceReadableLabels() {
    assertEquals("$0", Formatters.formatCompactCurrency(0.0))
    assertEquals("$8k", Formatters.formatCompactCurrency(8_000.0))
    assertEquals("$120k", Formatters.formatCompactCurrency(120_000.0))
    assertTrue(computeNiceAxisMax(82_000.0) >= 82_000.0)
    assertEquals(10_000.0, computeNiceAxisMax(0.0), 0.01)
  }
}
