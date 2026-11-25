package com.example.util

import com.example.data.model.FinancialTransaction
import com.example.data.model.Reseller
import com.example.data.model.ServicePlan
import com.example.data.model.UserSettings
import com.example.data.model.VpnAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudBackupHelperTest {

  @Test
  fun toJsonString_and_fromJsonString_roundTripsAllDataCleanly() {
    val payload =
      CloudBackupPayload(
        exportedAtMillis = 1_760_000_000_000L,
        sourceEmail = "axel@example.com",
        settings =
          UserSettings(
            businessName = "MALVINAS VIP",
            whatsappClientTemplate = "Hola {nombre}, abona {monto}",
            whatsappVpnTemplate = "VPN {usuario_vpn}: vence {vencimiento}",
            defaultCountryCode = "549",
            reminderHour = 10L,
            defaultVpnDays = 60L,
            autoRecordIncomeOnPay = false,
          ),
        transactions =
          listOf(
            FinancialTransaction(
              profileType = "premium",
              type = "income",
              title = "Pago mensual",
              category = "Servicios",
              amount = 15000.0,
              monthKey = "2026-11",
              dateMillis = 1_760_000_000_000L,
            )
          ),
        vpnAccounts =
          listOf(
            VpnAccount(
              profileType = "premium",
              clientName = "Carlos",
              clientPhone = "+5491155559999",
              vpnUsername = "carlos_vpn",
              planName = "VIP",
              amountToCharge = 9500.0,
              expirationMillis = 1_761_000_000_000L,
              isPaid = false,
              notifyOneDayBefore = true,
              paymentPromiseMillis = 1_760_500_000_000L,
              paymentPromiseNote = "Paga el 20 por transferencia",
            )
          ),
        resellers =
          listOf(
            Reseller(
              profileType = "common",
              name = "Revendedor Centro",
              phone = "+5491166667788",
              creditsCount = 30L,
              pricePerCredit = 1200.0,
              totalAmount = 36000.0,
              expirationMillis = 1_762_000_000_000L,
              notes = "Créditos mensuales",
            )
          ),
        servicePlans =
          listOf(
            ServicePlan(
              name = "VPN Full 30 días",
              price = 7500.0,
              durationDays = 30L,
              targetType = ServicePlan.TARGET_CLIENT,
            )
          ),
      )

    val json = CloudBackupHelper.toJsonString(payload)
    val parsedResult = CloudBackupHelper.fromJsonString(json)
    assertTrue(parsedResult.isSuccess)
    val restored = parsedResult.getOrThrow()

    assertEquals("axel@example.com", restored.sourceEmail)
    assertEquals("MALVINAS VIP", restored.settings.businessName)
    assertEquals("549", restored.settings.defaultCountryCode)
    assertEquals(10L, restored.settings.reminderHour)
    assertEquals(60L, restored.settings.defaultVpnDays)
    assertEquals(false, restored.settings.autoRecordIncomeOnPay)

    assertEquals(1, restored.transactions.size)
    assertEquals(15000.0, restored.transactions.first().amount, 0.01)

    assertEquals(1, restored.vpnAccounts.size)
    assertEquals("carlos_vpn", restored.vpnAccounts.first().vpnUsername)
    assertEquals(1_760_500_000_000L, restored.vpnAccounts.first().paymentPromiseMillis)
    assertEquals("Paga el 20 por transferencia", restored.vpnAccounts.first().paymentPromiseNote)

    assertEquals(1, restored.resellers.size)
    assertEquals("Revendedor Centro", restored.resellers.first().name)
    assertEquals(30L, restored.resellers.first().creditsCount)
    assertEquals(1200.0, restored.resellers.first().pricePerCredit, 0.01)
    assertEquals(36000.0, restored.resellers.first().computedTotal, 0.01)
    assertEquals(1_762_000_000_000L, restored.resellers.first().expirationMillis)

    assertEquals(1, restored.servicePlans.size)
    assertEquals("VPN Full 30 días", restored.servicePlans.first().name)
    assertEquals(7500.0, restored.servicePlans.first().price, 0.01)
    assertEquals(30L, restored.servicePlans.first().durationDays)
    assertEquals(ServicePlan.TARGET_CLIENT, restored.servicePlans.first().targetType)
  }

  @Test
  fun fromJsonString_rejectsInvalidBackupFiles() {
    assertTrue(CloudBackupHelper.fromJsonString("not json").isFailure)
    assertTrue(CloudBackupHelper.fromJsonString("{}").isFailure)
  }

  @Test
  fun suggestedBackupFileName_formatsIsoDate() {
    val filename = CloudBackupHelper.suggestedBackupFileName(1_760_000_000_000L)
    assertTrue(filename.startsWith("malvinas_pro_respaldo_"))
    assertTrue(filename.endsWith(".json"))
  }
}
