package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.UserSettings
import com.example.data.model.VpnAccount
import com.example.util.Formatters
import kotlin.math.abs

private enum class QueueScope(val label: String) {
  URGENT("Urgentes y promesas"),
  ALL_UNPAID("Todos pendientes"),
}

@Composable
fun QuickCollectionDialog(
  vpnAccounts: List<VpnAccount>,
  settings: UserSettings,
  onSendWhatsApp: (VpnAccount) -> Unit,
  onSendPaymentInfo: (VpnAccount) -> Unit = {},
  onCollectAndRenew: (VpnAccount) -> Unit,
  onDismiss: () -> Unit,
) {
  var scope by remember { mutableStateOf(QueueScope.URGENT) }
  var sentIds by remember { mutableStateOf(setOf<String>()) }
  var collectedIds by remember { mutableStateOf(setOf<String>()) }
  var activeIndex by remember { mutableIntStateOf(0) }

  val queue =
    remember(vpnAccounts, scope) {
      val base =
        when (scope) {
          QueueScope.URGENT ->
            vpnAccounts.filter { vpn ->
              !vpn.isPaid &&
                (Formatters.daysUntilLocalDay(vpn.expirationMillis) <= 1 ||
                  (vpn.paymentPromiseMillis > 0L &&
                    Formatters.daysUntilLocalDay(vpn.paymentPromiseMillis) <= 0))
            }
          QueueScope.ALL_UNPAID -> vpnAccounts.filter { !it.isPaid }
        }
      base.sortedBy { vpn ->
        if (vpn.paymentPromiseMillis > 0L && Formatters.daysUntilLocalDay(vpn.paymentPromiseMillis) <= 0) {
          vpn.paymentPromiseMillis
        } else {
          vpn.expirationMillis
        }
      }
    }

  val clampedIndex =
    if (queue.isEmpty()) 0 else activeIndex.coerceIn(0, queue.lastIndex)
  val current = queue.getOrNull(clampedIndex)
  val handledCount =
    queue.count { it.id in sentIds || it.id in collectedIds }
  val totalAmountPending = queue.sumOf { it.amountToCharge }
  val defaultDays = settings.defaultVpnDays.toInt().coerceIn(1, 365)

  fun advanceQueue() {
    if (queue.isEmpty()) return
    val nextUnhandled =
      ((clampedIndex + 1) until queue.size).firstOrNull { idx ->
        val id = queue[idx].id
        id !in sentIds && id !in collectedIds
      }
    activeIndex =
      nextUnhandled ?: ((clampedIndex + 1) % queue.size)
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Card(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 20.dp)
          .widthIn(max = 560.dp)
          .testTag("quick_collection_dialog"),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        AlbicelesteRibbonStripe(height = 5.dp, showSunBadge = true)
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
              )
              Column {
                Text(
                  text = "Cola rápida de cobranza",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Enviá avisos de WhatsApp uno tras otro y cobrá sin perder el ritmo",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("quick_collection_close_button"),
            ) {
              Icon(Icons.Filled.Close, contentDescription = "Cerrar cola de cobranza")
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QueueScope.entries.forEach { option ->
              FilterChip(
                selected = scope == option,
                onClick = {
                  scope = option
                  activeIndex = 0
                },
                label = { Text(option.label) },
                modifier = Modifier.testTag("queue_scope_${option.name.lowercase()}"),
              )
            }
          }

          if (queue.isEmpty()) {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.size(36.dp),
                )
                Text(
                  text = "¡Excelente! No tenés cobros pendientes aquí",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    if (scope == QueueScope.URGENT) {
                      "Podés cambiar a 'Todos pendientes' si querés adelantar recordatorios."
                    } else {
                      "Todos tus clientes figuran al día."
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            val progress = (handledCount.toFloat() / queue.size.toFloat()).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Progreso: $handledCount de ${queue.size} atendidos",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Pendiente: ${Formatters.formatCurrency(totalAmountPending)}",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
              }
              LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.tertiary,
              )
            }

            current?.let { vpn ->
              val daysLeft = Formatters.daysUntilLocalDay(vpn.expirationMillis)
              val statusText =
                when {
                  vpn.paymentPromiseMillis > 0L && Formatters.daysUntilLocalDay(vpn.paymentPromiseMillis) <= 0 ->
                    "Promesa de pago: ${Formatters.formatDate(vpn.paymentPromiseMillis)}"
                  daysLeft < 0 -> "Vencido hace ${abs(daysLeft)} día(s)"
                  daysLeft == 0 -> "¡Vence HOY!"
                  daysLeft == 1 -> "Vence MAÑANA"
                  else -> "Vence el ${Formatters.formatDate(vpn.expirationMillis)}"
                }
              val isSent = vpn.id in sentIds
              val isCollected = vpn.id in collectedIds

              Surface(
                modifier = Modifier.fillMaxWidth().testTag("quick_collection_focus_card"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "Cliente ${clampedIndex + 1} de ${queue.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = vpn.clientName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      val meta =
                        listOfNotNull(
                          vpn.vpnUsername.takeIf { it.isNotBlank() }?.let { "@$it" },
                          vpn.planName.takeIf { it.isNotBlank() },
                          vpn.clientPhone.takeIf { it.isNotBlank() } ?: "Sin teléfono",
                        ).joinToString(" · ")
                      Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    Text(
                      text = Formatters.formatCurrency(vpn.amountToCharge),
                      style = MaterialTheme.typography.titleLarge,
                      fontWeight = FontWeight.ExtraBold,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }

                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color =
                        if (daysLeft <= 0) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                      Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color =
                          if (daysLeft <= 0) MaterialTheme.colorScheme.onErrorContainer
                          else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                      )
                    }
                    if (isSent) {
                      Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                      ) {
                        Text(
                          text = "Aviso enviado",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onTertiaryContainer,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                      }
                    }
                  }

                  Button(
                    onClick = {
                      sentIds = sentIds + vpn.id
                      onSendWhatsApp(vpn)
                    },
                    modifier =
                      Modifier.fillMaxWidth()
                        .height(50.dp)
                        .testTag("queue_send_whatsapp_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                      ),
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.Send,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      if (isSent) "Reenviar WhatsApp" else "Enviar aviso por WhatsApp",
                      fontWeight = FontWeight.SemiBold,
                    )
                  }

                  OutlinedButton(
                    onClick = { onSendPaymentInfo(vpn) },
                    modifier =
                      Modifier.fillMaxWidth()
                        .height(48.dp)
                        .testTag("queue_send_payment_info_button"),
                    shape = RoundedCornerShape(12.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.AccountBalance,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enviar datos de cobro (Alias / CBU)", maxLines = 1)
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    FilledTonalButton(
                      onClick = {
                        collectedIds = collectedIds + vpn.id
                        onCollectAndRenew(vpn)
                      },
                      enabled = !isCollected,
                      modifier = Modifier.weight(1.2f).height(48.dp).testTag("queue_collect_button"),
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Autorenew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Cobrar +${defaultDays}d", maxLines = 1)
                    }

                    OutlinedButton(
                      onClick = { advanceQueue() },
                      modifier = Modifier.weight(0.8f).height(48.dp).testTag("queue_next_button"),
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Text("Siguiente", maxLines = 1)
                      Spacer(modifier = Modifier.width(4.dp))
                      Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  }
                }
              }
            }

            HorizontalDivider()

            LazyColumn(
              modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              itemsIndexed(queue, key = { _, item -> item.id }) { idx, vpn ->
                val isCurrent = idx == clampedIndex
                val isSent = vpn.id in sentIds
                val isCollected = vpn.id in collectedIds
                Surface(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(10.dp))
                      .clickable { activeIndex = idx },
                  shape = RoundedCornerShape(10.dp),
                  color =
                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                  border =
                    if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = vpn.clientName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "${Formatters.formatCurrency(vpn.amountToCharge)} · ${Formatters.formatDate(vpn.expirationMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    if (isCollected) {
                      Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Cobrado",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                      )
                    } else if (isSent) {
                      Icon(
                        imageVector = Icons.Filled.MarkChatRead,
                        contentDescription = "WhatsApp enviado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
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
  }
}
