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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.UserSettings
import com.example.util.Formatters
import com.example.util.PaymentReceiptData
import com.example.util.WhatsAppHelper

@Composable
fun PaymentReceiptDialog(
  receipt: PaymentReceiptData,
  settings: UserSettings,
  onSendWhatsApp: (String) -> Unit,
  onCopyReceipt: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val businessName = settings.businessName.ifBlank { UserSettings.DEFAULT_BUSINESS_NAME }
  val formattedMessage =
    remember(receipt, settings) {
      WhatsAppHelper.buildPaymentReceiptMessage(receipt = receipt, settings = settings)
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Card(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 24.dp)
          .widthIn(max = 460.dp)
          .testTag("payment_receipt_dialog"),
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
              .padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.size(56.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = "Pago confirmado",
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.padding(12.dp),
            )
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "Comprobante de pago",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
            )
            Text(
              text = businessName,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
          }

          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                  )
                  Text(
                    text = "N° ${receipt.receiptCode}",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                  Text(
                    text = "PAGADO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
              }

              HorizontalDivider()

              ReceiptDetailRow(label = "Cliente", value = receipt.recipientName)
              if (receipt.serviceDetail.isNotBlank()) {
                ReceiptDetailRow(label = "Detalle", value = receipt.serviceDetail)
              }
              ReceiptDetailRow(
                label = "Fecha de pago",
                value = Formatters.formatDate(receipt.paidAtMillis),
              )
              if (receipt.nextExpirationMillis > 0L) {
                ReceiptDetailRow(
                  label = "Próximo vencimiento",
                  value = Formatters.formatDate(receipt.nextExpirationMillis),
                )
              }

              HorizontalDivider()

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Monto abonado",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = Formatters.formatCurrency(receipt.amount),
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.ExtraBold,
                  color = MaterialTheme.colorScheme.tertiary,
                )
              }
            }
          }

          Button(
            onClick = { onSendWhatsApp(formattedMessage) },
            modifier =
              Modifier.fillMaxWidth()
                .height(50.dp)
                .testTag("receipt_send_whatsapp_button"),
            shape = RoundedCornerShape(14.dp),
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
            Text("Enviar comprobante por WhatsApp", fontWeight = FontWeight.SemiBold)
          }

          OutlinedButton(
            onClick = { onCopyReceipt(formattedMessage) },
            modifier =
              Modifier.fillMaxWidth()
                .height(48.dp)
                .testTag("receipt_copy_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.ContentCopy,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copiar comprobante")
          }

          TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("receipt_close_button"),
          ) {
            Text("Cerrar")
          }
        }
      }
    }
  }
}

@Composable
private fun ReceiptDetailRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      textAlign = TextAlign.End,
      modifier = Modifier.weight(1f, fill = false),
    )
  }
}
