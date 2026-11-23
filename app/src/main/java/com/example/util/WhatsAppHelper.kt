package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.Reseller
import com.example.data.model.UserSettings
import com.example.data.model.VpnAccount

object WhatsAppHelper {

  fun sanitizePhoneDigits(rawPhone: String, defaultCountryCode: String = ""): String {
    val trimmed = rawPhone.trim()
    if (trimmed.isBlank()) return ""
    val digits = trimmed.filter { it.isDigit() }
    if (digits.isBlank()) return ""
    val countryDigits = defaultCountryCode.filter { it.isDigit() }
    return if (
      countryDigits.isNotEmpty() &&
        !trimmed.startsWith("+") &&
        !digits.startsWith(countryDigits) &&
        digits.length <= 10
    ) {
      "$countryDigits$digits"
    } else {
      digits
    }
  }

  fun renderTemplate(template: String, variables: Map<String, String>): String {
    var rendered = template
    variables.forEach { (key, value) ->
      rendered = rendered.replace("{$key}", value)
    }
    return rendered
  }

  fun buildVpnReminderMessage(
    vpn: VpnAccount,
    settings: UserSettings = UserSettings(),
  ): String {
    val daysLeft = Formatters.daysUntilLocalDay(vpn.expirationMillis)
    val dayAndMonth = Formatters.formatDayAndMonthFromMillis(vpn.expirationMillis)
    val periodText =
      if (vpn.expirationMillis > 0L) {
        Formatters.formatMonthTitle(Formatters.monthKeyFromMillis(vpn.expirationMillis))
      } else {
        "Actual"
      }
    val amountText = Formatters.formatCurrency(vpn.amountToCharge)
    val dueDateText =
      when {
        vpn.expirationMillis <= 0L -> "Sin fecha"
        daysLeft < 0 -> "$dayAndMonth (vencido)"
        daysLeft == 0 -> "$dayAndMonth (vence hoy)"
        daysLeft == 1 -> "$dayAndMonth (vence mañana)"
        else -> dayAndMonth
      }
    val serviceLabel =
      when {
        vpn.planName.isNotBlank() && vpn.vpnUsername.isNotBlank() ->
          "${vpn.planName} (@${vpn.vpnUsername})"
        vpn.vpnUsername.isNotBlank() -> "@${vpn.vpnUsername}"
        vpn.planName.isNotBlank() -> vpn.planName
        else -> "Servicio contratado"
      }
    val businessName = settings.businessName.ifBlank { UserSettings.DEFAULT_BUSINESS_NAME }
    val template = settings.whatsappVpnTemplate.ifBlank { UserSettings.DEFAULT_VPN_TEMPLATE }
    return renderTemplate(
      template = template,
      variables =
        mapOf(
          "nombre" to vpn.clientName,
          "servicio" to serviceLabel,
          "usuario" to vpn.vpnUsername,
          "usuario_vpn" to vpn.vpnUsername,
          "periodo" to periodText,
          "monto" to amountText,
          "vencimiento" to dueDateText,
          "empresa" to businessName,
        ),
    )
  }

  fun buildResellerSummaryMessage(
    reseller: Reseller,
    settings: UserSettings = UserSettings(),
  ): String {
    val businessName = settings.businessName.ifBlank { UserSettings.DEFAULT_BUSINESS_NAME }
    val unitPrice = Formatters.formatCurrency(reseller.pricePerCredit)
    val total = Formatters.formatCurrency(reseller.computedTotal)
    val expirationLine =
      if (reseller.expirationMillis > 0L) {
        val daysLeft = Formatters.daysUntilLocalDay(reseller.expirationMillis)
        val dateText = Formatters.formatDayAndMonthFromMillis(reseller.expirationMillis)
        val statusSuffix =
          when {
            daysLeft < 0 -> " (vencido)"
            daysLeft == 0 -> " (vence hoy)"
            daysLeft == 1 -> " (vence mañana)"
            else -> ""
          }
        "\n⏰ Vencimiento (cada 30 días): $dateText$statusSuffix"
      } else {
        ""
      }
    return """
      👋 ¡Hola, ${reseller.name}!

      💙 Resumen de tus créditos en $businessName:

      🎟️ Créditos disponibles: ${reseller.creditsCount}
      💵 Valor por crédito: $unitPrice
      💰 Total: $total$expirationLine

      ¡Gracias por trabajar con nosotros! 🚀
    """.trimIndent()
  }

  fun buildWhatsAppIntent(
    rawPhone: String,
    message: String,
    defaultCountryCode: String = "",
  ): Intent {
    val digits = sanitizePhoneDigits(rawPhone, defaultCountryCode)
    val encodedMessage = Uri.encode(message)
    val url =
      if (digits.isNotEmpty()) {
        "https://wa.me/$digits?text=$encodedMessage"
      } else {
        "https://wa.me/?text=$encodedMessage"
      }
    return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun openWhatsApp(
    context: Context,
    rawPhone: String,
    message: String,
    defaultCountryCode: String = "",
  ): Boolean {
    val intent = buildWhatsAppIntent(rawPhone, message, defaultCountryCode)
    return try {
      context.startActivity(intent)
      true
    } catch (_: ActivityNotFoundException) {
      false
    }
  }
}
