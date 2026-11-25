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
    val paymentBlock = buildPaymentDetailsBlock(settings)
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
          "datos_pago" to paymentBlock,
        ),
    ).replace(Regex("\n{3,}"), "\n\n").trim()
  }

  fun buildPaymentDetailsBlock(settings: UserSettings): String {
    val lines = buildList {
      if (settings.paymentBankOrWallet.isNotBlank()) {
        add("🏦 Medio: ${settings.paymentBankOrWallet.trim()}")
      }
      if (settings.paymentHolderName.isNotBlank()) {
        add("👤 Titular: ${settings.paymentHolderName.trim()}")
      }
      if (settings.paymentAlias.isNotBlank()) {
        add("🏷️ Alias: *${settings.paymentAlias.trim()}*")
      }
      if (settings.paymentCbuCvu.isNotBlank()) {
        add("🔢 CBU/CVU: ${settings.paymentCbuCvu.trim()}")
      }
      if (settings.paymentLink.isNotBlank()) {
        add("🔗 Link de pago: ${settings.paymentLink.trim()}")
      }
    }
    if (lines.isEmpty()) return ""
    return "💳 *Datos para transferir / pagar:*\n" + lines.joinToString("\n")
  }

  fun buildStandalonePaymentInfoMessage(
    settings: UserSettings,
    recipientName: String = "",
    amount: Double? = null,
    serviceDetail: String = "",
  ): String {
    val businessName = settings.businessName.ifBlank { UserSettings.DEFAULT_BUSINESS_NAME }
    val block = buildPaymentDetailsBlock(settings)
    if (block.isBlank()) return ""
    val greeting =
      if (recipientName.isNotBlank()) {
        "👋 ¡Hola, ${recipientName.trim()}!"
      } else {
        "👋 ¡Hola!"
      }
    val detailLines = buildList {
      if (serviceDetail.isNotBlank()) {
        add("📡 Servicio: ${serviceDetail.trim()}")
      }
      if (amount != null && amount > 0.0) {
        add("💰 Importe: *${Formatters.formatCurrency(amount)}*")
      }
    }
    val detailSection =
      if (detailLines.isNotEmpty()) {
        "\n\n" + detailLines.joinToString("\n")
      } else {
        ""
      }
    return """
      $greeting$detailSection

      $block

      📩 Cuando realices el pago, enviame el comprobante por acá.
      ¡Muchas gracias! 💙
      $businessName 🌐
    """.trimIndent()
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

  fun buildPaymentReceiptMessage(
    receipt: PaymentReceiptData,
    settings: UserSettings = UserSettings(),
  ): String {
    val businessName = settings.businessName.ifBlank { UserSettings.DEFAULT_BUSINESS_NAME }
    val amountText = Formatters.formatCurrency(receipt.amount)
    val paidDateText = Formatters.formatDate(receipt.paidAtMillis)
    val nextDueLine =
      if (receipt.nextExpirationMillis > 0L) {
        "\n📅 Próximo vencimiento: ${Formatters.formatDate(receipt.nextExpirationMillis)}"
      } else {
        ""
      }
    val detailLine =
      if (receipt.serviceDetail.isNotBlank()) {
        "\n📡 Detalle: ${receipt.serviceDetail}"
      } else {
        ""
      }
    return """
      ✅ *COMPROBANTE DE PAGO*
      🏢 $businessName
      🧾 N° ${receipt.receiptCode}

      👤 Cliente: ${receipt.recipientName}$detailLine
      💰 Monto abonado: *$amountText*
      🗓️ Fecha de pago: $paidDateText$nextDueLine
      📌 Estado: *PAGADO*

      ¡Muchas gracias por tu pago y confianza! 💙
      $businessName 🌐
    """.trimIndent()
  }

  fun generateReceiptCode(nowMillis: Long = System.currentTimeMillis()): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val month = cal.get(java.util.Calendar.MONTH) + 1
    val suffix = ((nowMillis / 100L) % 10_000L).toInt().toString().padStart(4, '0')
    return "MP-%02d%02d-%s".format(java.util.Locale.US, day, month, suffix)
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

data class PaymentReceiptData(
  val recipientName: String,
  val recipientPhone: String,
  val serviceDetail: String,
  val amount: Double,
  val paidAtMillis: Long = System.currentTimeMillis(),
  val nextExpirationMillis: Long = 0L,
  val receiptCode: String = WhatsAppHelper.generateReceiptCode(),
)
