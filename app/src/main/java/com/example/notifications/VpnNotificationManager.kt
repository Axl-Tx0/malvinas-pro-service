package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.UserSettings
import com.example.data.model.VpnAccount
import com.example.util.Formatters
import com.example.util.WhatsAppHelper
import java.util.Calendar

object VpnNotificationManager {
  const val CHANNEL_ID = "vpn_expiration_alerts"
  const val PROMISE_CHANNEL_ID = "vpn_payment_promises"

  const val ACTION_VPN_EXPIRATION = "com.example.ACTION_VPN_EXPIRATION"
  const val ACTION_PAYMENT_PROMISE = "com.example.ACTION_PAYMENT_PROMISE"

  const val EXTRA_VPN_ID = "extra_vpn_id"
  const val EXTRA_ALERT_TYPE = "extra_alert_type"
  const val ALERT_TYPE_EXPIRATION = "expiration"
  const val ALERT_TYPE_PROMISE = "promise"

  const val EXTRA_CLIENT_NAME = "extra_client_name"
  const val EXTRA_CLIENT_PHONE = "extra_client_phone"
  const val EXTRA_VPN_USERNAME = "extra_vpn_username"
  const val EXTRA_AMOUNT_FORMATTED = "extra_amount_formatted"
  const val EXTRA_EXPIRATION_DATE = "extra_expiration_date"
  const val EXTRA_PROMISE_DATE = "extra_promise_date"
  const val EXTRA_PROMISE_MILLIS = "extra_promise_millis"
  const val EXTRA_PROMISE_NOTE = "extra_promise_note"
  const val EXTRA_WHATSAPP_MESSAGE = "extra_whatsapp_message"
  const val EXTRA_COUNTRY_CODE = "extra_country_code"

  private const val PREFS_NAME = "vpn_reminder_prefs"

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            "Vencimientos de clientes",
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = "Recordatorios 1 día antes del vencimiento de tus clientes"
            enableVibration(true)
          }
        manager.createNotificationChannel(channel)
      }
      if (manager.getNotificationChannel(PROMISE_CHANNEL_ID) == null) {
        val promiseChannel =
          NotificationChannel(
            PROMISE_CHANNEL_ID,
            "Promesas de pago de clientes",
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = "Avisos en la fecha prometida de pago de clientes"
            enableVibration(true)
          }
        manager.createNotificationChannel(promiseChannel)
      }
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun syncReminders(
    context: Context,
    accounts: List<VpnAccount>,
    settings: UserSettings = UserSettings(),
  ) {
    ensureChannel(context)
    accounts.forEach { account ->
      if (account.id.isBlank()) return@forEach

      if (account.notifyOneDayBefore && !account.isPaid) {
        scheduleOneDayBefore(context, account, settings)
      } else {
        cancelReminder(context, account.id)
      }

      if (account.paymentPromiseMillis > 0L && !account.isPaid) {
        schedulePaymentPromise(context, account, settings)
      } else {
        cancelPaymentPromiseReminder(context, account.id)
      }
    }
  }

  fun scheduleOneDayBefore(
    context: Context,
    account: VpnAccount,
    settings: UserSettings = UserSettings(),
  ) {
    if (account.id.isBlank()) return
    ensureChannel(context)

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val triggerAtMillis =
      computeOneDayBeforeTriggerMillis(
        expirationMillis = account.expirationMillis,
        reminderHour = settings.reminderHour.toInt(),
      )
    val now = System.currentTimeMillis()

    val oneDayAfterExpiration = account.expirationMillis + 86_400_000L
    if (oneDayAfterExpiration < now) {
      cancelReminder(context, account.id)
      return
    }

    val pendingIntent = buildExpirationPendingIntent(context, account, settings)
    val effectiveTrigger = if (triggerAtMillis <= now) now + 5_000L else triggerAtMillis
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, effectiveTrigger, pendingIntent)
  }

  fun schedulePaymentPromise(
    context: Context,
    account: VpnAccount,
    settings: UserSettings = UserSettings(),
  ) {
    if (account.id.isBlank() || account.paymentPromiseMillis <= 0L || account.isPaid) {
      cancelPaymentPromiseReminder(context, account.id)
      return
    }
    ensureChannel(context)

    val now = System.currentTimeMillis()
    val daysUntilPromise = Formatters.daysUntilLocalDay(account.paymentPromiseMillis, now)
    if (daysUntilPromise < -1) {
      cancelPaymentPromiseReminder(context, account.id)
      return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val triggerAtMillis =
      computeOnDayTriggerMillis(
        targetMillis = account.paymentPromiseMillis,
        reminderHour = settings.reminderHour.toInt(),
      )

    if (triggerAtMillis <= now) {
      val lastNotifiedPromise = getLastNotifiedPromiseMillis(context, account.id)
      if (lastNotifiedPromise == account.paymentPromiseMillis) {
        return
      }
    }

    val pendingIntent = buildPromisePendingIntent(context, account, settings)
    val effectiveTrigger = if (triggerAtMillis <= now) now + 5_000L else triggerAtMillis
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, effectiveTrigger, pendingIntent)
  }

  fun cancelReminder(context: Context, vpnId: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent =
      Intent(context, VpnReminderReceiver::class.java).apply {
        action = ACTION_VPN_EXPIRATION
      }
    val pendingIntent =
      PendingIntent.getBroadcast(
        context,
        vpnId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    alarmManager.cancel(pendingIntent)
  }

  fun cancelPaymentPromiseReminder(context: Context, vpnId: String) {
    if (vpnId.isBlank()) return
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent =
      Intent(context, VpnReminderReceiver::class.java).apply {
        action = ACTION_PAYMENT_PROMISE
      }
    val pendingIntent =
      PendingIntent.getBroadcast(
        context,
        promiseRequestCode(vpnId),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    alarmManager.cancel(pendingIntent)
  }

  fun markPromiseNotified(context: Context, vpnId: String, promiseMillis: Long) {
    if (vpnId.isBlank() || promiseMillis <= 0L) return
    context
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putLong("promise_$vpnId", promiseMillis)
      .apply()
  }

  private fun getLastNotifiedPromiseMillis(context: Context, vpnId: String): Long =
    context
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getLong("promise_$vpnId", 0L)

  fun showNotification(
    context: Context,
    notificationId: Int,
    clientName: String,
    clientPhone: String,
    vpnUsername: String,
    amountFormatted: String,
    expirationDate: String,
    whatsAppMessage: String,
    defaultCountryCode: String = "",
  ) {
    ensureChannel(context)
    if (!hasNotificationPermission(context)) return

    val tapIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        context,
        notificationId,
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val whatsAppIntent =
      WhatsAppHelper.buildWhatsAppIntent(clientPhone, whatsAppMessage, defaultCountryCode)
    val whatsAppPendingIntent =
      PendingIntent.getActivity(
        context,
        notificationId + 100_000,
        whatsAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val builder =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Cliente vence mañana: $clientName")
        .setContentText("Cuenta @$vpnUsername vence el $expirationDate · Cobrar $amountFormatted")
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText(
              "El servicio de $clientName (@$vpnUsername) vence el $expirationDate (en 1 día). " +
                "Monto a cobrar: $amountFormatted. Tocá 'Avisar por WhatsApp' para enviarle el recordatorio."
            )
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(contentPendingIntent)
        .addAction(
          android.R.drawable.ic_menu_send,
          "Avisar por WhatsApp",
          whatsAppPendingIntent,
        )

    NotificationManagerCompat.from(context).notify(notificationId, builder.build())
  }

  fun showPaymentPromiseNotification(
    context: Context,
    notificationId: Int,
    clientName: String,
    clientPhone: String,
    vpnUsername: String,
    amountFormatted: String,
    promiseDate: String,
    promiseNote: String,
    whatsAppMessage: String,
    defaultCountryCode: String = "",
  ) {
    ensureChannel(context)
    if (!hasNotificationPermission(context)) return

    val tapIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        context,
        notificationId,
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val whatsAppIntent =
      WhatsAppHelper.buildWhatsAppIntent(clientPhone, whatsAppMessage, defaultCountryCode)
    val whatsAppPendingIntent =
      PendingIntent.getActivity(
        context,
        notificationId + 200_000,
        whatsAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val noteSuffix = if (promiseNote.isNotBlank()) " Nota: \"$promiseNote\"." else ""
    val builder =
      NotificationCompat.Builder(context, PROMISE_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Promesa de pago hoy: $clientName")
        .setContentText("Prometió pagar $amountFormatted hoy ($promiseDate) · @$vpnUsername")
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText(
              "$clientName (@$vpnUsername) tiene promesa de pago para hoy ($promiseDate) por $amountFormatted.$noteSuffix " +
                "Tocá 'Avisar por WhatsApp' o abrí la app para registrar el cobro."
            )
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(contentPendingIntent)
        .addAction(
          android.R.drawable.ic_menu_send,
          "Avisar por WhatsApp",
          whatsAppPendingIntent,
        )

    NotificationManagerCompat.from(context).notify(notificationId, builder.build())
  }

  fun sendTestNotification(
    context: Context,
    settings: UserSettings = UserSettings(),
  ) {
    val tomorrowMillis = Formatters.addDaysLocalNoon(System.currentTimeMillis(), 1)
    val dateStr = Formatters.formatDate(tomorrowMillis)
    val amountStr = Formatters.formatCurrency(10000.0)
    val demoAccount =
      VpnAccount(
        clientName = "Prueba Malvinas Pro",
        vpnUsername = "cliente_demo",
        planName = "Internet Ilimitado",
        amountToCharge = 10000.0,
        expirationMillis = tomorrowMillis,
      )
    showNotification(
      context = context,
      notificationId = 9901,
      clientName = demoAccount.clientName,
      clientPhone = "",
      vpnUsername = demoAccount.vpnUsername,
      amountFormatted = amountStr,
      expirationDate = dateStr,
      whatsAppMessage = WhatsAppHelper.buildVpnReminderMessage(demoAccount, settings),
      defaultCountryCode = settings.defaultCountryCode,
    )
  }

  private fun buildExpirationPendingIntent(
    context: Context,
    account: VpnAccount,
    settings: UserSettings,
  ): PendingIntent {
    val dateText = Formatters.formatDate(account.expirationMillis)
    val amountText = Formatters.formatCurrency(account.amountToCharge)
    val whatsAppMsg = WhatsAppHelper.buildVpnReminderMessage(account, settings)

    val intent =
      Intent(context, VpnReminderReceiver::class.java).apply {
        action = ACTION_VPN_EXPIRATION
        putExtra(EXTRA_ALERT_TYPE, ALERT_TYPE_EXPIRATION)
        putExtra(EXTRA_VPN_ID, account.id)
        putExtra(EXTRA_CLIENT_NAME, account.clientName)
        putExtra(EXTRA_CLIENT_PHONE, account.clientPhone)
        putExtra(EXTRA_VPN_USERNAME, account.vpnUsername)
        putExtra(EXTRA_AMOUNT_FORMATTED, amountText)
        putExtra(EXTRA_EXPIRATION_DATE, dateText)
        putExtra(EXTRA_WHATSAPP_MESSAGE, whatsAppMsg)
        putExtra(EXTRA_COUNTRY_CODE, settings.defaultCountryCode)
      }
    return PendingIntent.getBroadcast(
      context,
      account.id.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun buildPromisePendingIntent(
    context: Context,
    account: VpnAccount,
    settings: UserSettings,
  ): PendingIntent {
    val promiseDateText = Formatters.formatDate(account.paymentPromiseMillis)
    val amountText = Formatters.formatCurrency(account.amountToCharge)
    val whatsAppMsg = WhatsAppHelper.buildVpnReminderMessage(account, settings)

    val intent =
      Intent(context, VpnReminderReceiver::class.java).apply {
        action = ACTION_PAYMENT_PROMISE
        putExtra(EXTRA_ALERT_TYPE, ALERT_TYPE_PROMISE)
        putExtra(EXTRA_VPN_ID, account.id)
        putExtra(EXTRA_CLIENT_NAME, account.clientName)
        putExtra(EXTRA_CLIENT_PHONE, account.clientPhone)
        putExtra(EXTRA_VPN_USERNAME, account.vpnUsername)
        putExtra(EXTRA_AMOUNT_FORMATTED, amountText)
        putExtra(EXTRA_PROMISE_DATE, promiseDateText)
        putExtra(EXTRA_PROMISE_MILLIS, account.paymentPromiseMillis)
        putExtra(EXTRA_PROMISE_NOTE, account.paymentPromiseNote)
        putExtra(EXTRA_WHATSAPP_MESSAGE, whatsAppMsg)
        putExtra(EXTRA_COUNTRY_CODE, settings.defaultCountryCode)
      }
    return PendingIntent.getBroadcast(
      context,
      promiseRequestCode(account.id),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  internal fun promiseRequestCode(vpnId: String): Int = ("promise:$vpnId").hashCode()

  internal fun computeOneDayBeforeTriggerMillis(
    expirationMillis: Long,
    reminderHour: Int = 9,
  ): Long {
    val clampedHour = reminderHour.coerceIn(0, 23)
    val cal =
      Calendar.getInstance().apply {
        timeInMillis = expirationMillis
        add(Calendar.DAY_OF_YEAR, -1)
        set(Calendar.HOUR_OF_DAY, clampedHour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
    return cal.timeInMillis
  }

  internal fun computeOnDayTriggerMillis(
    targetMillis: Long,
    reminderHour: Int = 9,
  ): Long {
    val clampedHour = reminderHour.coerceIn(0, 23)
    val cal =
      Calendar.getInstance().apply {
        timeInMillis = targetMillis
        set(Calendar.HOUR_OF_DAY, clampedHour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
    return cal.timeInMillis
  }
}
