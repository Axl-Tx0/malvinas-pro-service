package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.MonthlyChartPoint
import com.example.util.Formatters
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun MonthlyIncomeExpenseChartCard(
  points: List<MonthlyChartPoint>,
  selectedMonthKey: String,
  onSelectMonth: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val incomeColor = MaterialTheme.colorScheme.tertiary
  val expenseColor = MaterialTheme.colorScheme.error
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  val selectedPoint = points.firstOrNull { it.monthKey == selectedMonthKey } ?: points.lastOrNull()

  Card(
    modifier = modifier.fillMaxWidth().testTag("financial_bar_chart_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Ingresos vs. Gastos",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Tocá un mes para ver el detalle exacto",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Accessible Legend using both color and directional icons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LegendBadge(
          label = "Ingresos",
          color = incomeColor,
          icon = Icons.Filled.ArrowUpward,
        )
        LegendBadge(
          label = "Gastos",
          color = expenseColor,
          icon = Icons.Filled.ArrowDownward,
        )
      }

      // Interactive Tooltip Banner for the selected month
      if (selectedPoint != null) {
        ChartTooltipCard(
          point = selectedPoint,
          incomeColor = incomeColor,
          expenseColor = expenseColor,
        )
      }

      val maxRaw =
        points.maxOfOrNull { maxOf(it.totalIncome, it.totalExpenses) }?.coerceAtLeast(0.0) ?: 0.0
      val scaleMax = computeNiceAxisMax(maxRaw)

      // Y-Axis ticks + Plot area
      val chartPlotHeight = 190.dp
      val axisLabelWidth = 52.dp
      val scrollState = rememberScrollState()

      LaunchedEffect(points.size) {
        if (points.size > 6) {
          scrollState.scrollTo(scrollState.maxValue)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
      ) {
        // Y-Axis Tick Labels
        Column(
          modifier = Modifier.width(axisLabelWidth).height(chartPlotHeight),
          verticalArrangement = Arrangement.SpaceBetween,
          horizontalAlignment = Alignment.End,
        ) {
          listOf(1.0, 0.6667, 0.3333, 0.0).forEach { fraction ->
            Text(
              text = Formatters.formatCompactCurrency(scaleMax * fraction),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              softWrap = false,
              modifier = Modifier.padding(end = 6.dp),
            )
          }
        }

        // Scrollable or full-width bar plot with dashed horizontal gridlines
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
          val minColumnWidth = 54.dp
          val count = points.size.coerceAtLeast(1)
          val fitsWithoutScroll = maxWidth >= (minColumnWidth * count)
          val columnWidth: Dp =
            if (fitsWithoutScroll) maxWidth / count else minColumnWidth
          val totalPlotWidth = columnWidth * count

          Box(
            modifier =
              Modifier.fillMaxWidth()
                .let { base ->
                  if (fitsWithoutScroll) base else base.horizontalScroll(scrollState)
                },
          ) {
            Box(modifier = Modifier.width(totalPlotWidth)) {
              // Gridlines behind bars
              Canvas(
                modifier = Modifier.fillMaxWidth().height(chartPlotHeight),
              ) {
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                val stepCount = 3
                for (i in 0..stepCount) {
                  val y =
                    if (i == stepCount) size.height - 1f
                    else (size.height / stepCount) * i
                  drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (i == stepCount) 2f else 1.2f,
                    pathEffect = if (i == stepCount) null else dashEffect,
                  )
                }
              }

              // Month Bar Groups + X-axis labels
              Row(modifier = Modifier.fillMaxWidth()) {
                points.forEach { point ->
                  MonthBarGroupColumn(
                    point = point,
                    scaleMax = scaleMax,
                    isSelected = point.monthKey == selectedPoint?.monthKey,
                    plotHeight = chartPlotHeight,
                    columnWidth = columnWidth,
                    incomeColor = incomeColor,
                    expenseColor = expenseColor,
                    onClick = { onSelectMonth(point.monthKey) },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LegendBadge(
  label: String,
  color: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Surface(
      shape = CircleShape,
      color = color.copy(alpha = 0.16f),
      modifier = Modifier.size(22.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = color,
          modifier = Modifier.size(14.dp),
        )
      }
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun ChartTooltipCard(
  point: MonthlyChartPoint,
  incomeColor: Color,
  expenseColor: Color,
) {
  Surface(
    modifier = Modifier.fillMaxWidth().testTag("chart_selected_tooltip"),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
          text = "Balance: ${Formatters.formatCurrency(point.netBalance)}",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color =
            if (point.netBalance >= 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Ingresos: ${Formatters.formatCurrency(point.totalIncome)}",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = incomeColor,
        )
        Text(
          text = "Gastos: ${Formatters.formatCurrency(point.totalExpenses)}",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = expenseColor,
        )
      }
    }
  }
}

@Composable
private fun MonthBarGroupColumn(
  point: MonthlyChartPoint,
  scaleMax: Double,
  isSelected: Boolean,
  plotHeight: Dp,
  columnWidth: Dp,
  incomeColor: Color,
  expenseColor: Color,
  onClick: () -> Unit,
) {
  val targetIncomeRatio =
    if (scaleMax > 0.0) (point.totalIncome / scaleMax).toFloat().coerceIn(0f, 1f) else 0f
  val targetExpenseRatio =
    if (scaleMax > 0.0) (point.totalExpenses / scaleMax).toFloat().coerceIn(0f, 1f) else 0f

  val animatedIncomeRatio by
    animateFloatAsState(
      targetValue = targetIncomeRatio,
      animationSpec = tween(durationMillis = 360),
      label = "income_bar_${point.monthKey}",
    )
  val animatedExpenseRatio by
    animateFloatAsState(
      targetValue = targetExpenseRatio,
      animationSpec = tween(durationMillis = 360),
      label = "expense_bar_${point.monthKey}",
    )

  val accessibilitySummary =
    "${point.fullLabel}. Ingresos: ${Formatters.formatCurrency(point.totalIncome)}. " +
      "Gastos: ${Formatters.formatCurrency(point.totalExpenses)}. " +
      "Balance neto: ${Formatters.formatCurrency(point.netBalance)}."

  val highlightBg =
    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    else Color.Transparent

  Column(
    modifier =
      Modifier.width(columnWidth)
        .clip(RoundedCornerShape(10.dp))
        .background(highlightBg)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) {
          selected = isSelected
          contentDescription = accessibilitySummary
        }
        .padding(horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().height(plotHeight),
      contentAlignment = Alignment.BottomCenter,
    ) {
      Canvas(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
        val gapPx = 4.dp.toPx()
        val barWidthPx = ((size.width - gapPx * 3f) / 2f).coerceIn(6.dp.toPx(), 16.dp.toPx())
        val groupWidth = barWidthPx * 2f + gapPx
        val startX = (size.width - groupWidth) / 2f
        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        val minVisiblePx = 3.dp.toPx()

        if (point.totalIncome > 0.0) {
          val h = (size.height * animatedIncomeRatio).coerceAtLeast(minVisiblePx)
          drawRoundRect(
            color = incomeColor,
            topLeft = Offset(startX, size.height - h),
            size = Size(barWidthPx, h),
            cornerRadius = radius,
          )
        }

        if (point.totalExpenses > 0.0) {
          val h = (size.height * animatedExpenseRatio).coerceAtLeast(minVisiblePx)
          drawRoundRect(
            color = expenseColor,
            topLeft = Offset(startX + barWidthPx + gapPx, size.height - h),
            size = Size(barWidthPx, h),
            cornerRadius = radius,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = point.shortLabel,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color =
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Clip,
      modifier = Modifier.padding(bottom = 6.dp),
    )
  }
}

internal fun computeNiceAxisMax(maxValue: Double): Double {
  if (maxValue <= 0.0) return 10_000.0
  val magnitude = 10.0.pow(kotlin.math.floor(log10(maxValue)))
  val fraction = maxValue / magnitude
  val niceFraction =
    when {
      fraction <= 1.0 -> 1.0
      fraction <= 1.5 -> 1.5
      fraction <= 2.0 -> 2.0
      fraction <= 3.0 -> 3.0
      fraction <= 5.0 -> 5.0
      fraction <= 8.0 -> 8.0
      else -> ceil(fraction)
    }
  return niceFraction * magnitude
}
