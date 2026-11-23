package com.example.notifications

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VpnNotificationManagerTest {

  @Test
  fun computeOnDayTriggerMillis_triggersOnExactSameCalendarDayAtReminderHour() {
    val targetCal =
      Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 20, 15, 30, 0)
        set(Calendar.MILLISECOND, 0)
      }

    val triggerMillis =
      VpnNotificationManager.computeOnDayTriggerMillis(
        targetMillis = targetCal.timeInMillis,
        reminderHour = 9,
      )

    val triggerCal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
    assertEquals(2026, triggerCal.get(Calendar.YEAR))
    assertEquals(Calendar.NOVEMBER, triggerCal.get(Calendar.MONTH))
    assertEquals(20, triggerCal.get(Calendar.DAY_OF_MONTH))
    assertEquals(9, triggerCal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, triggerCal.get(Calendar.MINUTE))
  }

  @Test
  fun promiseRequestCode_differsFromExpirationRequestCode() {
    val vpnId = "vpn_user_123"
    assertNotEquals(vpnId.hashCode(), VpnNotificationManager.promiseRequestCode(vpnId))
  }
}
