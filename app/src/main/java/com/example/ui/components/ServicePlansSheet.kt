package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ServicePlan
import com.example.util.Formatters

private val PLAN_DAYS_PRESETS = listOf(7L, 15L, 30L, 60L, 90L)

@Composable
fun ServicePlansManagerDialog(
  plans: List<ServicePlan>,
  initialTargetType: String = ServicePlan.TARGET_CLIENT,
  onSavePlan: (
    existing: ServicePlan?,
    name: String,
    price: Double,
    durationDays: Long,
    targetType: String,
  ) -> Unit,
  onDeletePlan: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedTab by remember { mutableStateOf(initialTargetType) }
  var editingPlan by remember { mutableStateOf<ServicePlan?>(null) }
  var showForm by remember { mutableStateOf(false) }

  var name by remember { mutableStateOf("") }
  var priceText by remember { mutableStateOf("") }
  var daysText by remember { mutableStateOf("30") }
  var formTarget by remember { mutableStateOf(initialTargetType) }
  var formError by remember { mutableStateOf<String?>(null) }

  val filteredPlans =
    remember(plans, selectedTab) {
      plans.filter { it.targetType == selectedTab }
    }

  fun openNewForm(target: String) {
    editingPlan = null
    name = ""
    priceText = ""
    daysText = "30"
    formTarget = target
    formError = null
    showForm = true
  }

  fun openEditForm(plan: ServicePlan) {
    editingPlan = plan
    name = plan.name
    priceText = if (plan.price > 0.0) plan.price.toString() else ""
    daysText = plan.durationDays.coerceIn(1L, 365L).toString()
    formTarget = plan.targetType
    formError = null
    showForm = true
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Card(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 20.dp)
          .widthIn(max = 540.dp)
          .testTag("service_plans_dialog"),
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
                imageVector = Icons.Filled.LocalOffer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column {
                Text(
                  text = "Planes y precios guardados",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Usalos para autocompletar clientes y revendedores en un toque",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("service_plans_close_button"),
            ) {
              Icon(Icons.Filled.Close, contentDescription = "Cerrar planes")
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = selectedTab == ServicePlan.TARGET_CLIENT,
              onClick = { selectedTab = ServicePlan.TARGET_CLIENT },
              label = {
                Text(
                  "Clientes (${plans.count { it.targetType == ServicePlan.TARGET_CLIENT }})"
                )
              },
              modifier = Modifier.testTag("plans_tab_client"),
            )
            FilterChip(
              selected = selectedTab == ServicePlan.TARGET_RESELLER,
              onClick = { selectedTab = ServicePlan.TARGET_RESELLER },
              label = {
                Text(
                  "Revendedores (${plans.count { it.targetType == ServicePlan.TARGET_RESELLER }})"
                )
              },
              modifier = Modifier.testTag("plans_tab_reseller"),
            )
          }

          if (showForm) {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text(
                  text = if (editingPlan == null) "Nuevo plan guardado" else "Editar plan",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  FilterChip(
                    selected = formTarget == ServicePlan.TARGET_CLIENT,
                    onClick = { formTarget = ServicePlan.TARGET_CLIENT },
                    label = { Text("Para clientes") },
                  )
                  FilterChip(
                    selected = formTarget == ServicePlan.TARGET_RESELLER,
                    onClick = { formTarget = ServicePlan.TARGET_RESELLER },
                    label = { Text("Para revendedores") },
                  )
                }

                OutlinedTextField(
                  value = name,
                  onValueChange = {
                    if (it.length <= 60) {
                      name = it
                      formError = null
                    }
                  },
                  label = { Text("Nombre del plan") },
                  placeholder = {
                    Text(
                      if (formTarget == ServicePlan.TARGET_RESELLER) {
                        "Ej: Crédito estándar"
                      } else {
                        "Ej: Mensual 1 dispositivo"
                      }
                    )
                  },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("plan_name_input"),
                )

                OutlinedTextField(
                  value = priceText,
                  onValueChange = {
                    priceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                    formError = null
                  },
                  label = {
                    Text(
                      if (formTarget == ServicePlan.TARGET_RESELLER) {
                        "Precio por crédito ($)"
                      } else {
                        "Precio del plan ($)"
                      }
                    )
                  },
                  placeholder = { Text("Ej: 6000") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("plan_price_input"),
                )

                if (formTarget == ServicePlan.TARGET_CLIENT) {
                  Text(
                    text = "Duración del plan (días)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    PLAN_DAYS_PRESETS.forEach { d ->
                      FilterChip(
                        selected = daysText.toLongOrNull() == d,
                        onClick = { daysText = d.toString() },
                        label = { Text("${d}d") },
                      )
                    }
                  }
                  OutlinedTextField(
                    value = daysText,
                    onValueChange = {
                      daysText = it.filter { ch -> ch.isDigit() }.take(3)
                      formError = null
                    },
                    label = { Text("Días personalizados (1–365)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("plan_days_input"),
                  )
                }

                if (formError != null) {
                  Text(
                    text = formError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedButton(
                    onClick = { showForm = false },
                    modifier = Modifier.weight(1f),
                  ) {
                    Text("Cancelar")
                  }
                  Button(
                    onClick = {
                      val cleanName = name.trim()
                      val parsedPrice = priceText.replace(',', '.').toDoubleOrNull()
                      val parsedDays =
                        if (formTarget == ServicePlan.TARGET_RESELLER) {
                          30L
                        } else {
                          daysText.toLongOrNull()?.coerceIn(1L, 365L) ?: 30L
                        }
                      when {
                        cleanName.isBlank() -> formError = "Ingresá el nombre del plan."
                        parsedPrice == null || parsedPrice < 0.0 ->
                          formError = "Ingresá un precio válido."
                        else -> {
                          onSavePlan(editingPlan, cleanName, parsedPrice, parsedDays, formTarget)
                          selectedTab = formTarget
                          showForm = false
                        }
                      }
                    },
                    modifier = Modifier.weight(1f).testTag("save_plan_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Save,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardar")
                  }
                }
              }
            }
          } else {
            Button(
              onClick = { openNewForm(selectedTab) },
              modifier =
                Modifier.fillMaxWidth()
                  .height(48.dp)
                  .testTag("add_service_plan_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                if (selectedTab == ServicePlan.TARGET_RESELLER) {
                  "Nuevo precio de revendedor"
                } else {
                  "Nuevo plan de cliente"
                }
              )
            }
          }

          HorizontalDivider()

          if (filteredPlans.isEmpty()) {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "Todavía no guardaste planes aquí",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text =
                    "Creá tus planes frecuentes (por ej. 30 días, 2 dispositivos o precio de crédito) para elegirlos rápido al cargar.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(filteredPlans, key = { it.id }) { plan ->
                Surface(
                  modifier = Modifier.fillMaxWidth().testTag("service_plan_row_${plan.id}"),
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = plan.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      val subtitle =
                        if (plan.targetType == ServicePlan.TARGET_RESELLER) {
                          "${Formatters.formatCurrency(plan.price)} por crédito"
                        } else {
                          "${Formatters.formatCurrency(plan.price)} · ${plan.durationDays} días"
                        }
                      Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                      )
                    }
                    IconButton(onClick = { openEditForm(plan) }) {
                      Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Editar plan ${plan.name}",
                        modifier = Modifier.size(18.dp),
                      )
                    }
                    IconButton(onClick = { onDeletePlan(plan.id) }) {
                      Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Eliminar plan ${plan.name}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                      )
                    }
                  }
                }
              }
            }
          }

          TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
          ) {
            Text("Listo")
          }
        }
      }
    }
  }
}
