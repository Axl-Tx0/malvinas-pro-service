package com.example.util

import com.example.data.model.FinancialTransaction
import com.example.data.model.ProfileType
import com.example.data.model.Reseller
import com.example.data.model.ServicePlan
import com.example.data.model.TransactionType
import com.example.data.model.UserSettings
import com.example.data.model.VpnAccount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class CloudBackupPayload(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val exportedAtMillis: Long = System.currentTimeMillis(),
  val sourceEmail: String = "",
  val settings: UserSettings = UserSettings(),
  val transactions: List<FinancialTransaction> = emptyList(),
  val vpnAccounts: List<VpnAccount> = emptyList(),
  val resellers: List<Reseller> = emptyList(),
  val servicePlans: List<ServicePlan> = emptyList(),
) {
  companion object {
    const val CURRENT_SCHEMA_VERSION = 1
  }
}

object CloudBackupHelper {

  fun suggestedBackupFileName(nowMillis: Long = System.currentTimeMillis()): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
    return "malvinas_pro_respaldo_$stamp.json"
  }

  fun toJsonString(payload: CloudBackupPayload): String {
    val root = JSONObject()
    root.put("schemaVersion", payload.schemaVersion)
    root.put("exportedAtMillis", payload.exportedAtMillis)
    root.put("sourceEmail", payload.sourceEmail)

    val settingsObj = JSONObject()
    settingsObj.put("businessName", payload.settings.businessName)
    settingsObj.put("whatsappClientTemplate", payload.settings.whatsappClientTemplate)
    settingsObj.put("whatsappVpnTemplate", payload.settings.whatsappVpnTemplate)
    settingsObj.put("defaultCountryCode", payload.settings.defaultCountryCode)
    settingsObj.put("reminderHour", payload.settings.reminderHour)
    settingsObj.put("defaultVpnDays", payload.settings.defaultVpnDays)
    settingsObj.put("autoRecordIncomeOnPay", payload.settings.autoRecordIncomeOnPay)
    settingsObj.put("paymentAlias", payload.settings.paymentAlias)
    settingsObj.put("paymentCbuCvu", payload.settings.paymentCbuCvu)
    settingsObj.put("paymentHolderName", payload.settings.paymentHolderName)
    settingsObj.put("paymentBankOrWallet", payload.settings.paymentBankOrWallet)
    settingsObj.put("paymentLink", payload.settings.paymentLink)
    root.put("settings", settingsObj)

    val txArray = JSONArray()
    payload.transactions.forEach { tx ->
      txArray.put(
        JSONObject().apply {
          put("profileType", tx.profileType)
          put("type", tx.type)
          put("title", tx.title)
          put("category", tx.category)
          put("amount", tx.amount)
          put("monthKey", tx.monthKey)
          put("dateMillis", tx.dateMillis)
          put("linkedClientId", tx.linkedClientId)
          put("notes", tx.notes)
        }
      )
    }
    root.put("transactions", txArray)

    val vpnArray = JSONArray()
    payload.vpnAccounts.forEach { vpn ->
      vpnArray.put(
        JSONObject().apply {
          put("profileType", vpn.profileType)
          put("clientName", vpn.clientName)
          put("clientPhone", vpn.clientPhone)
          put("vpnUsername", vpn.vpnUsername)
          put("planName", vpn.planName)
          put("amountToCharge", vpn.amountToCharge)
          put("expirationMillis", vpn.expirationMillis)
          put("isPaid", vpn.isPaid)
          put("notifyOneDayBefore", vpn.notifyOneDayBefore)
          put("paymentPromiseMillis", vpn.paymentPromiseMillis)
          put("paymentPromiseNote", vpn.paymentPromiseNote)
          put("notes", vpn.notes)
        }
      )
    }
    root.put("vpnAccounts", vpnArray)

    val resellersArray = JSONArray()
    payload.resellers.forEach { reseller ->
      resellersArray.put(
        JSONObject().apply {
          put("profileType", reseller.profileType)
          put("name", reseller.name)
          put("phone", reseller.phone)
          put("creditsCount", reseller.creditsCount)
          put("pricePerCredit", reseller.pricePerCredit)
          put("totalAmount", reseller.computedTotal)
          put("expirationMillis", reseller.expirationMillis)
          put("notes", reseller.notes)
        }
      )
    }
    root.put("resellers", resellersArray)

    val plansArray = JSONArray()
    payload.servicePlans.forEach { plan ->
      plansArray.put(
        JSONObject().apply {
          put("name", plan.name)
          put("price", plan.price)
          put("durationDays", plan.durationDays)
          put("targetType", plan.targetType)
        }
      )
    }
    root.put("servicePlans", plansArray)

    return root.toString(2)
  }

  fun fromJsonString(rawJson: String): Result<CloudBackupPayload> {
    return try {
      val root = JSONObject(rawJson.trim())
      val schemaVersion = root.optInt("schemaVersion", 0)
      if (schemaVersion <= 0) {
        return Result.failure(IllegalArgumentException("El archivo no corresponde a un respaldo válido de Malvinas Pro Service."))
      }

      val settingsObj = root.optJSONObject("settings")
      val settings =
        if (settingsObj != null) {
          UserSettings(
            businessName =
              settingsObj.optString("businessName", UserSettings.DEFAULT_BUSINESS_NAME)
                .ifBlank { UserSettings.DEFAULT_BUSINESS_NAME }
                .take(80),
            whatsappClientTemplate =
              settingsObj.optString("whatsappClientTemplate", UserSettings.DEFAULT_CLIENT_TEMPLATE)
                .ifBlank { UserSettings.DEFAULT_CLIENT_TEMPLATE }
                .take(1500),
            whatsappVpnTemplate =
              settingsObj.optString("whatsappVpnTemplate", UserSettings.DEFAULT_VPN_TEMPLATE)
                .ifBlank { UserSettings.DEFAULT_VPN_TEMPLATE }
                .take(1500),
            defaultCountryCode =
              settingsObj.optString("defaultCountryCode", "").filter { it.isDigit() }.take(10),
            reminderHour = settingsObj.optLong("reminderHour", 9L).coerceIn(0L, 23L),
            defaultVpnDays = settingsObj.optLong("defaultVpnDays", 30L).coerceIn(1L, 365L),
            autoRecordIncomeOnPay = settingsObj.optBoolean("autoRecordIncomeOnPay", true),
            paymentAlias = settingsObj.optString("paymentAlias", "").trim().take(80),
            paymentCbuCvu = settingsObj.optString("paymentCbuCvu", "").trim().take(40),
            paymentHolderName = settingsObj.optString("paymentHolderName", "").trim().take(80),
            paymentBankOrWallet = settingsObj.optString("paymentBankOrWallet", "").trim().take(60),
            paymentLink =
              settingsObj.optString("paymentLink", "").trim().take(250).let { link ->
                if (
                  link.startsWith("https://", ignoreCase = true) ||
                    link.startsWith("http://", ignoreCase = true)
                ) {
                  link
                } else {
                  ""
                }
              },
          )
        } else {
          UserSettings()
        }

      val transactions = mutableListOf<FinancialTransaction>()
      val txArray = root.optJSONArray("transactions") ?: JSONArray()
      for (i in 0 until txArray.length()) {
        val obj = txArray.optJSONObject(i) ?: continue
        val title = obj.optString("title", "").trim().take(100)
        val amount = obj.optDouble("amount", 0.0)
        val monthKey = obj.optString("monthKey", "")
        if (title.isBlank() || amount <= 0.0 || !MONTH_KEY_REGEX.matches(monthKey)) continue
        transactions.add(
          FinancialTransaction(
            profileType = ProfileType.fromId(obj.optString("profileType", "common")).id,
            type = TransactionType.fromId(obj.optString("type", "income")).id,
            title = title,
            category = obj.optString("category", "").trim().take(50),
            amount = amount.coerceAtMost(1_000_000_000.0),
            monthKey = monthKey,
            dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()).coerceAtLeast(0L),
            linkedClientId = obj.optString("linkedClientId", "").take(128),
            notes = obj.optString("notes", "").trim().take(300),
          )
        )
      }

      val vpnAccounts = mutableListOf<VpnAccount>()
      val vpnArray = root.optJSONArray("vpnAccounts") ?: JSONArray()
      for (i in 0 until vpnArray.length()) {
        val obj = vpnArray.optJSONObject(i) ?: continue
        val clientName = obj.optString("clientName", "").trim().take(80)
        val vpnUsername = obj.optString("vpnUsername", "").trim().take(60)
        if (clientName.isBlank() || vpnUsername.isBlank()) continue
        vpnAccounts.add(
          VpnAccount(
            profileType = ProfileType.fromId(obj.optString("profileType", "common")).id,
            clientName = clientName,
            clientPhone = obj.optString("clientPhone", "").trim().take(30),
            vpnUsername = vpnUsername,
            planName = obj.optString("planName", "").trim().take(60),
            amountToCharge = obj.optDouble("amountToCharge", 0.0).coerceIn(0.0, 1_000_000_000.0),
            expirationMillis = obj.optLong("expirationMillis", 0L).coerceAtLeast(0L),
            isPaid = obj.optBoolean("isPaid", false),
            notifyOneDayBefore = obj.optBoolean("notifyOneDayBefore", true),
            paymentPromiseMillis = obj.optLong("paymentPromiseMillis", 0L).coerceAtLeast(0L),
            paymentPromiseNote = obj.optString("paymentPromiseNote", "").trim().take(160),
            notes = obj.optString("notes", "").trim().take(300),
          )
        )
      }

      val resellers = mutableListOf<Reseller>()
      val resellersArray = root.optJSONArray("resellers") ?: JSONArray()
      for (i in 0 until resellersArray.length()) {
        val obj = resellersArray.optJSONObject(i) ?: continue
        val name = obj.optString("name", "").trim().take(80)
        if (name.isBlank()) continue
        val credits = obj.optLong("creditsCount", 0L).coerceIn(0L, 1_000_000L)
        val price = obj.optDouble("pricePerCredit", 0.0).coerceIn(0.0, 1_000_000_000.0)
        resellers.add(
          Reseller(
            profileType = ProfileType.fromId(obj.optString("profileType", "common")).id,
            name = name,
            phone = obj.optString("phone", "").trim().take(30),
            creditsCount = credits,
            pricePerCredit = price,
            totalAmount = credits * price,
            expirationMillis = obj.optLong("expirationMillis", 0L).coerceAtLeast(0L),
            notes = obj.optString("notes", "").trim().take(300),
          )
        )
      }

      val servicePlans = mutableListOf<ServicePlan>()
      val plansArray = root.optJSONArray("servicePlans") ?: JSONArray()
      for (i in 0 until plansArray.length()) {
        val obj = plansArray.optJSONObject(i) ?: continue
        val name = obj.optString("name", "").trim().take(60)
        if (name.isBlank()) continue
        val targetType =
          if (obj.optString("targetType", ServicePlan.TARGET_CLIENT) == ServicePlan.TARGET_RESELLER) {
            ServicePlan.TARGET_RESELLER
          } else {
            ServicePlan.TARGET_CLIENT
          }
        servicePlans.add(
          ServicePlan(
            name = name,
            price = obj.optDouble("price", 0.0).coerceIn(0.0, 1_000_000_000.0),
            durationDays = obj.optLong("durationDays", 30L).coerceIn(1L, 365L),
            targetType = targetType,
          )
        )
      }

      Result.success(
        CloudBackupPayload(
          schemaVersion = schemaVersion,
          exportedAtMillis = root.optLong("exportedAtMillis", System.currentTimeMillis()),
          sourceEmail = root.optString("sourceEmail", ""),
          settings = settings,
          transactions = transactions,
          vpnAccounts = vpnAccounts,
          resellers = resellers,
          servicePlans = servicePlans,
        )
      )
    } catch (e: Exception) {
      Result.failure(
        IllegalArgumentException(e.localizedMessage ?: "No se pudo leer el archivo de respaldo.")
      )
    }
  }

  private val MONTH_KEY_REGEX = Regex("^[0-9]{4}-[0-9]{2}$")
}
