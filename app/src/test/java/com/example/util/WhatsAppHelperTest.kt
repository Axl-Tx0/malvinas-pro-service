package com.example.util

import com.example.data.model.Reseller
import com.example.data.model.UserSettings
import com.example.data.model.VpnAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppHelperTest {

  @Test
  fun sanitizePhoneDigits_stripsFormatting() {
    assertEquals("5491155551234", WhatsAppHelper.sanitizePhoneDigits("+54 9 11 5555-1234"))
    assertEquals("", WhatsAppHelper.sanitizePhoneDigits("   "))
  }

  @Test
  fun sanitizePhoneDigits_prependsDefaultCountryCodeWhenLocal() {
    assertEquals(
      "5491155551234",
      WhatsAppHelper.sanitizePhoneDigits("11 5555-1234", defaultCountryCode = "549"),
    )
    assertEquals(
      "5491155551234",
      WhatsAppHelper.sanitizePhoneDigits("5491155551234", defaultCountryCode = "549"),
    )
    assertEquals(
      "5215512345678",
      WhatsAppHelper.sanitizePhoneDigits("+52 1 55 1234-5678", defaultCountryCode = "549"),
    )
  }

  @Test
  fun buildVpnReminderMessage_includesClientAndAmount() {
    val tomorrow = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 1)
    val vpn =
      VpnAccount(
        clientName = "Carlos",
        vpnUsername = "carlos_vpn",
        planName = "Internet Ilimitado",
        amountToCharge = 8500.0,
        expirationMillis = tomorrow,
      )
    val message = WhatsAppHelper.buildVpnReminderMessage(vpn)
    assertTrue(message.contains("¡Hola, Carlos!"))
    assertTrue(message.contains("MALVINAS PRO SERVICE"))
    assertTrue(message.contains("Internet Ilimitado (@carlos_vpn)"))
    assertTrue(message.contains("(vence mañana)"))
    assertTrue(message.contains("Siempre conectados con vos."))
  }

  @Test
  fun buildVpnReminderMessage_usesCustomTemplateWithVpnUsernameAndBusiness() {
    val tomorrow = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 1)
    val vpn =
      VpnAccount(
        clientName = "Mateo",
        vpnUsername = "mateo_pro",
        planName = "VIP",
        amountToCharge = 12000.0,
        expirationMillis = tomorrow,
      )
    val settings =
      UserSettings(
        businessName = "Mi Red Pro",
        whatsappVpnTemplate = "Hola {nombre} (@{usuario_vpn}) de {empresa}: abona {monto} el {vencimiento}.",
      )
    val message = WhatsAppHelper.buildVpnReminderMessage(vpn, settings)
    assertTrue(message.startsWith("Hola Mateo (@mateo_pro) de Mi Red Pro: abona $12.000"))
    assertTrue(message.contains("(vence mañana)"))
  }

  @Test
  fun buildResellerSummaryMessage_includesCreditsPriceAndTotal() {
    val tomorrow = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 1)
    val reseller =
      Reseller(
        name = "Lucas",
        creditsCount = 25L,
        pricePerCredit = 1500.0,
        expirationMillis = tomorrow,
      )
    val message = WhatsAppHelper.buildResellerSummaryMessage(reseller, UserSettings(businessName = "MALVINAS PRO"))
    assertTrue(message.contains("Lucas"))
    assertTrue(message.contains("25"))
    assertTrue(message.contains("$1.500"))
    assertTrue(message.contains("$37.500"))
    assertTrue(message.contains("Vencimiento (cada 30 días)"))
    assertTrue(message.contains("vence mañana"))
    assertTrue(message.contains("MALVINAS PRO"))
  }
}
