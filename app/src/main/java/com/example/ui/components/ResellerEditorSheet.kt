package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ProfileType
import com.example.data.model.Reseller
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResellerEditorSheet(
  existing: Reseller?,
  defaultProfile: ProfileType,
  onSave: (ProfileType, String, String, Long, Double, Long, String) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val profile = ProfileType.PREMIUM
  var name by remember { mutableStateOf(existing?.name ?: "") }
  var phone by remember { mutableStateOf(existing?.phone ?: "") }
  var creditsText by remember {
    mutableStateOf(
      if (existing != null) existing.creditsCount.toString() else "10"
    )
  }
  var priceText by remember {
    mutableStateOf(
      if (existing != null && existing.pricePerCredit > 0.0) {
        existing.pricePerCredit.toBigDecimal().stripTrailingZeros().toPlainString()
      } else {
        ""
      }
    )
  }
  var expirationMillis by remember {
    mutableLongStateOf(
      existing?.expirationMillis?.takeIf { it > 0L }
        ?: Formatters.addDaysLocalNoon(System.currentTimeMillis(), 30)
    )
  }
  var notes by remember { mutableStateOf(existing?.notes ?: "") }
  var showDatePicker by remember { mutableStateOf(false) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val parsedCredits = creditsText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
  val parsedPrice = priceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
  val liveTotal = parsedCredits * parsedPrice

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
        text = if (existing == null) "Nuevo revendedor" else "Editar revendedor",
        style = MaterialTheme.typography.headlineSmall,
      )

      OutlinedTextField(
        value = name,
        onValueChange = {
          name = it.take(80)
          validationError = null
        },
        label = { Text("Nombre del revendedor") },
        placeholder = { Text("Ej. Lucas Pérez") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("reseller_name_input"),
      )

      OutlinedTextField(
        value = phone,
        onValueChange = { phone = it.take(30) },
        label = { Text("WhatsApp / Teléfono (opcional)") },
        placeholder = { Text("Ej. +5491155551234") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("reseller_phone_input"),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = creditsText,
          onValueChange = { raw ->
            creditsText = raw.filter { it.isDigit() }.take(7)
            validationError = null
          },
          label = { Text("Créditos") },
          placeholder = { Text("0") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("reseller_credits_input"),
        )
        OutlinedTextField(
          value = priceText,
          onValueChange = { raw ->
            priceText = raw.replace(",", ".")
            validationError = null
          },
          label = { Text("Precio c/u ($)") },
          placeholder = { Text("1500") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("reseller_price_input"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(5L, 10L, 20L, 50L, 100L).forEach { addAmount ->
          FilterChip(
            selected = false,
            onClick = {
              val current = creditsText.toLongOrNull() ?: 0L
              creditsText = (current + addAmount).coerceAtMost(1_000_000L).toString()
            },
            label = { Text("+$addAmount créditos") },
          )
        }
      }

      OutlinedTextField(
        value = Formatters.formatDate(expirationMillis),
        onValueChange = {},
        readOnly = true,
        label = { Text("Vencimiento de créditos (cada 30 días)") },
        trailingIcon = {
          IconButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.testTag("reseller_pick_date_button"),
          ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = "Elegir vencimiento")
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = Formatters.daysUntilLocalDay(expirationMillis) == 15,
          onClick = { expirationMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 15) },
          label = { Text("+15 días") },
        )
        FilterChip(
          selected = Formatters.daysUntilLocalDay(expirationMillis) == 30,
          onClick = { expirationMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 30) },
          label = { Text("+30 días (ciclo)") },
        )
        FilterChip(
          selected = Formatters.daysUntilLocalDay(expirationMillis) == 60,
          onClick = { expirationMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 60) },
          label = { Text("+60 días") },
        )
        FilterChip(
          selected = false,
          onClick = { showDatePicker = true },
          label = { Text("Calendario") },
        )
      }

      Surface(
        modifier = Modifier.fillMaxWidth().testTag("reseller_live_total_card"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.Calculate,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Column {
              Text(
                text = "Total de sus créditos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                text =
                  "$parsedCredits × ${Formatters.formatCurrency(parsedPrice)} c/u · Vence ${Formatters.formatDate(expirationMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
          Text(
            text = Formatters.formatCurrency(liveTotal),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
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
            val credits = creditsText.toLongOrNull()
            val price = priceText.toDoubleOrNull()
            when {
              name.isBlank() -> validationError = "Ingresá el nombre del revendedor."
              credits == null || credits < 0L ->
                validationError = "Ingresá una cantidad válida de créditos."
              price == null || price < 0.0 ->
                validationError = "Ingresá cuánto sale cada crédito."
              else ->
                onSave(
                  profile,
                  name.trim(),
                  phone.trim(),
                  credits.coerceAtMost(1_000_000L),
                  price.coerceAtMost(1_000_000_000.0),
                  expirationMillis,
                  notes.trim(),
                )
            }
          },
          modifier = Modifier.weight(1f).testTag("reseller_save_button"),
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
}
