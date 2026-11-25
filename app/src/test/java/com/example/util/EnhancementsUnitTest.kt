package com.example.util

import com.example.data.model.FinancialTransaction
import com.example.data.model.TransactionType
import com.example.data.model.UserSettings
import com.example.ui.viewmodel.MonthlyChartPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancementsUnitTest {

  @Test
  fun escapeCsvCell_neutralizesSpreadsheetFormulaPrefixes() {
    assertEquals("\"'=SUM(A1:A5)\"", FinancialReportExporter.escapeCsvCell("=SUM(A1:A5)"))
    assertEquals("\"'+12345\"", FinancialReportExporter.escapeCsvCell("+12345"))
    assertEquals("\"'-cmd|'/C calc'!A0\"", FinancialReportExporter.escapeCsvCell("-cmd|'/C calc'!A0"))
    assertEquals("\"'@HYPERLINK\"", FinancialReportExporter.escapeCsvCell("@HYPERLINK"))
    assertEquals("\"'\tattack\"", FinancialReportExporter.escapeCsvCell("\tattack"))
  }

  @Test
  fun escapeCsvCell_escapesQuotesAndFlattensNewlines() {
    assertEquals("\"Plan \"\"Pro\"\" Mensual\"", FinancialReportExporter.escapeCsvCell("Plan \"Pro\"\nMensual"))
    assertEquals("\"Cliente Normal\"", FinancialReportExporter.escapeCsvCell("Cliente Normal"))
  }

  @Test
  fun buildCsvReport_includesUtf8BomSummaryBreakdownAndSignedRows() {
    val txIncome =
      FinancialTransaction(
        id = "tx1",
        type = TransactionType.INCOME.id,
        title = "Cobro VPN Juan",
        category = "VPN",
        amount = 10000.0,
        dateMillis = 1763942400000L,
        monthKey = "2026-11",
        notes = "=formula_note",
      )
    val txExpense =
      FinancialTransaction(
        id = "tx2",
        type = TransactionType.EXPENSE.id,
        title = "Servidor VPS",
        category = "Infraestructura",
        amount = 3500.0,
        dateMillis = 1763856000000L,
        monthKey = "2026-11",
        notes = "Pago mensual",
      )
    val csv =
      FinancialReportExporter.buildCsvReport(
        FinancialExportRequest(
          businessName = "MALVINAS PRO SERVICE",
          periodTitle = "Noviembre 2026",
          fileSlug = "2026-11",
          transactions = listOf(txIncome, txExpense),
          monthlyBreakdown =
            listOf(
              MonthlyChartPoint(
                monthKey = "2026-11",
                shortLabel = "Nov",
                fullLabel = "Noviembre 2026",
                totalIncome = 10000.0,
                totalExpenses = 3500.0,
                netBalance = 6500.0,
              )
            ),
        )
      )

    assertTrue(csv.startsWith("\uFEFF"))
    assertTrue(csv.contains("\"Ingresos totales\";10000.00"))
    assertTrue(csv.contains("\"Gastos totales\";3500.00"))
    assertTrue(csv.contains("\"Balance neto\";6500.00"))
    assertTrue(csv.contains("\"Noviembre 2026\";10000.00;3500.00;6500.00"))
    assertTrue(csv.contains("\"Cobro VPN Juan\";\"VPN\";10000.00;\"'=formula_note\""))
    assertTrue(csv.contains("\"Servidor VPS\";\"Infraestructura\";-3500.00;\"Pago mensual\""))
  }

  @Test
  fun paymentReceipt_generatesValidCodeAndFormattedWhatsAppMessage() {
    val code = WhatsAppHelper.generateReceiptCode(1763942400000L)
    assertTrue(code.startsWith("MP-"))
    assertEquals(12, code.length)

    val receipt =
      PaymentReceiptData(
        recipientName = "Carlos Gomez",
        recipientPhone = "+5491122334455",
        serviceDetail = "Plan VPN 30 días (@carlos)",
        amount = 8500.0,
        paidAtMillis = 1763942400000L,
        nextExpirationMillis = 1766534400000L,
        receiptCode = code,
      )
    val message =
      WhatsAppHelper.buildPaymentReceiptMessage(
        receipt = receipt,
        settings = UserSettings(businessName = "MALVINAS PRO SERVICE"),
      )

    assertTrue(message.contains("COMPROBANTE DE PAGO"))
    assertTrue(message.contains("MALVINAS PRO SERVICE"))
    assertTrue(message.contains(code))
    assertTrue(message.contains("Carlos Gomez"))
    assertTrue(message.contains("Plan VPN 30 días (@carlos)"))
    assertTrue(message.contains("PAGADO"))
    assertTrue(message.contains("Próximo vencimiento:"))
  }

  @Test
  fun appLockPinValidation_enforcesFourToSixDigits() {
    assertTrue(AppLockStore.isValidAppPin("1234"))
    assertTrue(AppLockStore.isValidAppPin("12345"))
    assertTrue(AppLockStore.isValidAppPin("000000"))

    assertFalse(AppLockStore.isValidAppPin(""))
    assertFalse(AppLockStore.isValidAppPin("123"))
    assertFalse(AppLockStore.isValidAppPin("1234567"))
    assertFalse(AppLockStore.isValidAppPin("12a4"))
    assertFalse(AppLockStore.isValidAppPin("12 4"))
  }

  @Test
  fun paymentDetails_rendersBlockStandaloneMessageAndVpnTemplateVariable() {
    val settings =
      UserSettings(
        businessName = "MALVINAS PRO SERVICE",
        whatsappVpnTemplate = "Hola {nombre}, monto {monto}\n\n{datos_pago}",
        paymentBankOrWallet = "Mercado Pago",
        paymentHolderName = "Axel Del Valle",
        paymentAlias = "malvinas.pro.mp",
        paymentCbuCvu = "0000003100012345678901",
        paymentLink = "https://link.mercadopago.com.ar/malvinaspro",
      )
    assertTrue(settings.hasPaymentInfo)

    val block = WhatsAppHelper.buildPaymentDetailsBlock(settings)
    assertTrue(block.contains("Mercado Pago"))
    assertTrue(block.contains("Axel Del Valle"))
    assertTrue(block.contains("malvinas.pro.mp"))
    assertTrue(block.contains("0000003100012345678901"))
    assertTrue(block.contains("https://link.mercadopago.com.ar/malvinaspro"))

    val standalone =
      WhatsAppHelper.buildStandalonePaymentInfoMessage(
        settings = settings,
        recipientName = "Carlos",
        amount = 8500.0,
        serviceDetail = "Plan VPN 30 días",
      )
    assertTrue(standalone.contains("Carlos"))
    assertTrue(standalone.contains("Plan VPN 30 días"))
    assertTrue(standalone.contains("malvinas.pro.mp"))
    assertTrue(standalone.contains("MALVINAS PRO SERVICE"))

    val vpnReminder =
      WhatsAppHelper.buildVpnReminderMessage(
        vpn =
          com.example.data.model.VpnAccount(
            clientName = "Carlos",
            vpnUsername = "carlos_vpn",
            amountToCharge = 8500.0,
            expirationMillis = 1763942400000L,
          ),
        settings = settings,
      )
    assertTrue(vpnReminder.contains("Carlos"))
    assertTrue(vpnReminder.contains("malvinas.pro.mp"))
    assertFalse(vpnReminder.contains("{datos_pago}"))

    val emptySettings = UserSettings()
    assertFalse(emptySettings.hasPaymentInfo)
    assertEquals("", WhatsAppHelper.buildPaymentDetailsBlock(emptySettings))
    assertEquals("", WhatsAppHelper.buildStandalonePaymentInfoMessage(emptySettings))
  }
}
