package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Formatters {
  private val localeEs = Locale("es", "AR")
  private val monthNames =
    listOf(
      "Enero",
      "Febrero",
      "Marzo",
      "Abril",
      "Mayo",
      "Junio",
      "Julio",
      "Agosto",
      "Septiembre",
      "Octubre",
      "Noviembre",
      "Diciembre",
    )

  private val moneyFormat =
    DecimalFormat("#,##0.##", DecimalFormatSymbols(localeEs)).apply {
      isDecimalSeparatorAlwaysShown = false
    }

  private val dateFormat = SimpleDateFormat("dd/MM/yyyy", localeEs)

  fun formatCurrency(amount: Double): String {
    val sign = if (amount < 0) "-" else ""
    return "$sign$${moneyFormat.format(abs(amount))}"
  }

  fun formatDate(millis: Long): String {
    if (millis <= 0L) return "Sin fecha"
    return dateFormat.format(Date(millis))
  }

  fun currentMonthKey(nowMillis: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    return String.format(Locale.US, "%04d-%02d", year, month)
  }

  fun monthKeyFromMillis(millis: Long): String = currentMonthKey(millis)

  fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
    val parts = monthKey.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
    val cal =
      Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, deltaMonths)
      }
    return String.format(
      Locale.US,
      "%04d-%02d",
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH) + 1,
    )
  }

  fun formatMonthTitle(monthKey: String): String {
    val parts = monthKey.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return monthKey
    val monthIndex = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
    val monthName = monthNames.getOrElse(monthIndex) { "Mes" }
    return "$monthName $year"
  }

  fun formatDayAndMonth(monthKey: String, dayOfMonth: Long): String {
    val parts = monthKey.split("-")
    val monthIndex = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
    val monthName = monthNames.getOrElse(monthIndex) { "mes" }.lowercase(localeEs)
    return "$dayOfMonth de $monthName"
  }

  fun formatDayAndMonthFromMillis(millis: Long): String {
    if (millis <= 0L) return "Sin fecha"
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val monthName = monthNames.getOrElse(cal.get(Calendar.MONTH)) { "mes" }.lowercase(localeEs)
    return "$day de $monthName"
  }

  fun formatShortMonthLabel(monthKey: String): String {
    val parts = monthKey.split("-")
    val monthIndex = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
    val monthName = monthNames.getOrElse(monthIndex) { "Mes" }
    return monthName.take(3)
  }

  fun formatCompactCurrency(amount: Double): String {
    val absVal = abs(amount)
    val sign = if (amount < 0) "-" else ""
    return when {
      absVal >= 1_000_000.0 -> {
        val m = absVal / 1_000_000.0
        val formatted =
          if (m % 1.0 == 0.0) m.toInt().toString() else String.format(localeEs, "%.1f", m)
        "$sign$${formatted}M"
      }
      absVal >= 1_000.0 -> {
        val k = absVal / 1_000.0
        val formatted =
          if (k % 1.0 == 0.0 || k >= 100.0) k.toInt().toString()
          else String.format(localeEs, "%.1f", k)
        "$sign$${formatted}k"
      }
      else -> "$sign$${absVal.toInt()}"
    }
  }

  fun recentMonthKeys(endMonthKey: String, count: Int): List<String> {
    if (count <= 0) return emptyList()
    return (-(count - 1)..0).map { offset -> shiftMonthKey(endMonthKey, offset) }
  }

  fun localNoonMillis(year: Int, monthZeroBased: Int, dayOfMonth: Int): Long {
    val cal =
      Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, monthZeroBased)
        set(Calendar.DAY_OF_MONTH, dayOfMonth)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
    return cal.timeInMillis
  }

  fun addDaysLocalNoon(baseMillis: Long, days: Int): Long {
    val cal =
      Calendar.getInstance().apply {
        timeInMillis = baseMillis
        add(Calendar.DAY_OF_YEAR, days)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
    return cal.timeInMillis
  }

  fun daysUntilLocalDay(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int {
    val startOfToday = startOfLocalDay(nowMillis)
    val startOfTarget = startOfLocalDay(targetMillis)
    val diffMillis = startOfTarget - startOfToday
    return (diffMillis / 86_400_000L).toInt()
  }

  private fun startOfLocalDay(millis: Long): Long {
    val cal =
      Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
    return cal.timeInMillis
  }
}
