package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FinancialTransaction
import com.example.data.model.ProfileType
import com.example.data.model.TransactionType
import com.example.ui.components.AlbicelesteRibbonStripe
import com.example.ui.viewmodel.DashboardData
import com.example.util.Formatters

private enum class TxFilter(val label: String) {
  ALL("Todos"),
  INCOME("Ingresos"),
  EXPENSE("Gastos"),
}

@Composable
fun OverviewScreen(
  data: DashboardData,
  onEditTransaction: (FinancialTransaction) -> Unit,
  onDeleteTransaction: (FinancialTransaction) -> Unit,
  onOpenVpnTab: () -> Unit,
  onOpenResellersTab: () -> Unit,
  onOpenFinancialSummary: () -> Unit,
  onOpenQuickCollection: () -> Unit = {},
  onOpenPaymentDetails: () -> Unit = {},
  onExportMonthPdf: () -> Unit = {},
  onExportMonthCsv: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var selectedFilter by remember { mutableStateOf(TxFilter.ALL) }

  val filteredTransactions =
    remember(data.transactions, selectedFilter) {
      when (selectedFilter) {
        TxFilter.ALL -> data.transactions
        TxFilter.INCOME -> data.transactions.filter { it.type == TransactionType.INCOME.id }
        TxFilter.EXPENSE -> data.transactions.filter { it.type == TransactionType.EXPENSE.id }
      }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      NetBalanceHeroCard(
        data = data,
        onOpenFinancialSummary = onOpenFinancialSummary,
      )
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        MetricQuickCard(
          title = "Clientes pendientes",
          value = Formatters.formatCurrency(data.summary.vpnPendingAmount),
          subtitle = "${data.summary.vpnUnpaidCount} sin pagar · ${data.summary.vpnPaidCount} al día",
          icon = Icons.Filled.People,
          accentColor = MaterialTheme.colorScheme.secondary,
          onClick = onOpenVpnTab,
          modifier = Modifier.weight(1f).testTag("summary_vpn_pending_card"),
        )
        MetricQuickCard(
          title = "Clientes por vencer",
          value = "${data.summary.vpnDueSoonCount}",
          subtitle =
            if (data.summary.vpnPendingAmount > 0) {
              "Cobrar ${Formatters.formatCurrency(data.summary.vpnPendingAmount)}"
            } else {
              "Todo al día"
            },
          icon = Icons.Filled.NotificationsActive,
          accentColor =
            if (data.summary.vpnDueSoonCount > 0) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.primary
            },
          onClick = onOpenVpnTab,
          modifier = Modifier.weight(1f).testTag("summary_vpn_alert_card"),
        )
      }
    }

    item {
      val resellerAlertSuffix =
        if (data.summary.resellerDueSoonCount > 0) {
          " · ${data.summary.resellerDueSoonCount} por vencer"
        } else {
          ""
        }
      MetricQuickCard(
        title = "Revendedores y créditos (30 días)",
        value = Formatters.formatCurrency(data.summary.totalResellerAmount),
        subtitle =
          "${data.summary.resellerCount} revendedor(es) · ${data.summary.totalResellerCredits} créditos$resellerAlertSuffix",
        icon = Icons.Filled.Storefront,
        accentColor =
          if (data.summary.resellerDueSoonCount > 0) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.primary
          },
        onClick = onOpenResellersTab,
        modifier = Modifier.fillMaxWidth().testTag("summary_resellers_card"),
      )
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.summary.vpnUnpaidCount > 0) {
          FilledTonalButton(
            onClick = onOpenQuickCollection,
            modifier = Modifier.fillMaxWidth().testTag("overview_quick_collection_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.RocketLaunch,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Cola rápida de cobranza (${data.summary.vpnUnpaidCount} pendiente(s))",
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
        FilledTonalButton(
          onClick = onOpenPaymentDetails,
          modifier = Modifier.fillMaxWidth().testTag("overview_payment_details_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.AccountBalance,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              if (data.settings.paymentAlias.isNotBlank()) {
                "Datos de cobro · Alias: ${data.settings.paymentAlias}"
              } else if (data.settings.hasPaymentInfo) {
                "Datos de cobro listos para enviar"
              } else {
                "Configurar datos de cobro (Alias / CBU / MP)"
              },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onExportMonthPdf,
            modifier = Modifier.weight(1f).testTag("overview_export_pdf_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.PictureAsPdf,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Balance PDF", maxLines = 1)
          }
          OutlinedButton(
            onClick = onExportMonthCsv,
            modifier = Modifier.weight(1f).testTag("overview_export_csv_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.TableChart,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Excel (CSV)", maxLines = 1)
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Movimientos del mes",
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = "${filteredTransactions.size} registros",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TxFilter.entries.forEach { filter ->
          FilterChip(
            selected = selectedFilter == filter,
            onClick = { selectedFilter = filter },
            label = { Text(filter.label) },
          )
        }
      }
    }

    if (filteredTransactions.isEmpty()) {
      item {
        EmptyLedgerCard(
          title = "Sin movimientos en ${Formatters.formatMonthTitle(data.selectedMonthKey)}",
          subtitle =
            "Tocá el botón + Movimiento para anotar tus ingresos o gastos en ${data.activeProfile.label}.",
        )
      }
    } else {
      items(filteredTransactions, key = { it.id }) { tx ->
        TransactionRowCard(
          tx = tx,
          onEdit = { onEditTransaction(tx) },
          onDelete = { onDeleteTransaction(tx) },
        )
      }
    }
  }
}

@Composable
private fun NetBalanceHeroCard(
  data: DashboardData,
  onOpenFinancialSummary: () -> Unit,
) {
  val isPremium = data.activeProfile == ProfileType.PREMIUM
  val gradientStart =
    if (isPremium) {
      MaterialTheme.colorScheme.secondaryContainer
    } else {
      MaterialTheme.colorScheme.primaryContainer
    }
  val gradientEnd = MaterialTheme.colorScheme.surface
  val incomeColor = MaterialTheme.colorScheme.tertiary
  val expenseColor = MaterialTheme.colorScheme.error

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      AlbicelesteRibbonStripe(height = 5.dp, showSunBadge = true)
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(gradientStart.copy(alpha = 0.7f), gradientEnd)))
            .padding(18.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "Balance neto · ${data.activeProfile.shortLabel}",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = Formatters.formatCurrency(data.summary.netBalance),
              style = MaterialTheme.typography.displaySmall,
              color =
                if (data.summary.netBalance >= 0) {
                  MaterialTheme.colorScheme.onSurface
                } else {
                  MaterialTheme.colorScheme.error
                },
            )
          }
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier.size(46.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint =
                  if (isPremium) {
                    MaterialTheme.colorScheme.secondary
                  } else {
                    MaterialTheme.colorScheme.primary
                  },
              )
            }
          }
        }

        // Proportion Bar
        val totalFlow = data.summary.totalIncome + data.summary.totalExpenses
        val incomeRatio =
          if (totalFlow > 0.0) (data.summary.totalIncome / totalFlow).toFloat().coerceIn(0f, 1f)
          else 0.5f

        Canvas(
          modifier =
            Modifier.fillMaxWidth()
              .height(10.dp)
              .clip(RoundedCornerShape(6.dp))
        ) {
          drawRoundRect(
            color = expenseColor.copy(alpha = if (totalFlow > 0.0) 0.9f else 0.2f),
            size = size,
            cornerRadius = CornerRadius(12f, 12f),
          )
          if (totalFlow > 0.0 && incomeRatio > 0f) {
            drawRoundRect(
              color = incomeColor,
              size = Size(width = size.width * incomeRatio, height = size.height),
              cornerRadius = CornerRadius(12f, 12f),
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.ArrowUpward,
              contentDescription = null,
              tint = incomeColor,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
              Text(
                text = "Ingresos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = Formatters.formatCurrency(data.summary.totalIncome),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = incomeColor,
              )
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.ArrowDownward,
              contentDescription = null,
              tint = expenseColor,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "Gastos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = Formatters.formatCurrency(data.summary.totalExpenses),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = expenseColor,
              )
            }
          }
        }

        TextButton(
          onClick = onOpenFinancialSummary,
          modifier = Modifier.align(Alignment.End).testTag("overview_open_chart_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.BarChart,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Ver gráfico mensual")
        }
      }
      }
    }
  }
}

@Composable
private fun MetricQuickCard(
  title: String,
  value: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun TransactionRowCard(
  tx: FinancialTransaction,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val isIncome = tx.type == TransactionType.INCOME.id
  val accentColor =
    if (isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
  val bgColor =
    if (isIncome) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier.size(42.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (isIncome) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = if (isIncome) "Ingreso" else "Gasto",
            tint = accentColor,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = tx.title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${tx.category.ifBlank { "General" }} · ${Formatters.formatDate(tx.dateMillis)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = (if (isIncome) "+" else "-") + Formatters.formatCurrency(tx.amount),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = accentColor,
        )
        Row {
          IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = "Editar movimiento",
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Eliminar movimiento",
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}

@Composable
fun EmptyLedgerCard(
  title: String,
  subtitle: String,
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
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.AccountBalanceWallet,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp),
      )
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
