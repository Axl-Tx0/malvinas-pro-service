package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.FinancialTransaction
import com.example.data.model.ProfileType
import com.example.data.model.ServicePlan
import com.example.data.model.TransactionType
import com.example.data.model.VpnAccount
import com.example.util.Formatters
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorSheet(
  existing: FinancialTransaction?,
  defaultProfile: ProfileType,
  onSave: (ProfileType, TransactionType, String, String, Double, Long, String) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val profile = ProfileType.PREMIUM
  var txType by remember {
    mutableStateOf(existing?.let { TransactionType.fromId(it.type) } ?: TransactionType.INCOME)
  }
  var title by remember { mutableStateOf(existing?.title ?: "") }
  var category by remember { mutableStateOf(existing?.category ?: "Servicios") }
  var amountText by remember {
    mutableStateOf(if (existing != null && existing.amount > 0) existing.amount.toBigDecimal().stripTrailingZeros().toPlainString() else "")
  }
  var dateMillis by remember { mutableLongStateOf(existing?.dateMillis ?: System.currentTimeMillis()) }
  var notes by remember { mutableStateOf(existing?.notes ?: "") }
  var showDatePicker by remember { mutableStateOf(false) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val categoryOptions =
    if (txType == TransactionType.INCOME) {
      listOf("Servicios", "Clientes", "Instalación", "Soporte", "Otros")
    } else {
      listOf("Servidores", "Internet", "Insumos", "Impuestos", "Otros")
    }

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
      Text(
        text = if (existing == null) "Nuevo movimiento" else "Editar movimiento",
        style = MaterialTheme.typography.headlineSmall,
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = txType == TransactionType.INCOME,
          onClick = {
            txType = TransactionType.INCOME
            if (category !in listOf("Servicios", "Clientes", "Instalación", "Soporte", "Otros")) {
              category = "Servicios"
            }
          },
          label = { Text("Ingreso (+)") },
          modifier = Modifier.weight(1f).testTag("tx_type_income"),
        )
        FilterChip(
          selected = txType == TransactionType.EXPENSE,
          onClick = {
            txType = TransactionType.EXPENSE
            if (category !in listOf("Servidores", "Internet", "Insumos", "Impuestos", "Otros")) {
              category = "Servidores"
            }
          },
          label = { Text("Gasto (-)") },
          modifier = Modifier.weight(1f).testTag("tx_type_expense"),
        )
      }

      OutlinedTextField(
        value = title,
        onValueChange = {
          title = it.take(100)
          validationError = null
        },
        label = { Text("Concepto o descripción") },
        placeholder = { Text("Ej. Pago cliente Juan / Servidor VPS") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("tx_title_input"),
      )

      OutlinedTextField(
        value = amountText,
        onValueChange = {
          amountText = it.replace(",", ".")
          validationError = null
        },
        label = { Text("Monto ($)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("tx_amount_input"),
      )

      Text("Categoría", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categoryOptions.forEach { option ->
          FilterChip(
            selected = category == option,
            onClick = { category = option },
            label = { Text(option) },
          )
        }
      }

      OutlinedTextField(
        value = Formatters.formatDate(dateMillis),
        onValueChange = {},
        readOnly = true,
        label = { Text("Fecha") },
        trailingIcon = {
          IconButton(onClick = { showDatePicker = true }) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = "Elegir fecha")
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it.take(300) },
        label = { Text("Notas (opcional)") },
        maxLines = 2,
        modifier = Modifier.fillMaxWidth(),
      )

      if (validationError != null) {
        Text(
          text = validationError!!,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
          Text("Cancelar")
        }
        Button(
          onClick = {
            val amount = amountText.toDoubleOrNull()
            when {
              title.isBlank() -> validationError = "Ingresá un concepto."
              amount == null || amount <= 0.0 -> validationError = "Ingresá un monto mayor a 0."
              else -> onSave(profile, txType, title.trim(), category, amount, dateMillis, notes.trim())
            }
          },
          modifier = Modifier.weight(1f).testTag("tx_save_button"),
        ) {
          Text("Guardar")
        }
      }
    }
  }

  if (showDatePicker) {
    LocalDatePickerModal(
      initialMillis = dateMillis,
      onDateSelected = {
        dateMillis = it
        showDatePicker = false
      },
      onDismiss = { showDatePicker = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnEditorSheet(
  existing: VpnAccount?,
  defaultProfile: ProfileType,
  savedPlans: List<ServicePlan> = emptyList(),
  onManagePlans: () -> Unit = {},
  onSave: (ProfileType, String, String, String, String, Double, Long, Boolean, Boolean, Long, String, String) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val profile = ProfileType.PREMIUM
  var clientName by remember { mutableStateOf(existing?.clientName ?: "") }
  var clientPhone by remember { mutableStateOf(existing?.clientPhone ?: "") }
  var vpnUsername by remember { mutableStateOf(existing?.vpnUsername ?: "") }
  var planName by remember { mutableStateOf(existing?.planName ?: "Plan Mensual 30 días") }
  var amountText by remember {
    mutableStateOf(
      if (existing != null && existing.amountToCharge > 0) {
        existing.amountToCharge.toBigDecimal().stripTrailingZeros().toPlainString()
      } else {
        ""
      }
    )
  }
  var expirationMillis by remember {
    mutableLongStateOf(
      existing?.expirationMillis
        ?: Formatters.addDaysLocalNoon(System.currentTimeMillis(), 30)
    )
  }
  var isPaid by remember { mutableStateOf(existing?.isPaid ?: false) }
  var notifyOneDayBefore by remember { mutableStateOf(existing?.notifyOneDayBefore ?: true) }
  var hasPaymentPromise by remember { mutableStateOf((existing?.paymentPromiseMillis ?: 0L) > 0L) }
  var promiseMillis by remember {
    mutableLongStateOf(
      existing?.paymentPromiseMillis?.takeIf { it > 0L }
        ?: Formatters.addDaysLocalNoon(System.currentTimeMillis(), 1)
    )
  }
  var promiseNote by remember { mutableStateOf(existing?.paymentPromiseNote ?: "") }
  var notes by remember { mutableStateOf(existing?.notes ?: "") }
  var showDatePicker by remember { mutableStateOf(false) }
  var showPromiseDatePicker by remember { mutableStateOf(false) }
  var validationError by remember { mutableStateOf<String?>(null) }

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
      Text(
        text = if (existing == null) "Nuevo cliente" else "Editar cliente",
        style = MaterialTheme.typography.headlineSmall,
      )

      OutlinedTextField(
        value = clientName,
        onValueChange = {
          clientName = it.take(80)
          validationError = null
        },
        label = { Text("Nombre del cliente") },
        placeholder = { Text("Ej. Sofía Ruiz") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("vpn_client_name_input"),
      )

      OutlinedTextField(
        value = clientPhone,
        onValueChange = { clientPhone = it.take(30) },
        label = { Text("WhatsApp / Teléfono (opcional)") },
        placeholder = { Text("Ej. +5491155551234") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("vpn_phone_input"),
      )

      OutlinedTextField(
        value = vpnUsername,
        onValueChange = {
          vpnUsername = it.take(60)
          validationError = null
        },
        label = { Text("Usuario o cuenta del cliente") },
        placeholder = { Text("Ej. sofia_01") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("vpn_username_input"),
      )

      val clientPlans =
        remember(savedPlans) { savedPlans.filter { it.targetType == ServicePlan.TARGET_CLIENT } }
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Planes guardados",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
          onClick = onManagePlans,
          modifier = Modifier.testTag("vpn_editor_manage_plans_button"),
        ) {
          Text(if (clientPlans.isEmpty()) "+ Crear planes" else "Gestionar")
        }
      }
      if (clientPlans.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          clientPlans.forEach { plan ->
            AssistChip(
              onClick = {
                planName = plan.name
                amountText =
                  plan.price.toBigDecimal().stripTrailingZeros().toPlainString()
                expirationMillis =
                  Formatters.addDaysLocalNoon(
                    System.currentTimeMillis(),
                    plan.durationDays.toInt().coerceIn(1, 365),
                  )
                validationError = null
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.LocalOffer,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              label = {
                Text(
                  "${plan.name} · ${Formatters.formatCurrency(plan.price)} (${plan.durationDays}d)"
                )
              },
              modifier = Modifier.testTag("vpn_plan_chip_${plan.id}"),
            )
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = amountText,
          onValueChange = {
            amountText = it.replace(",", ".")
            validationError = null
          },
          label = { Text("Monto a cobrar ($)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("vpn_amount_input"),
        )
        OutlinedTextField(
          value = planName,
          onValueChange = { planName = it.take(60) },
          label = { Text("Servicio / Plan") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      OutlinedTextField(
        value = Formatters.formatDate(expirationMillis),
        onValueChange = {},
        readOnly = true,
        label = { Text("Fecha de vencimiento") },
        trailingIcon = {
          IconButton(onClick = { showDatePicker = true }) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = "Elegir vencimiento")
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = false,
          onClick = { expirationMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 1) },
          label = { Text("Mañana") },
        )
        FilterChip(
          selected = false,
          onClick = { expirationMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 7) },
          label = { Text("+7 días") },
        )
        FilterChip(
          selected = false,
          onClick = { expirationMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 30) },
          label = { Text("+30 días") },
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Notificar 1 día antes", style = MaterialTheme.typography.bodyLarge)
          Text(
            "Recibirás un recordatorio automático el día previo al vencimiento",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = notifyOneDayBefore,
          onCheckedChange = { notifyOneDayBefore = it },
          modifier = Modifier.testTag("vpn_notify_switch"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Ciclo actual pagado", style = MaterialTheme.typography.bodyLarge)
        Switch(
          checked = isPaid,
          onCheckedChange = { checked ->
            isPaid = checked
            if (checked) hasPaymentPromise = false
          },
          modifier = Modifier.testTag("vpn_paid_switch"),
        )
      }

      if (!isPaid) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Promesa de pago", style = MaterialTheme.typography.bodyLarge)
            Text(
              "Te avisamos exactamente el día acordado para cobrar",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = hasPaymentPromise,
            onCheckedChange = { hasPaymentPromise = it },
            modifier = Modifier.testTag("vpn_promise_switch"),
          )
        }

        if (hasPaymentPromise) {
          OutlinedTextField(
            value = Formatters.formatDate(promiseMillis),
            onValueChange = {},
            readOnly = true,
            label = { Text("Fecha prometida") },
            trailingIcon = {
              IconButton(onClick = { showPromiseDatePicker = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Elegir fecha de promesa")
              }
            },
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = promiseNote,
            onValueChange = { promiseNote = it.take(160) },
            label = { Text("Nota de promesa (opcional)") },
            placeholder = { Text("Ej. Paga el 20 al mediodía") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it.take(300) },
        label = { Text("Notas (opcional)") },
        maxLines = 2,
        modifier = Modifier.fillMaxWidth(),
      )

      if (validationError != null) {
        Text(
          text = validationError!!,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
        )
      }

      Spacer(modifier = Modifier.height(2.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
          Text("Cancelar")
        }
        Button(
          onClick = {
            val amount = amountText.toDoubleOrNull()
            when {
              clientName.isBlank() -> validationError = "Ingresá el nombre del cliente."
              vpnUsername.isBlank() -> validationError = "Ingresá un usuario o cuenta."
              amount == null || amount < 0.0 -> validationError = "Ingresá un monto válido."
              else ->
                onSave(
                  profile,
                  clientName.trim(),
                  clientPhone.trim(),
                  vpnUsername.trim(),
                  planName.trim(),
                  amount,
                  expirationMillis,
                  isPaid,
                  notifyOneDayBefore,
                  if (!isPaid && hasPaymentPromise) promiseMillis else 0L,
                  if (!isPaid && hasPaymentPromise) promiseNote.trim() else "",
                  notes.trim(),
                )
            }
          },
          modifier = Modifier.weight(1f).testTag("vpn_save_button"),
        ) {
          Text("Guardar")
        }
      }
    }
  }

  if (showDatePicker) {
    LocalDatePickerModal(
      initialMillis = expirationMillis,
      onDateSelected = {
        expirationMillis = it
        showDatePicker = false
      },
      onDismiss = { showDatePicker = false },
    )
  }

  if (showPromiseDatePicker) {
    LocalDatePickerModal(
      initialMillis = promiseMillis,
      onDateSelected = {
        promiseMillis = it
        showPromiseDatePicker = false
      },
      onDismiss = { showPromiseDatePicker = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalDatePickerModal(
  initialMillis: Long,
  onDateSelected: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          val utcMillis = datePickerState.selectedDateMillis
          if (utcMillis != null) {
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
              timeInMillis = utcMillis
            }
            val localNoon =
              Formatters.localNoonMillis(
                year = utcCal.get(Calendar.YEAR),
                monthZeroBased = utcCal.get(Calendar.MONTH),
                dayOfMonth = utcCal.get(Calendar.DAY_OF_MONTH),
              )
            onDateSelected(localNoon)
          } else {
            onDismiss()
          }
        }
      ) {
        Text("Aceptar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  ) {
    DatePicker(state = datePickerState)
  }
}
