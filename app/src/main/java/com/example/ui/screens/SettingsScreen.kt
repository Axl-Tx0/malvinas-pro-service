package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ServicePlan
import com.example.data.model.UserSettings
import com.example.data.model.VpnAccount
import com.example.ui.auth.AppLockSettingsCard
import com.example.ui.components.AppUpdateSectionCard
import com.example.ui.components.CloudSyncAndBackupCard
import com.example.ui.viewmodel.CloudSyncUiState
import com.example.ui.viewmodel.DashboardData
import com.example.util.Formatters
import com.example.util.GitHubUpdateUiState
import com.example.util.InstalledAppVersionInfo
import com.example.util.WhatsAppHelper

private val VPN_VARIABLES =
  listOf(
    "{nombre}",
    "{servicio}",
    "{usuario}",
    "{periodo}",
    "{monto}",
    "{vencimiento}",
    "{datos_pago}",
    "{empresa}",
  )

private val WALLET_PRESETS =
  listOf(
    "Mercado Pago",
    "Banco Nación",
    "Ualá",
    "Brubank",
    "Cuenta DNI",
    "Personal Pay",
  )

private val REMINDER_HOUR_PRESETS = listOf(8, 9, 12, 18, 20)
private val VPN_DAYS_PRESETS = listOf(7, 15, 30, 60, 90)

@Composable
fun SettingsScreen(
  data: DashboardData,
  userEmail: String?,
  userName: String?,
  hasNotificationPermission: Boolean,
  cloudSyncState: CloudSyncUiState,
  versionInfo: InstalledAppVersionInfo,
  githubRepo: String,
  githubUpdateState: GitHubUpdateUiState,
  isSavingGitHubRepo: Boolean,
  onSyncCloudNow: () -> Unit,
  onExportCloudBackup: () -> Unit,
  onImportCloudBackup: () -> Unit,
  onSaveGitHubRepo: (String) -> Unit,
  onCheckGitHubUpdates: () -> Unit,
  onOpenGitHubDownload: (String) -> Unit,
  onOpenGitHubReleases: () -> Unit,
  onSaveSettings: (UserSettings) -> Unit,
  onTestWhatsAppMessage: (String, String) -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onSendTestNotification: (UserSettings) -> Unit,
  onSignOut: () -> Unit,
  isAdmin: Boolean = false,
  onOpenUserAccessManager: () -> Unit = {},
  onOpenServicePlansManager: () -> Unit = {},
  onNotifyMessage: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val saved = data.settings
  var businessName by remember(saved) { mutableStateOf(saved.businessName) }
  var defaultCountryCode by remember(saved) { mutableStateOf(saved.defaultCountryCode) }
  var vpnTemplate by remember(saved) { mutableStateOf(saved.whatsappVpnTemplate) }
  var reminderHour by remember(saved) { mutableIntStateOf(saved.reminderHour.toInt().coerceIn(0, 23)) }
  var defaultVpnDaysText by remember(saved) { mutableStateOf(saved.defaultVpnDays.coerceIn(1L, 365L).toString()) }
  var autoRecordIncome by remember(saved) { mutableStateOf(saved.autoRecordIncomeOnPay) }
  var paymentBankOrWallet by remember(saved) { mutableStateOf(saved.paymentBankOrWallet) }
  var paymentHolderName by remember(saved) { mutableStateOf(saved.paymentHolderName) }
  var paymentAlias by remember(saved) { mutableStateOf(saved.paymentAlias) }
  var paymentCbuCvu by remember(saved) { mutableStateOf(saved.paymentCbuCvu) }
  var paymentLink by remember(saved) { mutableStateOf(saved.paymentLink) }

  val parsedVpnDays = defaultVpnDaysText.toIntOrNull()?.coerceIn(1, 365) ?: 30
  val preservedClientTemplate = saved.whatsappClientTemplate.ifBlank { UserSettings.DEFAULT_CLIENT_TEMPLATE }
  val draftSettings =
    UserSettings(
      id = saved.id,
      userId = saved.userId,
      businessName = businessName,
      whatsappClientTemplate = preservedClientTemplate,
      whatsappVpnTemplate = vpnTemplate,
      defaultCountryCode = defaultCountryCode.filter { it.isDigit() },
      reminderHour = reminderHour.toLong(),
      defaultVpnDays = parsedVpnDays.toLong(),
      autoRecordIncomeOnPay = autoRecordIncome,
      paymentAlias = paymentAlias,
      paymentCbuCvu = paymentCbuCvu,
      paymentHolderName = paymentHolderName,
      paymentBankOrWallet = paymentBankOrWallet,
      paymentLink = paymentLink,
    )

  val hasUnsavedChanges =
    draftSettings.businessName.trim() != saved.businessName.trim() ||
      draftSettings.whatsappVpnTemplate.trim() != saved.whatsappVpnTemplate.trim() ||
      draftSettings.defaultCountryCode != saved.defaultCountryCode ||
      draftSettings.reminderHour != saved.reminderHour ||
      draftSettings.defaultVpnDays != saved.defaultVpnDays ||
      draftSettings.autoRecordIncomeOnPay != saved.autoRecordIncomeOnPay ||
      draftSettings.paymentAlias.trim() != saved.paymentAlias.trim() ||
      draftSettings.paymentCbuCvu.trim() != saved.paymentCbuCvu.trim() ||
      draftSettings.paymentHolderName.trim() != saved.paymentHolderName.trim() ||
      draftSettings.paymentBankOrWallet.trim() != saved.paymentBankOrWallet.trim() ||
      draftSettings.paymentLink.trim() != saved.paymentLink.trim()

  val sampleVpn =
    remember {
      VpnAccount(
        clientName = "Carlos",
        vpnUsername = "carlos_01",
        planName = "Internet Ilimitado",
        amountToCharge = 8500.0,
        expirationMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 1),
      )
    }

  val vpnPreview =
    remember(draftSettings) {
      WhatsAppHelper.buildVpnReminderMessage(sampleVpn, draftSettings)
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (isAdmin) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Usuarios y accesos (Admin)",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    "Creá usuarios con correo y contraseña para compartir la app y deshabilitalos al instante cuando quieras.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Button(
              onClick = onOpenUserAccessManager,
              modifier = Modifier.fillMaxWidth().testTag("settings_open_user_access_admin_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.AdminPanelSettings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Gestionar usuarios y accesos")
            }
          }
        }
      }
    }

    item {
      CloudSyncAndBackupCard(
        userEmail = userEmail,
        userName = userName,
        cloudSyncState = cloudSyncState,
        transactionsCount = data.allTransactions.size,
        vpnAccountsCount = data.allVpnAccounts.size,
        resellersCount = data.allResellers.size,
        onSyncNow = onSyncCloudNow,
        onExportBackup = onExportCloudBackup,
        onImportBackup = onImportCloudBackup,
        onSignOut = onSignOut,
        isAdmin = isAdmin,
      )
    }

    item {
      val clientPlansCount = data.servicePlans.count { it.targetType == ServicePlan.TARGET_CLIENT }
      val resellerPlansCount = data.servicePlans.count { it.targetType == ServicePlan.TARGET_RESELLER }
      SettingsSectionCard(
        icon = Icons.Filled.LocalOffer,
        title = "Planes y precios guardados",
        subtitle =
          "$clientPlansCount plan(es) de clientes · $resellerPlansCount precio(s) de revendedores sincronizados en la nube.",
      ) {
        OutlinedButton(
          onClick = onOpenServicePlansManager,
          modifier = Modifier.fillMaxWidth().testTag("settings_open_service_plans_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.LocalOffer,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Gestionar planes y precios")
        }
      }
    }

    item {
      AppLockSettingsCard(onNotify = onNotifyMessage)
    }

    item {
      SettingsSectionCard(
        icon = Icons.Filled.Business,
        title = "Negocio y prefijo WhatsApp",
        subtitle = "Personalizá la firma {empresa} y el código de país para teléfonos locales.",
      ) {
        OutlinedTextField(
          value = businessName,
          onValueChange = { if (it.length <= 80) businessName = it },
          label = { Text("Nombre de empresa / firma") },
          placeholder = { Text(UserSettings.DEFAULT_BUSINESS_NAME) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_business_name_input"),
        )
        OutlinedTextField(
          value = defaultCountryCode,
          onValueChange = { input ->
            defaultCountryCode = input.filter { it.isDigit() }.take(6)
          },
          label = { Text("Código de país por defecto (opcional)") },
          placeholder = { Text("Ej: 549 (Argentina), 52 (México), 57 (Colombia)") },
          supportingText = {
            Text("Si un teléfono no empieza con '+', se antepone este prefijo al abrir WhatsApp.")
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_country_code_input"),
        )
      }
    }

    item {
      val paymentPreview =
        remember(draftSettings) {
          WhatsAppHelper.buildStandalonePaymentInfoMessage(settings = draftSettings)
        }
      SettingsSectionCard(
        icon = Icons.Filled.AccountBalance,
        title = "Datos de cobro (Alias / CBU / CVU / MP)",
        subtitle =
          "Guardá tus datos de transferencia para enviarlos en un toque o usarlos con {datos_pago} en tus recordatorios.",
      ) {
        Text(
          text = "Billetera o banco rápido:",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          WALLET_PRESETS.forEach { preset ->
            FilterChip(
              selected = paymentBankOrWallet.equals(preset, ignoreCase = true),
              onClick = { paymentBankOrWallet = preset },
              label = { Text(preset) },
            )
          }
        }
        OutlinedTextField(
          value = paymentBankOrWallet,
          onValueChange = { if (it.length <= 60) paymentBankOrWallet = it },
          label = { Text("Billetera o banco") },
          placeholder = { Text("Ej: Mercado Pago, Banco Nación, Ualá") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_payment_bank_input"),
        )
        OutlinedTextField(
          value = paymentHolderName,
          onValueChange = { if (it.length <= 80) paymentHolderName = it },
          label = { Text("Titular de la cuenta") },
          placeholder = { Text("Ej: Nombre y Apellido") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_payment_holder_input"),
        )
        OutlinedTextField(
          value = paymentAlias,
          onValueChange = { if (it.length <= 80) paymentAlias = it },
          label = { Text("Alias") },
          placeholder = { Text("Ej: malvinas.pro.mp") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_payment_alias_input"),
        )
        OutlinedTextField(
          value = paymentCbuCvu,
          onValueChange = { if (it.length <= 40) paymentCbuCvu = it },
          label = { Text("CBU / CVU") },
          placeholder = { Text("Ej: 00000031000...") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_payment_cbu_input"),
        )
        OutlinedTextField(
          value = paymentLink,
          onValueChange = { if (it.length <= 250) paymentLink = it },
          label = { Text("Link de pago (opcional)") },
          placeholder = { Text("Ej: https://link.mercadopago.com.ar/...") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_payment_link_input"),
        )

        if (paymentPreview.isNotBlank()) {
          Text(
            text = "Vista previa de datos de cobro",
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
              text = paymentPreview,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(12.dp),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { onTestWhatsAppMessage(paymentPreview, draftSettings.defaultCountryCode) },
              modifier = Modifier.weight(1f).testTag("settings_test_payment_whatsapp_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Probar en WhatsApp", maxLines = 1)
            }
            Button(
              onClick = { onSaveSettings(draftSettings) },
              modifier = Modifier.weight(1f).testTag("settings_save_payment_info_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Guardar datos", maxLines = 1)
            }
          }
        } else {
          Button(
            onClick = { onSaveSettings(draftSettings) },
            modifier = Modifier.fillMaxWidth().testTag("settings_save_payment_info_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Save,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Guardar datos de cobro")
          }
        }
      }
    }

    item {
      WhatsAppTemplateSectionCard(
        icon = Icons.Filled.People,
        title = "Mensaje WhatsApp — Clientes",
        subtitle = "Se envía desde la pestaña Clientes y desde la notificación 1 día antes.",
        template = vpnTemplate,
        onTemplateChange = { if (it.length <= 1500) vpnTemplate = it },
        variables = VPN_VARIABLES,
        previewText = vpnPreview,
        inputTestTag = "settings_vpn_template_input",
        resetTestTag = "reset_vpn_template_button",
        testWhatsAppTestTag = "test_vpn_whatsapp_button",
        onResetTemplate = { vpnTemplate = UserSettings.DEFAULT_VPN_TEMPLATE },
        onTestWhatsApp = {
          onTestWhatsAppMessage(vpnPreview, draftSettings.defaultCountryCode)
        },
      )
    }

    item {
      SettingsSectionCard(
        icon = Icons.Filled.Schedule,
        title = "Alertas de clientes y automatización",
        subtitle = "Ajustá a qué hora recibir tus recordatorios y las opciones de cobro.",
      ) {
        Text(
          text = "Hora de notificación (1 día antes del vencimiento)",
          style = MaterialTheme.typography.titleSmall,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          IconButton(
            onClick = { reminderHour = if (reminderHour <= 0) 23 else reminderHour - 1 },
            modifier = Modifier.testTag("reminder_hour_minus_button"),
          ) {
            Icon(Icons.Filled.Remove, contentDescription = "Disminuir hora de aviso")
          }
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = "${reminderHour.toString().padStart(2, '0')}:00 hs",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
          }
          IconButton(
            onClick = { reminderHour = if (reminderHour >= 23) 0 else reminderHour + 1 },
            modifier = Modifier.testTag("reminder_hour_plus_button"),
          ) {
            Icon(Icons.Filled.Add, contentDescription = "Aumentar hora de aviso")
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          REMINDER_HOUR_PRESETS.forEach { preset ->
            FilterChip(
              selected = reminderHour == preset,
              onClick = { reminderHour = preset },
              label = { Text("${preset.toString().padStart(2, '0')}:00") },
            )
          }
        }

        HorizontalDivider()

        Text(
          text = "Días al usar 'Cobrar +Xd' en Clientes",
          style = MaterialTheme.typography.titleSmall,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          VPN_DAYS_PRESETS.forEach { days ->
            FilterChip(
              selected = parsedVpnDays == days,
              onClick = { defaultVpnDaysText = days.toString() },
              label = { Text("${days}d") },
            )
          }
        }
        OutlinedTextField(
          value = defaultVpnDaysText,
          onValueChange = { input ->
            defaultVpnDaysText = input.filter { it.isDigit() }.take(3)
          },
          label = { Text("Días de renovación personalizada (1–365)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_vpn_days_input"),
        )

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
              text = "Anotar ingreso automáticamente",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text = "Registra el ingreso en Finanzas automáticamente al renovar o cobrar un cliente.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = autoRecordIncome,
            onCheckedChange = { autoRecordIncome = it },
            modifier = Modifier.testTag("settings_auto_income_switch"),
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.NotificationsActive,
            contentDescription = null,
            tint =
              if (hasNotificationPermission) {
                MaterialTheme.colorScheme.tertiary
              } else {
                MaterialTheme.colorScheme.error
              },
          )
          Text(
            text =
              if (hasNotificationPermission) {
                "Permiso de notificaciones activo"
              } else {
                "Notificaciones bloqueadas en este dispositivo"
              },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
          )
        }
        if (!hasNotificationPermission) {
          Button(
            onClick = onRequestNotificationPermission,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Permitir notificaciones")
          }
        } else {
          OutlinedButton(
            onClick = { onSendTestNotification(draftSettings) },
            modifier = Modifier.fillMaxWidth().testTag("settings_test_notification_button"),
          ) {
            Text("Probar notificación con tu mensaje actual")
          }
        }
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
          onClick = { onSaveSettings(draftSettings) },
          modifier = Modifier.fillMaxWidth().testTag("save_settings_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Save,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            if (hasUnsavedChanges) {
              "Guardar configuración"
            } else {
              "Configuración guardada (Guardar nuevamente)"
            }
          )
        }
        TextButton(
          onClick = {
            businessName = UserSettings.DEFAULT_BUSINESS_NAME
            defaultCountryCode = ""
            vpnTemplate = UserSettings.DEFAULT_VPN_TEMPLATE
            reminderHour = 9
            defaultVpnDaysText = "30"
            autoRecordIncome = true
          },
          modifier = Modifier.fillMaxWidth().testTag("reset_all_settings_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.RestartAlt,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Restablecer valores de fábrica")
        }
      }
    }

    item {
      AppUpdateSectionCard(
        versionInfo = versionInfo,
        githubRepo = githubRepo,
        githubUpdateState = githubUpdateState,
        isAdmin = isAdmin,
        isSavingRepo = isSavingGitHubRepo,
        onSaveGitHubRepo = onSaveGitHubRepo,
        onCheckGitHubUpdates = onCheckGitHubUpdates,
        onOpenGitHubDownload = onOpenGitHubDownload,
        onOpenGitHubReleases = onOpenGitHubReleases,
      )
    }
  }
}

@Composable
private fun SettingsSectionCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  content: @Composable () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      content()
    }
  }
}

@Composable
private fun WhatsAppTemplateSectionCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  template: String,
  onTemplateChange: (String) -> Unit,
  variables: List<String>,
  previewText: String,
  inputTestTag: String,
  resetTestTag: String,
  testWhatsAppTestTag: String,
  onResetTemplate: () -> Unit,
  onTestWhatsApp: () -> Unit,
) {
  SettingsSectionCard(
    icon = icon,
    title = title,
    subtitle = subtitle,
  ) {
    Text(
      text = "Tocá una variable para insertarla al final del mensaje:",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      variables.forEach { token ->
        AssistChip(
          onClick = {
            val separator =
              if (template.isEmpty() || template.endsWith(" ") || template.endsWith("\n")) {
                ""
              } else {
                " "
              }
            onTemplateChange("$template$separator$token")
          },
          label = { Text(token) },
        )
      }
    }

    OutlinedTextField(
      value = template,
      onValueChange = onTemplateChange,
      label = { Text("Texto del mensaje (${template.length}/1500)") },
      minLines = 5,
      maxLines = 12,
      modifier = Modifier.fillMaxWidth().testTag(inputTestTag),
    )

    Text(
      text = "Vista previa",
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
        text = previewText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(12.dp),
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(
        onClick = onResetTemplate,
        modifier = Modifier.testTag(resetTestTag),
      ) {
        Icon(
          imageVector = Icons.Filled.RestartAlt,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Restablecer")
      }

      OutlinedButton(
        onClick = onTestWhatsApp,
        modifier = Modifier.testTag(testWhatsAppTestTag),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Send,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Probar en WhatsApp")
      }
    }
  }
}
