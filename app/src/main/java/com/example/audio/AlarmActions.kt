package com.example.audio

/**
 * Standard intent actions for alarms, reminders, and notifications.
 * Includes legacy actions for seamless backward compatibility with previously scheduled alarms.
 */
object AlarmActions {
    // Current secure actions
    const val ACTION_START_ALARM = "com.lifefreshcrm.pro.action.START_ALARM"
    const val ACTION_NOTIFICATION_DISMISS = "com.lifefreshcrm.pro.action.NOTIFICATION_DISMISS"
    const val ACTION_NOTIFICATION_SNOOZE = "com.lifefreshcrm.pro.action.NOTIFICATION_SNOOZE"
    const val ACTION_NOTIFICATION_COMPLETE = "com.lifefreshcrm.pro.action.NOTIFICATION_COMPLETE"
    const val ACTION_SERVICE_START = "com.lifefreshcrm.pro.action.START"

    // Legacy actions for backward compatibility
    const val LEGACY_ACTION_START_ALARM = "com.example.action.START_ALARM"
    const val LEGACY_ACTION_NOTIFICATION_DISMISS = "com.example.action.NOTIFICATION_DISMISS"
    const val LEGACY_ACTION_NOTIFICATION_SNOOZE = "com.example.action.NOTIFICATION_SNOOZE"
    const val LEGACY_ACTION_NOTIFICATION_COMPLETE = "com.example.action.NOTIFICATION_COMPLETE"
    const val LEGACY_ACTION_SERVICE_START = "com.example.action.START"
}
