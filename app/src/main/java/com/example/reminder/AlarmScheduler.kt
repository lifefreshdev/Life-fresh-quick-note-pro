package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    /**
     * Schedules an alarm with safe exact alarm check for Android 12+ (API 31) and Android 14+ (API 34).
     * Prevents SecurityException by checking canScheduleExactAlarms() and falling back to inexact alarm.
     */
    fun scheduleExactOrFallback(
        context: Context,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Fallback to inexact alarm to prevent SecurityException crash on Android 14+
                Log.w(TAG, "Exact alarm permission not granted. Falling back to setAndAllowWhileIdle.")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                return
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.d(TAG, "Exact alarm scheduled successfully at $triggerAtMillis")
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException while scheduling exact alarm, falling back to inexact alarm", se)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to schedule alarm", e)
        }
    }
}
