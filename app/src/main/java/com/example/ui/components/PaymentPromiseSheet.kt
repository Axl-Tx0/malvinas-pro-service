package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.VpnAccount
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentPromiseSheet(
  vpn: VpnAccount,
  reminderHour: Int,
  onSavePromise: (Long, String) -> Unit,
  onClearPromise: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var promiseMillis by remember(vpn.id, vpn.paymentPromiseMillis) {
    mutableLongStateOf(
      if (vpn.paymentPromiseMillis > 0L) {
        vpn.paymentPromiseMillis
      } else {
        Formatters.addDaysLocalNoon(System.currentTimeMillis(), 1)
      }
    )
  }
  var promiseNote by remember(vpn.id, vpn.paymentPromiseNote) {
    mutableStateOf(vpn.paymentPromiseNote)
  }
  var showDatePicker by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .navigationBarsPadding()
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.EventAvailable,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
        )
        Column {
          Text(
            text = "Promesa de pago",
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text = "${vpn.clientName} (@${vpn.vpnUsername}) · ${Formatters.formatCurrency(vpn.amountToCharge)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      OutlinedTextField(
        value = Formatters.formatDate(promiseMillis),
        onValueChange = {},
        readOnly = true,
        label = { Text("Fecha prometida de pago") },
        trailingIcon = {
          IconButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.testTag("promise_pick_date_button"),
          ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = "Elegir fecha en calendario")
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = Formatters.daysUntilLocalDay(promiseMillis) == 0,
          onClick = { promiseMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 0) },
          label = { Text("Hoy") },
        )
        FilterChip(
          selected = Formatters.daysUntilLocalDay(promiseMillis) == 1,
          onClick = { promiseMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 1) },
          label = { Text("Mañana") },
        )
        FilterChip(
          selected = Formatters.daysUntilLocalDay(promiseMillis) == 3,
          onClick = { promiseMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 3) },
          label = { Text("+3 días") },
        )
        FilterChip(
          selected = Formatters.daysUntilLocalDay(promiseMillis) == 7,
          onClick = { promiseMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 7) },
          label = { Text("+7 días") },
        )
        FilterChip(
          selected = false,
          onClick = { showDatePicker = true },
          label = { Text("Calendario") },
        )
      }

      OutlinedTextField(
        value = promiseNote,
        onValueChange = { promiseNote = it.take(160) },
        label = { Text("Nota de compromiso (opcional)") },
        placeholder = { Text("Ej. Paga el 20 por transferencia") },
        maxLines = 2,
        modifier = Modifier.fillMaxWidth().testTag("promise_note_input"),
      )

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text =
              "La app te avisará el ${Formatters.formatDate(promiseMillis)} a las ${reminderHour.toString().padStart(2, '0')}:00 hs para cobrar a ${vpn.clientName}.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
          Text("Cancelar")
        }
        Button(
          onClick = { onSavePromise(promiseMillis, promiseNote.trim()) },
          modifier = Modifier.weight(1f).testTag("promise_save_button"),
        ) {
          Text("Guardar promesa")
        }
      }

      if (vpn.paymentPromiseMillis > 0L) {
        TextButton(
          onClick = onClearPromise,
          modifier = Modifier.fillMaxWidth().testTag("promise_clear_button"),
        ) {
          Spacer(modifier = Modifier.width(4.dp))
          Text("Quitar promesa de pago", color = MaterialTheme.colorScheme.error)
        }
      }
    }
  }

  if (showDatePicker) {
    LocalDatePickerModal(
      initialMillis = promiseMillis,
      onDateSelected = {
        promiseMillis = it
        showDatePicker = false
      },
      onDismiss = { showDatePicker = false },
    )
  }
}
