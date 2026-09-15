package com.example.audio

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.ActiveAccountStore
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ScheduleResult {
    EXACT_SCHEDULED,
    INEXACT_FALLBACK_SCHEDULED,
    PERMISSION_REQUIRED,
    FAILED
}

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    // Singleton StateFlow to coordinate UI with background Service
    val activeRingingLead = MutableStateFlow<LeadEntity?>(null)

    private val schedulerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startChecking(context: Context) {
        val activeUid = ActiveAccountStore.getActiveUid(context)
        if (activeUid.isNotBlank()) {
            startCheckingForUser(context, activeUid)
        }
    }

    fun startCheckingForUser(context: Context, ownerUid: String) {
        if (ownerUid.isBlank()) return
        val appContext = context.applicationContext
        schedulerScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(appContext)
                val leads = db.leadDao.getAllLeadsList(ownerUid)
                Log.d(TAG, "startCheckingForUser: Rescheduling pending alarms for owner $ownerUid. Total leads: ${leads.size}")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                val now = System.currentTimeMillis()
                for (lead in leads) {
                    if (lead.ownerUid == ownerUid && lead.reminderStatus == "Pending" && lead.reminderDate.isNotEmpty() && lead.reminderTime.isNotEmpty()) {
                        try {
                            val triggerDate = sdf.parse("${lead.reminderDate} ${lead.reminderTime}")
                            val triggerTimeMs = triggerDate?.time ?: 0L
                            if (triggerTimeMs <= now) {
                                val updatedLead = lead.copy(reminderStatus = "Missed")
                                val repo = com.example.data.repository.LeadRepository(
                                    db.leadDao,
                                    com.example.sync.LeadSyncMutationCoordinator(
                                        appContext, db, db.leadDao, db.leadSyncMetadataDao, db.syncDao
                                    )
                                )
                                repo.insertLead(updatedLead, com.example.sync.LeadWriteOrigin.SYSTEM_REMINDER)
                                Log.d(TAG, "startCheckingForUser: Marked stale past reminder for ${lead.name} as Missed")
                            } else {
                                scheduleReminder(appContext, lead)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "startCheckingForUser: Error checking date/time for lead ${lead.id}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startCheckingForUser: Failed for owner $ownerUid", e)
            }
        }
    }

    private var ringingPlayer: android.media.MediaPlayer? = null

    fun playRingingSound(context: Context) {
        Log.d(TAG, "Alarm sound callback: Start playback of alarm sound")
        stopRingingSound()
        
        val sharedPrefs = context.getSharedPreferences("lifefresh_prefs", Context.MODE_PRIVATE)
        val alarmSound = sharedPrefs.getString("alarm_sound", "holiday") ?: "holiday"
        val alarmVolume = sharedPrefs.getString("alarm_volume", "medium") ?: "medium"
        val alarmUseCustom = sharedPrefs.getBoolean("alarm_use_custom", false)
        val customPath = sharedPrefs.getString("alarm_custom_path", null)

        val volumeFactor = when (alarmVolume.lowercase()) {
            "low" -> 0.20f
            "high" -> 1.0f
            else -> 0.60f
        }

        try {
            val player = android.media.MediaPlayer()
            var isStarted = false
            
            if (alarmUseCustom && customPath != null && java.io.File(customPath).exists()) {
                try {
                    player.setDataSource(customPath)
                    player.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                    player.isLooping = true
                    player.setVolume(volumeFactor, volumeFactor)
                    player.prepare()
                    player.start()
                    Log.d(TAG, "Alarm Sound Started: Sound='$customPath', Volume='$alarmVolume'")
                    isStarted = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to play custom sound, falling back to assets: $customPath", e)
                }
            }

            if (!isStarted) {
                val assetName = AlarmSoundResolver.getSoundAssetPath(alarmSound)
                try {
                    val fd = context.assets.openFd(assetName)
                    player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    fd.close()
                    player.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                    player.isLooping = true
                    player.setVolume(volumeFactor, volumeFactor)
                    player.prepare()
                    player.start()
                    Log.d(TAG, "Alarm Sound Started: Sound='$assetName', Volume='$alarmVolume'")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed playing asset $assetName in ReminderScheduler, falling back to AlarmSynthesizer", e)
                    try {
                        AlarmSynthesizer.playAlarmSound(alarmSound, alarmVolume, loop = true)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Synthesizer fallback failed too", ex)
                    }
                }
            }
            
            ringingPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm sound playback", e)
            try {
                AlarmSynthesizer.playAlarmSound(alarmSound, alarmVolume, loop = true)
            } catch (ex: Exception) {
                Log.e(TAG, "Synthesizer fallback failed too", ex)
            }
        }
    }

    fun stopRingingSound() {
        Log.d(TAG, "Alarm sound callback: Stop playback of alarm sound")
        try {
            AlarmSynthesizer.stopAlarmSound()
        } catch (e: Exception) {}
        ringingPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
                Log.d(TAG, "Alarm Sound Stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping active alarm sound", e)
            }
        }
        ringingPlayer = null
    }

    fun scheduleReminder(context: Context, lead: LeadEntity): ScheduleResult {
        val appContext = context.applicationContext
        Log.d(TAG, "scheduleReminder: Lead='${lead.name}', ID='${lead.id}', ownerUid='${lead.ownerUid}', Status='${lead.reminderStatus}'")
        
        if (lead.ownerUid.isBlank()) {
            Log.e(TAG, "scheduleReminder rejected: lead.ownerUid is blank for lead ${lead.id}")
            return ScheduleResult.FAILED
        }

        // Always cancel any existing alarm for safety first
        cancelReminder(appContext, lead.ownerUid, lead.id)

        if (lead.reminderStatus == "Pending" && lead.reminderDate.isNotEmpty() && lead.reminderTime.isNotEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            try {
                val triggerDate = sdf.parse("${lead.reminderDate} ${lead.reminderTime}")
                val triggerTimeMs = triggerDate?.time ?: 0L
                val now = System.currentTimeMillis()

                val sdfAudit = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                Log.d("TIMING_AUDIT", "Alarm scheduled: ${sdfAudit.format(Date(triggerTimeMs))} (triggerEpoch: $triggerTimeMs)")

                if (triggerTimeMs > now) {
                    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val actionName = AlarmActions.ACTION_START_ALARM

                    val intent = Intent(appContext, AlarmReceiver::class.java).apply {
                        action = actionName
                        data = android.net.Uri.parse("lifefresh://alarm/${lead.ownerUid}/${lead.id}/$actionName")
                        putExtra("owner_uid", lead.ownerUid)
                        putExtra("lead_id", lead.id)
                    }

                    val requestCode = ("${lead.ownerUid}:${lead.id}:$actionName").hashCode()
                    val pendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val showIntent = Intent(appContext, MainActivity::class.java).apply {
                        putExtra("ringing_lead_id", lead.id)
                        putExtra("owner_uid", lead.ownerUid)
                    }
                    val showPending = PendingIntent.getActivity(
                        appContext,
                        requestCode,
                        showIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        alarmManager.canScheduleExactAlarms()
                    } else {
                        true
                    }

                    if (canScheduleExact) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTimeMs,
                                    pendingIntent
                                )
                            } else {
                                alarmManager.setExact(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTimeMs,
                                    pendingIntent
                                )
                            }
                            Log.d(TAG, "Exact alarm scheduled successfully at $triggerTimeMs for owner ${lead.ownerUid}, lead ${lead.name}")
                            return ScheduleResult.EXACT_SCHEDULED
                        } catch (se: SecurityException) {
                            Log.w(TAG, "SecurityException on exact alarm, falling back to inexact alarm", se)
                            try {
                                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                                return ScheduleResult.INEXACT_FALLBACK_SCHEDULED
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed inexact fallback after SecurityException", e)
                                return ScheduleResult.PERMISSION_REQUIRED
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error scheduling exact alarm", e)
                            return ScheduleResult.FAILED
                        }
                    } else {
                        Log.w(TAG, "canScheduleExactAlarms is false on API 31+. Scheduling inexact alarm as fallback.")
                        try {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                            return ScheduleResult.INEXACT_FALLBACK_SCHEDULED
                        } catch (e: Exception) {
                            Log.e(TAG, "Error scheduling inexact fallback alarm", e)
                            return ScheduleResult.PERMISSION_REQUIRED
                        }
                    }
                } else {
                    Log.d(TAG, "Not scheduling reminder for ${lead.name} because trigger time $triggerTimeMs is in the past compared to now $now")
                    schedulerScope.launch(Dispatchers.IO) {
                        try {
                            val db = AppDatabase.getDatabase(appContext)
                            val updatedLead = lead.copy(reminderStatus = "Missed")
                            val repo = com.example.data.repository.LeadRepository(
                                db.leadDao,
                                com.example.sync.LeadSyncMutationCoordinator(
                                    appContext, db, db.leadDao, db.leadSyncMetadataDao, db.syncDao
                                )
                            )
                            repo.insertLead(updatedLead, com.example.sync.LeadWriteOrigin.SYSTEM_REMINDER)
                            Log.d(TAG, "scheduleReminder: Updated stale past reminder for ${lead.name} to Missed status")
                        } catch (e: Exception) {
                            Log.e(TAG, "scheduleReminder: Failed to update past reminder status to Missed", e)
                        }
                    }
                    return ScheduleResult.FAILED
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse or schedule reminder date/time for lead ${lead.id}", e)
                return ScheduleResult.FAILED
            }
        }
        return ScheduleResult.FAILED
    }

    fun cancelReminder(context: Context, ownerUid: String, leadId: String) {
        if (ownerUid.isBlank() || leadId.isBlank()) return
        val appContext = context.applicationContext
        Log.d(TAG, "cancelReminder: ownerUid='$ownerUid', leadId='$leadId'")
        try {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val actionsToCancel = listOf(AlarmActions.ACTION_START_ALARM, AlarmActions.LEGACY_ACTION_START_ALARM)
            for (actionName in actionsToCancel) {
                val intent = Intent(appContext, AlarmReceiver::class.java).apply {
                    action = actionName
                    data = android.net.Uri.parse("lifefresh://alarm/${ownerUid}/${leadId}/$actionName")
                    putExtra("owner_uid", ownerUid)
                    putExtra("lead_id", leadId)
                }
                val requestCode = ("$ownerUid:$leadId:$actionName").hashCode()
                val pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                    Log.d(TAG, "Alarm ($actionName) canceled cleanly for ownerUid $ownerUid, leadId $leadId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling reminder for ownerUid $ownerUid, leadId $leadId", e)
        }
    }

    fun cancelReminder(context: Context, leadId: String) {
        val activeUid = ActiveAccountStore.getActiveUid(context)
        if (activeUid.isNotBlank()) {
            cancelReminder(context, activeUid, leadId)
        }
    }

    fun cancelAllRemindersForUser(context: Context, ownerUid: String) {
        if (ownerUid.isBlank()) return
        val appContext = context.applicationContext
        schedulerScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(appContext)
                val leads = db.leadDao.getAllLeadsList(ownerUid)
                for (lead in leads) {
                    cancelReminder(appContext, ownerUid, lead.id)
                }
                Log.d(TAG, "Canceled all scheduled alarms for user $ownerUid (${leads.size} leads checked)")
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling all reminders for user $ownerUid", e)
            }
        }
    }

    fun clearNotificationsForUser(context: Context, ownerUid: String) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }

    fun rescheduleAllReminders(context: Context, leads: List<LeadEntity>) {
        val appContext = context.applicationContext
        val activeUid = ActiveAccountStore.getActiveUid(appContext)
        Log.d(TAG, "rescheduleAllReminders: Rescheduling leads count=${leads.size}")
        for (lead in leads) {
            if (activeUid.isNotBlank() && lead.ownerUid != activeUid) {
                continue
            }
            if (lead.reminderStatus == "Pending") {
                scheduleReminder(appContext, lead)
            } else {
                cancelReminder(appContext, lead.ownerUid, lead.id)
            }
        }
    }
}
