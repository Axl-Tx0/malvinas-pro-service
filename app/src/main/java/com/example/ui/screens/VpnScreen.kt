package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.VpnAccount
import com.example.ui.viewmodel.DashboardData
import com.example.util.Formatters
import kotlin.math.abs

private enum class VpnFilter(val label: String) {
  ALL("Todos"),
  PROMISES("Promesas"),
  SOON("Por vencer"),
  EXPIRED("Vencidos"),
  PAID("Pagados"),
}

@Composable
fun VpnScreen(
  data: DashboardData,
  hasNotificationPermission: Boolean,
  onRequestNotificationPermission: () -> Unit,
  onTogglePaid: (VpnAccount, Boolean) -> Unit,
  onRenew30Days: (VpnAccount) -> Unit,
  onSendWhatsApp: (VpnAccount) -> Unit,
  onOpenPromiseEditor: (VpnAccount) -> Unit,
  onClearPromise: (VpnAccount) -> Unit,
  onEditVpn: (VpnAccount) -> Unit,
  onDeleteVpn: (VpnAccount) -> Unit,
  onOpenQuickCollection: () -> Unit = {},
  onShowReceipt: (VpnAccount) -> Unit = {},
  onSendPaymentInfo: (VpnAccount) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var selectedFilter by remember { mutableStateOf(VpnFilter.ALL) }
  val urgentVpns =
    remember(data.vpnAccounts) {
      data.vpnAccounts.filter { vpn ->
        !vpn.isPaid && Formatters.daysUntilLocalDay(vpn.expirationMillis) <= 1
      }
    }
  val urgentPromises =
    remember(data.vpnAccounts) {
      data.vpnAccounts
        .filter { vpn ->
          !vpn.isPaid &&
            vpn.paymentPromiseMillis > 0L &&
            Formatters.daysUntilLocalDay(vpn.paymentPromiseMillis) <= 0
        }
        .sortedBy { it.paymentPromiseMillis }
    }

  val filteredVpns =
    remember(data.vpnAccounts, selectedFilter) {
      data.vpnAccounts.filter { vpn ->
        val daysLeft = Formatters.daysUntilLocalDay(vpn.expirationMillis)
        when (selectedFilter) {
          VpnFilter.ALL -> true
          VpnFilter.PROMISES -> !vpn.isPaid && vpn.paymentPromiseMillis > 0L
          VpnFilter.SOON -> !vpn.isPaid && daysLeft in 0..3
          VpnFilter.EXPIRED -> !vpn.isPaid && daysLeft < 0
          VpnFilter.PAID -> vpn.isPaid
        }
      }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (!hasNotificationPermission) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
          shape = MaterialTheme.shapes.medium,
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Activá los avisos de vencimiento y promesas",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            Text(
              text =
                "Permití notificaciones para que la app te avise 1 día antes de cada vencimiento y el día de cada promesa de pago.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(
              onClick = onRequestNotificationPermission,
              modifier = Modifier.testTag("grant_notification_permission_button"),
            ) {
              Text("Activar notificaciones")
            }
          }
        }
      }
    }

    if (urgentPromises.isNotEmpty()) {
      item {
        val nextPromise = urgentPromises.first()
        val daysDelta = Formatters.daysUntilLocalDay(nextPromise.paymentPromiseMillis)
        val timingLabel =
          if (daysDelta == 0) "hoy (${Formatters.formatDate(nextPromise.paymentPromiseMillis)})"
          else "el ${Formatters.formatDate(nextPromise.paymentPromiseMillis)}"
        Card(
          modifier = Modifier.fillMaxWidth().testTag("urgent_promises_banner"),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                text = "${urgentPromises.size} promesa(s) de pago para cobrar hoy",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Text(
                text =
                  "${nextPromise.clientName} (${Formatters.formatCurrency(nextPromise.amountToCharge)}) · $timingLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            Button(
              onClick = { onSendWhatsApp(nextPromise) },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
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

    if (urgentVpns.isNotEmpty()) {
      item {
        val nextUrgent = urgentVpns.first()
        Card(
          modifier = Modifier.fillMaxWidth(),
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
                text = "${urgentVpns.size} aviso(s) de clientes listos para enviar",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
              )
              Text(
                text = "Próximo: ${nextUrgent.clientName} (${Formatters.formatCurrency(nextUrgent.amountToCharge)})",
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
              Text("WhatsApp")
            }
          }
        }
      }
    }

    if (data.summary.vpnUnpaidCount > 0) {
      item {
        FilledTonalButton(
          onClick = onOpenQuickCollection,
          modifier = Modifier.fillMaxWidth().testTag("vpn_open_quick_collection_button"),
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
          Icon(
            imageVector = Icons.Filled.RocketLaunch,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              "Cola rápida de cobranza · ${data.summary.vpnUnpaidCount} pendiente(s)",
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        VpnFilter.entries.forEach { filter ->
          val label =
            if (filter == VpnFilter.PROMISES && data.summary.vpnPromiseCount > 0) {
              "${filter.label} (${data.summary.vpnPromiseCount})"
            } else {
              filter.label
            }
          FilterChip(
            selected = selectedFilter == filter,
            onClick = { selectedFilter = filter },
            label = { Text(label) },
            modifier = Modifier.testTag("vpn_filter_${filter.name.lowercase()}"),
          )
        }
      }
    }

    if (filteredVpns.isEmpty()) {
      item {
        EmptyVpnCard(
          profileLabel = data.activeProfile.label,
          isPromisesFilter = selectedFilter == VpnFilter.PROMISES,
        )
      }
    } else {
      items(filteredVpns, key = { it.id }) { vpn ->
        VpnAccountCard(
          vpn = vpn,
          defaultVpnDays = data.settings.defaultVpnDays.toInt().coerceIn(1, 365),
          onTogglePaid = { onTogglePaid(vpn, !vpn.isPaid) },
          onRenew30Days = { onRenew30Days(vpn) },
          onSendWhatsApp = { onSendWhatsApp(vpn) },
          onOpenPromiseEditor = { onOpenPromiseEditor(vpn) },
          onClearPromise = { onClearPromise(vpn) },
          onShowReceipt = { onShowReceipt(vpn) },
          onSendPaymentInfo = { onSendPaymentInfo(vpn) },
          onEdit = { onEditVpn(vpn) },
          onDelete = { onDeleteVpn(vpn) },
        )
      }
    }
  }
}

@Composable
private fun VpnAccountCard(
  vpn: VpnAccount,
  defaultVpnDays: Int,
  onTogglePaid: () -> Unit,
  onRenew30Days: () -> Unit,
  onSendWhatsApp: () -> Unit,
  onOpenPromiseEditor: () -> Unit,
  onClearPromise: () -> Unit,
  onShowReceipt: () -> Unit,
  onSendPaymentInfo: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val daysLeft = Formatters.daysUntilLocalDay(vpn.expirationMillis)
  val (statusLabel, statusBg, statusFg) =
    when {
      vpn.isPaid && daysLeft > 1 ->
        Triple(
          "Al día · Vence en $daysLeft días",
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
        )
      daysLeft < 0 ->
        Triple(
          "Vencido hace ${abs(daysLeft)} día(s)",
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.onErrorContainer,
        )
      daysLeft == 0 ->
        Triple(
          "¡Vence HOY!",
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.onErrorContainer,
        )
      daysLeft == 1 ->
        Triple(
          "Vence MAÑANA (Aviso 1 día)",
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
        )
      else ->
        Triple(
          "Vence en $daysLeft días",
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }

  val hasActivePromise = !vpn.isPaid && vpn.paymentPromiseMillis > 0L

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (!vpn.isPaid && daysLeft <= 1) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
          } else {
            MaterialTheme.colorScheme.outlineVariant
          },
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = vpn.clientName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          val detailParts =
            listOfNotNull(
              vpn.vpnUsername.takeIf { it.isNotBlank() }?.let { "@$it" },
              vpn.planName.ifBlank { "Servicio" },
              vpn.clientPhone.takeIf { it.isNotBlank() },
            )
          Text(
            text = detailParts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = Formatters.formatCurrency(vpn.amountToCharge),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "A cobrar",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = statusBg,
        ) {
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelLarge,
            color = statusFg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector =
              if (vpn.notifyOneDayBefore) Icons.Filled.NotificationsActive
              else Icons.Filled.NotificationsOff,
            contentDescription =
              if (vpn.notifyOneDayBefore) "Alerta 1 día antes activa" else "Alerta desactivada",
            tint =
              if (vpn.notifyOneDayBefore) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = Formatters.formatDate(vpn.expirationMillis),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (hasActivePromise) {
        val promiseDays = Formatters.daysUntilLocalDay(vpn.paymentPromiseMillis)
        val promiseDateText = Formatters.formatDate(vpn.paymentPromiseMillis)
        val promiseHeadline =
          when {
            promiseDays < 0 -> "Promesa vencida hace ${abs(promiseDays)} día(s) ($promiseDateText)"
            promiseDays == 0 -> "¡Promesa de pago HOY! ($promiseDateText)"
            promiseDays == 1 -> "Promesa de pago MAÑANA ($promiseDateText)"
            else -> "Promesa de pago: $promiseDateText (en $promiseDays días)"
          }
        val promiseBg =
          if (promiseDays <= 0) MaterialTheme.colorScheme.secondaryContainer
          else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        val promiseFg =
          if (promiseDays <= 0) MaterialTheme.colorScheme.onSecondaryContainer
          else MaterialTheme.colorScheme.onPrimaryContainer

        Surface(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .clickable(onClick = onOpenPromiseEditor)
              .testTag("vpn_promise_banner_${vpn.id}"),
          shape = RoundedCornerShape(10.dp),
          color = promiseBg,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.EventAvailable,
              contentDescription = null,
              tint = promiseFg,
              modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = promiseHeadline,
                style = MaterialTheme.typography.labelLarge,
                color = promiseFg,
                fontWeight = FontWeight.SemiBold,
              )
              if (vpn.paymentPromiseNote.isNotBlank()) {
                Text(
                  text = vpn.paymentPromiseNote,
                  style = MaterialTheme.typography.bodySmall,
                  color = promiseFg,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            IconButton(
              onClick = onClearPromise,
              modifier = Modifier.size(36.dp).testTag("vpn_clear_promise_${vpn.id}"),
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Quitar promesa de pago",
                tint = promiseFg,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        OutlinedButton(
          onClick = onTogglePaid,
          modifier = Modifier.weight(1f).testTag("vpn_paid_toggle_${vpn.id}"),
          shape = RoundedCornerShape(10.dp),
          colors =
            if (vpn.isPaid) {
              ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
            } else {
              ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            },
        ) {
          Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(if (vpn.isPaid) "Pagado" else "Pendiente", maxLines = 1)
        }

        Button(
          onClick = onRenew30Days,
          modifier = Modifier.weight(1.15f).testTag("vpn_renew_button_${vpn.id}"),
          shape = RoundedCornerShape(10.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Autorenew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Cobrar +${defaultVpnDays}d", maxLines = 1)
        }

        IconButton(
          onClick = onShowReceipt,
          modifier = Modifier.testTag("vpn_receipt_button_${vpn.id}"),
        ) {
          Icon(
            imageVector = Icons.Filled.ReceiptLong,
            contentDescription = "Enviar comprobante de pago",
            tint =
              if (vpn.isPaid) MaterialTheme.colorScheme.tertiary
              else MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(onClick = onEdit) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = "Editar cliente",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onDelete) {
          Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = "Eliminar cliente",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = onOpenPromiseEditor,
          modifier = Modifier.weight(0.9f).testTag("vpn_promise_button_${vpn.id}"),
          shape = RoundedCornerShape(10.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
          Icon(
            imageVector = Icons.Filled.EventAvailable,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(if (hasActivePromise) "Cambiar fecha" else "Promesa", maxLines = 1)
        }

        FilledTonalButton(
          onClick = onSendWhatsApp,
          modifier = Modifier.weight(1.1f).testTag("vpn_whatsapp_button_${vpn.id}"),
          shape = RoundedCornerShape(10.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = MaterialTheme.colorScheme.tertiaryContainer,
              contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "WhatsApp",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        IconButton(
          onClick = onSendPaymentInfo,
          modifier = Modifier.testTag("vpn_payment_info_button_${vpn.id}"),
        ) {
          Icon(
            imageVector = Icons.Filled.AccountBalance,
            contentDescription = "Enviar datos de cobro (Alias / CBU)",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyVpnCard(
  profileLabel: String,
  isPromisesFilter: Boolean = false,
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
        imageVector = if (isPromisesFilter) Icons.Filled.EventAvailable else Icons.Filled.People,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp),
      )
      Text(
        text =
          if (isPromisesFilter) "Sin promesas de pago en $profileLabel"
          else "Sin clientes en $profileLabel",
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        text =
          if (isPromisesFilter) {
            "Tocá 'Promesa' en cualquier cliente para agendar la fecha en que te prometió pagar y recibir el aviso ese día."
          } else {
            "Tocá + Cliente para registrar vencimientos, el monto a cobrar, promesas de pago y tu aviso 1 día antes."
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
