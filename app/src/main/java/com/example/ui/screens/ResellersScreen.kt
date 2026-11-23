package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ProfileType
import com.example.data.model.Reseller
import com.example.ui.components.AlbicelesteRibbonStripe
import com.example.ui.viewmodel.DashboardData
import com.example.util.Formatters
import kotlin.math.abs

private enum class ResellerFilter(val label: String) {
  ALL("Todos"),
  SOON("Por vencer"),
  EXPIRED("Vencidos"),
  WITH_CREDITS("Con créditos"),
  ZERO_CREDITS("Sin créditos"),
}

@Composable
fun ResellersScreen(
  data: DashboardData,
  onAdjustCredits: (Reseller, Long) -> Unit,
  onRenew30Days: (Reseller) -> Unit,
  onSendWhatsApp: (Reseller) -> Unit,
  onEditReseller: (Reseller) -> Unit,
  onDeleteReseller: (Reseller) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedFilter by remember { mutableStateOf(ResellerFilter.ALL) }

  val urgentResellers =
    remember(data.resellers) {
      data.resellers.filter { reseller ->
        reseller.expirationMillis > 0L && Formatters.daysUntilLocalDay(reseller.expirationMillis) <= 3
      }
    }

  val filteredResellers =
    remember(data.resellers, selectedFilter) {
      when (selectedFilter) {
        ResellerFilter.ALL -> data.resellers
        ResellerFilter.SOON ->
          data.resellers.filter {
            it.expirationMillis > 0L && Formatters.daysUntilLocalDay(it.expirationMillis) in 0..3
          }
        ResellerFilter.EXPIRED ->
          data.resellers.filter {
            it.expirationMillis > 0L && Formatters.daysUntilLocalDay(it.expirationMillis) < 0
          }
        ResellerFilter.WITH_CREDITS -> data.resellers.filter { it.creditsCount > 0L }
        ResellerFilter.ZERO_CREDITS -> data.resellers.filter { it.creditsCount <= 0L }
      }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      ResellersSummaryHeroCard(data = data)
    }

    if (urgentResellers.isNotEmpty()) {
      item {
        val nextUrgent = urgentResellers.first()
        Card(
          modifier = Modifier.fillMaxWidth().testTag("resellers_urgent_banner"),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
          shape = MaterialTheme.shapes.medium,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${urgentResellers.size} revendedor(es) con vencimiento próximo o vencido",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
              )
              Text(
                text =
                  "Próximo: ${nextUrgent.name} · ${Formatters.formatCurrency(nextUrgent.computedTotal)} (${Formatters.formatDate(nextUrgent.expirationMillis)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
              )
            }
            Button(
              onClick = { onSendWhatsApp(nextUrgent) },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.tertiary,
                  contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Cobrar")
            }
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
          text = "Revendedores (${filteredResellers.size})",
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = "${data.summary.totalResellerCredits} créditos",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ResellerFilter.entries.forEach { filter ->
          FilterChip(
            selected = selectedFilter == filter,
            onClick = { selectedFilter = filter },
            label = { Text(filter.label) },
            modifier = Modifier.testTag("reseller_filter_${filter.name.lowercase()}"),
          )
        }
      }
    }

    if (filteredResellers.isEmpty()) {
      item {
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
              imageVector = Icons.Filled.Storefront,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(36.dp),
            )
            Text(
              text = "Sin revendedores en esta vista",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "Tocá + Revendedor para cargar créditos, precio c/u y fecha de vencimiento cada 30 días.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(filteredResellers, key = { it.id }) { reseller ->
        ResellerCard(
          reseller = reseller,
          onDecreaseCredit = {
            if (reseller.creditsCount > 0L) {
              onAdjustCredits(reseller, reseller.creditsCount - 1L)
            }
          },
          onIncreaseCredit = {
            onAdjustCredits(reseller, (reseller.creditsCount + 1L).coerceAtMost(1_000_000L))
          },
          onRenew30Days = { onRenew30Days(reseller) },
          onSendWhatsApp = { onSendWhatsApp(reseller) },
          onEdit = { onEditReseller(reseller) },
          onDelete = { onDeleteReseller(reseller) },
        )
      }
    }
  }
}

@Composable
private fun ResellersSummaryHeroCard(data: DashboardData) {
  val isPremium = data.activeProfile == ProfileType.PREMIUM
  val gradientStart =
    if (isPremium) {
      MaterialTheme.colorScheme.secondaryContainer
    } else {
      MaterialTheme.colorScheme.primaryContainer
    }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("resellers_hero_card"),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      AlbicelesteRibbonStripe(height = 5.dp, showSunBadge = true)
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(
              Brush.linearGradient(
                listOf(gradientStart.copy(alpha = 0.72f), MaterialTheme.colorScheme.surface)
              )
            )
            .padding(18.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "Total general de créditos · ${data.activeProfile.shortLabel}",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = Formatters.formatCurrency(data.summary.totalResellerAmount),
              style = MaterialTheme.typography.displaySmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            modifier = Modifier.size(46.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.Storefront,
                contentDescription = null,
                tint =
                  if (isPremium) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            modifier = Modifier.weight(1f),
          ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
              Text(
                text = "Créditos activos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "${data.summary.totalResellerCredits}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            modifier = Modifier.weight(1f),
          ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
              Text(
                text = "Ciclo 30d por vencer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "${data.summary.resellerDueSoonCount}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color =
                  if (data.summary.resellerDueSoonCount > 0) MaterialTheme.colorScheme.error
                  else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
      }
    }
  }
}

@Composable
private fun ResellerCard(
  reseller: Reseller,
  onDecreaseCredit: () -> Unit,
  onIncreaseCredit: () -> Unit,
  onRenew30Days: () -> Unit,
  onSendWhatsApp: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val hasExpiration = reseller.expirationMillis > 0L
  val daysLeft = if (hasExpiration) Formatters.daysUntilLocalDay(reseller.expirationMillis) else Int.MAX_VALUE
  val (statusBg, statusFg, statusText) =
    when {
      !hasExpiration ->
        Triple(
          MaterialTheme.colorScheme.surfaceVariant,
          MaterialTheme.colorScheme.onSurfaceVariant,
          "Sin vencimiento asignado (tocá Editar o +30d)",
        )
      daysLeft < 0 ->
        Triple(
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.onErrorContainer,
          "Vencido hace ${abs(daysLeft)} día(s) · ${Formatters.formatDate(reseller.expirationMillis)}",
        )
      daysLeft == 0 ->
        Triple(
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.onErrorContainer,
          "Vence HOY (${Formatters.formatDate(reseller.expirationMillis)})",
        )
      daysLeft == 1 ->
        Triple(
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
          "Vence MAÑANA (${Formatters.formatDate(reseller.expirationMillis)})",
        )
      daysLeft <= 3 ->
        Triple(
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
          "Vence en $daysLeft días (${Formatters.formatDate(reseller.expirationMillis)})",
        )
      else ->
        Triple(
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.onPrimaryContainer,
          "Vence en $daysLeft días · ${Formatters.formatDate(reseller.expirationMillis)}",
        )
    }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("reseller_card_${reseller.id}"),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(42.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.ConfirmationNumber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = reseller.name,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (reseller.phone.isNotBlank()) {
              Text(
                text = reseller.phone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
              )
            } else if (reseller.notes.isNotBlank()) {
              Text(
                text = reseller.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        Row {
          IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
            Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = "Editar revendedor",
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Eliminar revendedor",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      // Expiration / 30-day cycle status banner
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = statusBg.copy(alpha = 0.65f),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Event,
            contentDescription = null,
            tint = statusFg,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = statusFg,
          )
        }
      }

      // 3-part breakdown: Créditos | Precio c/u | Total
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Créditos",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "${reseller.creditsCount}",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "Valor c/u",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = Formatters.formatCurrency(reseller.pricePerCredit),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
          }
          Column(
            modifier = Modifier.weight(1.1f),
            horizontalAlignment = Alignment.End,
          ) {
            Text(
              text = "Total",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = Formatters.formatCurrency(reseller.computedTotal),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      if (reseller.notes.isNotBlank() && reseller.phone.isNotBlank()) {
        Text(
          text = reseller.notes,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilledTonalIconButton(
          onClick = onDecreaseCredit,
          enabled = reseller.creditsCount > 0L,
          modifier = Modifier.size(48.dp).testTag("reseller_minus_${reseller.id}"),
        ) {
          Icon(
            imageVector = Icons.Filled.Remove,
            contentDescription = "Restar 1 crédito",
          )
        }
        FilledTonalIconButton(
          onClick = onIncreaseCredit,
          modifier = Modifier.size(48.dp).testTag("reseller_plus_${reseller.id}"),
        ) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Sumar 1 crédito",
          )
        }
        FilledTonalButton(
          onClick = onRenew30Days,
          modifier = Modifier.weight(1f).testTag("reseller_renew_${reseller.id}"),
          contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Autorenew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("+30 días", maxLines = 1)
        }
        OutlinedButton(
          onClick = onSendWhatsApp,
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("WhatsApp", maxLines = 1)
        }
      }
    }
  }
}
