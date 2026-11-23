package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ProfileType(val id: String, val label: String, val shortLabel: String) {
  COMMON("common", "Servicios Standar", "Standar"),
  PREMIUM("premium", "Servicios Premium", "Premium");

  companion object {
    fun fromId(id: String?): ProfileType =
      entries.firstOrNull { it.id == id } ?: COMMON
  }
}

enum class TransactionType(val id: String, val label: String) {
  INCOME("income", "Ingreso"),
  EXPENSE("expense", "Gasto");

  companion object {
    fun fromId(id: String?): TransactionType =
      entries.firstOrNull { it.id == id } ?: INCOME
  }
}

@IgnoreExtraProperties
data class FinancialTransaction(
  @DocumentId val id: String = "",
  val userId: String = "",
  val profileType: String = ProfileType.COMMON.id,
  val type: String = TransactionType.INCOME.id,
  val title: String = "",
  val category: String = "",
  val amount: Double = 0.0,
  val monthKey: String = "",
  val dateMillis: Long = 0L,
  val linkedClientId: String = "",
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class VpnAccount(
  @DocumentId val id: String = "",
  val userId: String = "",
  val profileType: String = ProfileType.COMMON.id,
  val clientName: String = "",
  val clientPhone: String = "",
  val vpnUsername: String = "",
  val planName: String = "",
  val amountToCharge: Double = 0.0,
  val expirationMillis: Long = 0L,
  val isPaid: Boolean = false,
  val notifyOneDayBefore: Boolean = true,
  val paymentPromiseMillis: Long = 0L,
  val paymentPromiseNote: String = "",
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Reseller(
  @DocumentId val id: String = "",
  val userId: String = "",
  val profileType: String = ProfileType.COMMON.id,
  val name: String = "",
  val phone: String = "",
  val creditsCount: Long = 0L,
  val pricePerCredit: Double = 0.0,
  val totalAmount: Double = 0.0,
  val expirationMillis: Long = 0L,
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val computedTotal: Double
    get() = creditsCount.coerceAtLeast(0L) * pricePerCredit.coerceAtLeast(0.0)
}

@IgnoreExtraProperties
data class UserSettings(
  @DocumentId val id: String = PREFERENCES_DOC_ID,
  val userId: String = "",
  val businessName: String = DEFAULT_BUSINESS_NAME,
  val whatsappClientTemplate: String = DEFAULT_CLIENT_TEMPLATE,
  val whatsappVpnTemplate: String = DEFAULT_VPN_TEMPLATE,
  val defaultCountryCode: String = "",
  val reminderHour: Long = 9L,
  val defaultVpnDays: Long = 30L,
  val autoRecordIncomeOnPay: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  companion object {
    const val PREFERENCES_DOC_ID = "preferences"
    const val DEFAULT_BUSINESS_NAME = "MALVINAS PRO SERVICE"

    val DEFAULT_CLIENT_TEMPLATE =
      """
      👋 ¡Hola, {nombre}!

      💙 Te escribimos de {empresa} para recordarte que ya está disponible el vencimiento de tu servicio.

      📡 Tu servicio: {servicio}
      📅 Período: {periodo}
      💰 Importe a pagar: {monto}
      ⏰ Fecha de vencimiento: {vencimiento}

      ¡No te quedes sin conexión! 🚀

      ¡Gracias por confiar en nosotros! 💙

      {empresa}
      Siempre conectados con vos. 🌐
      """.trimIndent()

    val DEFAULT_VPN_TEMPLATE =
      """
      👋 ¡Hola, {nombre}!

      💙 Te escribimos de {empresa} para recordarte que ya está disponible el vencimiento de tu servicio.

      📡 Tu servicio: {servicio}
      📅 Período: {periodo}
      💰 Importe a pagar: {monto}
      ⏰ Fecha de vencimiento: {vencimiento}

      ¡No te quedes sin conexión! 🚀

      ¡Gracias por confiar en nosotros! 💙

      {empresa}
      Siempre conectados con vos. 🌐
      """.trimIndent()
  }
}

@IgnoreExtraProperties
data class RemoteAppConfig(
  val latestVersionCode: Long = 0L,
  val latestVersionName: String = "",
  val announcementText: String = "",
  val updateNotes: String = "",
  val recommendedClientTemplate: String = "",
  val recommendedVpnTemplate: String = "",
)

@IgnoreExtraProperties
data class AuthorizedUser(
  @DocumentId val id: String = "",
  val email: String = "",
  val displayName: String = "",
  val enabled: Boolean = true,
  val passwordHash: String = "",
  val passwordSalt: String = "",
  val passwordVersion: Long = 1L,
  val notes: String = "",
  val boundUid: String = "",
  val lastAccessAt: Long = 0L,
  val createdBy: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

