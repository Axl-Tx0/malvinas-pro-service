package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FinancialTransaction
import com.example.data.model.TransactionType
import com.example.ui.components.MonthlyIncomeExpenseChartCard
import com.example.ui.viewmodel.DashboardData
import com.example.ui.viewmodel.MonthlyChartPoint
import com.example.util.Formatters

private data class CategoryBreakdown(
  val name: String,
  val amount: Double,
  val share: Float,
  val isIncome: Boolean,
)

@Composable
fun FinancialSummaryScreen(
  data: DashboardData,
  onAddTransaction: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var monthsCount by remember { mutableIntStateOf(6) }
  var inspectedMonthKey by remember(data.selectedMonthKey) { mutableStateOf(data.selectedMonthKey) }

  val fullHistory = data.combinedMonthlyHistory

  val displayedPoints =
    remember(fullHistory, monthsCount) {
      fullHistory.takeLast(monthsCount.coerceIn(1, fullHistory.size.coerceAtLeast(1)))
    }

  LaunchedEffect(displayedPoints) {
    if (displayedPoints.none { it.monthKey == inspectedMonthKey }) {
      inspectedMonthKey = displayedPoints.lastOrNull()?.monthKey ?: data.selectedMonthKey
    }
  }

  val totalIncome = remember(displayedPoints) { displayedPoints.sumOf { it.totalIncome } }
  val totalExpenses = remember(displayedPoints) { displayedPoints.sumOf { it.totalExpenses } }
  val netBalance = totalIncome - totalExpenses
  val hasAnyActivity = totalIncome > 0.0 || totalExpenses > 0.0

  val displayedMonthKeys = remember(displayedPoints) { displayedPoints.map { it.monthKey }.toSet() }
  val scopedTransactions =
    remember(data.allTransactions, displayedMonthKeys) {
      data.allTransactions.filter { tx -> tx.monthKey in displayedMonthKeys }
    }

  val topCategories =
    remember(scopedTransactions) {
      buildCategoryBreakdown(scopedTransactions).take(6)
    }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("financial_summary_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Resumen financiero",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = "Visualizá ingresos y gastos mensuales totales (Servicios Premium)",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = monthsCount == 6,
          onClick = { monthsCount = 6 },
          label = { Text("Últimos 6 meses") },
          modifier = Modifier.testTag("financial_range_6m_chip"),
        )
        FilterChip(
          selected = monthsCount == 12,
          onClick = { monthsCount = 12 },
          label = { Text("Últimos 12 meses") },
          modifier = Modifier.testTag("financial_range_12m_chip"),
        )
      }
    }

    item {
      PeriodTotalsCard(
        monthsCount = monthsCount,
        totalIncome = totalIncome,
        totalExpenses = totalExpenses,
        netBalance = netBalance,
      )
    }

    if (!hasAnyActivity) {
      item {
        EmptyFinancialChartCard(
          scopeLabel = "Servicios Premium",
          monthsCount = monthsCount,
          onAddTransaction = onAddTransaction,
        )
      }
    } else {
      item {
        MonthlyIncomeExpenseChartCard(
          points = displayedPoints,
          selectedMonthKey = inspectedMonthKey,
          onSelectMonth = { inspectedMonthKey = it },
        )
      }

      if (topCategories.isNotEmpty()) {
        item {
          CategoryBreakdownCard(categories = topCategories)
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Detalle mensual accesible",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "$monthsCount meses",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      items(displayedPoints.reversed(), key = { it.monthKey }) { point ->
        MonthlyDetailRowCard(
          point = point,
          isSelected = point.monthKey == inspectedMonthKey,
          onClick = { inspectedMonthKey = point.monthKey },
        )
      }
    }
  }
}

@Composable
private fun PeriodTotalsCard(
  monthsCount: Int,
  totalIncome: Double,
  totalExpenses: Double,
  netBalance: Double,
) {
  val incomeColor = MaterialTheme.colorScheme.tertiary
  val expenseColor = MaterialTheme.colorScheme.error
  val netColor =
    if (netBalance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

  val savingsRate =
    if (totalIncome > 0.0) {
      (((totalIncome - totalExpenses) / totalIncome) * 100.0).toInt()
    } else {
      0
    }

  val cardSummaryDescription =
    "Totales de los últimos $monthsCount meses. " +
      "Ingresos totales: ${Formatters.formatCurrency(totalIncome)}. " +
      "Gastos totales: ${Formatters.formatCurrency(totalExpenses)}. " +
      "Balance neto: ${Formatters.formatCurrency(netBalance)}."

  Card(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("financial_summary_totals_card")
        .semantics(mergeDescendants = true) { contentDescription = cardSummaryDescription },
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Acumulado ($monthsCount meses)",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (totalIncome > 0.0) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = netColor.copy(alpha = 0.14f),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Savings,
                contentDescription = null,
                tint = netColor,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "Margen $savingsRate%",
                style = MaterialTheme.typography.labelSmall,
                color = netColor,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }

      Text(
        text = Formatters.formatCurrency(netBalance),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = netColor,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        TotalMetricTile(
          label = "Ingresos totales",
          amount = Formatters.formatCurrency(totalIncome),
          accentColor = incomeColor,
          icon = Icons.Filled.ArrowUpward,
          modifier = Modifier.weight(1f),
        )
        TotalMetricTile(
          label = "Gastos totales",
          amount = Formatters.formatCurrency(totalExpenses),
          accentColor = expenseColor,
          icon = Icons.Filled.ArrowDownward,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun TotalMetricTile(
  label: String,
  amount: String,
  accentColor: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    color = accentColor.copy(alpha = 0.09f),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = amount,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = accentColor,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun CategoryBreakdownCard(categories: List<CategoryBreakdown>) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Principales categorías",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      categories.forEach { cat ->
        val barColor =
          if (cat.isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        val typeLabel = if (cat.isIncome) "Ingreso" else "Gasto"
        val pct = (cat.share * 100f).toInt()
        Column(
          modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
              contentDescription =
                "${cat.name} ($typeLabel): ${Formatters.formatCurrency(cat.amount)}, $pct%"
            },
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "${cat.name} · $typeLabel",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "${Formatters.formatCurrency(cat.amount)} ($pct%)",
              style = MaterialTheme.typography.labelLarge,
              color = barColor,
              fontWeight = FontWeight.SemiBold,
            )
          }
          LinearProgressIndicator(
            progress = { cat.share.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.16f),
          )
        }
      }
    }
  }
}

@Composable
private fun MonthlyDetailRowCard(
  point: MonthlyChartPoint,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val incomeColor = MaterialTheme.colorScheme.tertiary
  val expenseColor = MaterialTheme.colorScheme.error
  val borderColor =
    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

  val rowDescription =
    "${point.fullLabel}. Ingresos: ${Formatters.formatCurrency(point.totalIncome)}. " +
      "Gastos: ${Formatters.formatCurrency(point.totalExpenses)}. " +
      "Balance neto: ${Formatters.formatCurrency(point.netBalance)}."

  Card(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = rowDescription },
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
          else MaterialTheme.colorScheme.surface
      ),
    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = point.fullLabel,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = Formatters.formatCurrency(point.netBalance),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color =
            if (point.netBalance >= 0) MaterialTheme.colorScheme.primary else expenseColor,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Ingresos: +${Formatters.formatCurrency(point.totalIncome)}",
          style = MaterialTheme.typography.bodySmall,
          color = incomeColor,
        )
        Text(
          text = "Gastos: -${Formatters.formatCurrency(point.totalExpenses)}",
          style = MaterialTheme.typography.bodySmall,
          color = expenseColor,
        )
      }
    }
  }
}

@Composable
private fun EmptyFinancialChartCard(
  scopeLabel: String,
  monthsCount: Int,
  onAddTransaction: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.BarChart,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp),
      )
      Text(
        text = "Sin movimientos para graficar",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text =
          "No hay ingresos ni gastos registrados en los últimos $monthsCount meses ($scopeLabel).",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Button(
        onClick = onAddTransaction,
        modifier = Modifier.testTag("financial_empty_add_button"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Agregar movimiento")
      }
    }
  }
}

private fun buildCategoryBreakdown(transactions: List<FinancialTransaction>): List<CategoryBreakdown> {
  if (transactions.isEmpty()) return emptyList()
  val totalVolume = transactions.sumOf { it.amount }.coerceAtLeast(1.0)
  return transactions
    .groupBy { (it.category.ifBlank { "General" }) to (it.type == TransactionType.INCOME.id) }
    .map { (key, list) ->
      val sum = list.sumOf { it.amount }
      CategoryBreakdown(
        name = key.first,
        amount = sum,
        share = (sum / totalVolume).toFloat().coerceIn(0f, 1f),
        isIncome = key.second,
      )
    }
    .sortedByDescending { it.amount }
}
