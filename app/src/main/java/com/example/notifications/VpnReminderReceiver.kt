package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VpnReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val vpnId = intent.getStringExtra(VpnNotificationManager.EXTRA_VPN_ID) ?: return
    val alertType =
      intent.getStringExtra(VpnNotificationManager.EXTRA_ALERT_TYPE)
        ?: if (intent.action == VpnNotificationManager.ACTION_PAYMENT_PROMISE) {
          VpnNotificationManager.ALERT_TYPE_PROMISE
        } else {
          VpnNotificationManager.ALERT_TYPE_EXPIRATION
        }
    val clientName = intent.getStringExtra(VpnNotificationManager.EXTRA_CLIENT_NAME) ?: "Cliente"
    val clientPhone = intent.getStringExtra(VpnNotificationManager.EXTRA_CLIENT_PHONE) ?: ""
    val vpnUsername = intent.getStringExtra(VpnNotificationManager.EXTRA_VPN_USERNAME) ?: ""
    val amountFormatted = intent.getStringExtra(VpnNotificationManager.EXTRA_AMOUNT_FORMATTED) ?: ""
    val countryCode = intent.getStringExtra(VpnNotificationManager.EXTRA_COUNTRY_CODE) ?: ""

    if (alertType == VpnNotificationManager.ALERT_TYPE_PROMISE) {
      val promiseDate = intent.getStringExtra(VpnNotificationManager.EXTRA_PROMISE_DATE) ?: ""
      val promiseMillis = intent.getLongExtra(VpnNotificationManager.EXTRA_PROMISE_MILLIS, 0L)
      val promiseNote = intent.getStringExtra(VpnNotificationManager.EXTRA_PROMISE_NOTE) ?: ""
      val whatsAppMessage =
        intent.getStringExtra(VpnNotificationManager.EXTRA_WHATSAPP_MESSAGE)
          ?: "Hola $clientName, te recordamos desde Malvinas Pro Service tu compromiso de pago ($promiseDate) por $amountFormatted de tu servicio (@$vpnUsername)."

      VpnNotificationManager.markPromiseNotified(context, vpnId, promiseMillis)
      VpnNotificationManager.showPaymentPromiseNotification(
        context = context,
        notificationId = VpnNotificationManager.promiseRequestCode(vpnId),
        clientName = clientName,
        clientPhone = clientPhone,
        vpnUsername = vpnUsername,
        amountFormatted = amountFormatted,
        promiseDate = promiseDate,
        promiseNote = promiseNote,
        whatsAppMessage = whatsAppMessage,
        defaultCountryCode = countryCode,
      )
    } else {
      val expirationDate = intent.getStringExtra(VpnNotificationManager.EXTRA_EXPIRATION_DATE) ?: ""
      val whatsAppMessage =
        intent.getStringExtra(VpnNotificationManager.EXTRA_WHATSAPP_MESSAGE)
          ?: "Hola $clientName, te recordamos desde Malvinas Pro Service que tu servicio (@$vpnUsername) vence el $expirationDate ($amountFormatted)."

      VpnNotificationManager.showNotification(
        context = context,
        notificationId = vpnId.hashCode(),
        clientName = clientName,
        clientPhone = clientPhone,
        vpnUsername = vpnUsername,
        amountFormatted = amountFormatted,
        expirationDate = expirationDate,
        whatsAppMessage = whatsAppMessage,
        defaultCountryCode = countryCode,
      )
    }
  }
}
