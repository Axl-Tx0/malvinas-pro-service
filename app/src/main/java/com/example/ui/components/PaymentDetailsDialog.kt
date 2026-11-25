package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.UserSettings
import com.example.util.Formatters
import com.example.util.WhatsAppHelper

data class PaymentDetailsTarget(
  val recipientName: String = "",
  val recipientPhone: String = "",
  val serviceDetail: String = "",
  val amount: Double? = null,
)

@Composable
fun PaymentDetailsDialog(
  target: PaymentDetailsTarget,
  settings: UserSettings,
  onSendWhatsApp: (phone: String, message: String) -> Unit,
  onCopyText: (label: String, text: String) -> Unit,
  onOpenSettings: () -> Unit,
  onDismiss: () -> Unit,
) {
  val hasAmount = (target.amount ?: 0.0) > 0.0
  var includeAmount by remember(target) { mutableStateOf(hasAmount) }

  val messagePreview =
    remember(settings, target, includeAmount) {
      WhatsAppHelper.buildStandalonePaymentInfoMessage(
        settings = settings,
        recipientName = target.recipientName,
        amount = if (includeAmount) target.amount else null,
        serviceDetail = target.serviceDetail,
      )
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Card(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 20.dp)
          .widthIn(max = 520.dp)
          .testTag("payment_details_dialog"),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        AlbicelesteRibbonStripe(height = 5.dp, showSunBadge = true)
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(18.dp),
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
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Filled.AccountBalance,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column {
                Text(
                  text = "Datos de cobro",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    if (target.recipientName.isNotBlank()) {
                      "Para ${target.recipientName}"
                    } else {
                      "Alias, CBU/CVU y link listos para compartir"
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("payment_details_close_button"),
            ) {
              Icon(Icons.Filled.Close, contentDescription = "Cerrar datos de cobro")
            }
          }

          if (!settings.hasPaymentInfo) {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "Todavía no cargaste tus datos de cobro",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    "Guardá tu Alias, CBU/CVU, titular o link de Mercado Pago en Ajustes para enviarlos en un toque por WhatsApp.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Button(
              onClick = {
                onDismiss()
                onOpenSettings()
              },
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("payment_details_configure_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Configurar en Ajustes")
            }
          } else {
            // Quick 1-tap copy chips for Alias and CBU/CVU
            if (settings.paymentAlias.isNotBlank() || settings.paymentCbuCvu.isNotBlank()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                if (settings.paymentAlias.isNotBlank()) {
                  FilledTonalButton(
                    onClick = { onCopyText("Alias", settings.paymentAlias.trim()) },
                    modifier = Modifier.weight(1f).testTag("payment_copy_alias_button"),
                    shape = RoundedCornerShape(10.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.ContentCopy,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar Alias", maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
                if (settings.paymentCbuCvu.isNotBlank()) {
                  FilledTonalButton(
                    onClick = { onCopyText("CBU/CVU", settings.paymentCbuCvu.trim()) },
                    modifier = Modifier.weight(1f).testTag("payment_copy_cbu_button"),
                    shape = RoundedCornerShape(10.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.ContentCopy,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar CBU/CVU", maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }

            if (hasAmount) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Incluir importe (${Formatters.formatCurrency(target.amount ?: 0.0)})",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                )
                Switch(
                  checked = includeAmount,
                  onCheckedChange = { includeAmount = it },
                  modifier = Modifier.testTag("payment_details_include_amount_switch"),
                )
              }
            }

            Text(
              text = "Mensaje listo para enviar",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )

            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
              Text(
                text = messagePreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp),
              )
            }

            Button(
              onClick = { onSendWhatsApp(target.recipientPhone, messagePreview) },
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("payment_details_send_whatsapp_button"),
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
              Text("Enviar por WhatsApp", fontWeight = FontWeight.SemiBold)
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = { onCopyText("Datos de cobro", messagePreview) },
                modifier = Modifier.weight(1f).height(48.dp).testTag("payment_details_copy_all_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.ContentCopy,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copiar mensaje", maxLines = 1)
              }
              OutlinedButton(
                onClick = {
                  onDismiss()
                  onOpenSettings()
                },
                modifier = Modifier.height(48.dp).testTag("payment_details_edit_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Settings,
                  contentDescription = "Editar datos de cobro",
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
