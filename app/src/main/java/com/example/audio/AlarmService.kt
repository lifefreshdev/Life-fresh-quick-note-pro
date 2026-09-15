package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.ActiveAccountStore
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var autoStopJob: Job? = null

    companion object {
        private const val TAG = "AlarmService"
        private const val CHANNEL_ID = "alarm_reminders_channel"
        private const val NOTIFICATION_ID = 9999

        fun startService(context: Context, ownerUid: String, leadId: String) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = AlarmActions.ACTION_SERVICE_START
                putExtra("owner_uid", ownerUid)
                putExtra("lead_id", leadId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context, ownerUid: String? = null) {
            val intent = Intent(context, AlarmService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceContext: Context get() = this

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sdfAudit = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
        Log.d("TIMING_AUDIT", "AlarmService.onStartCommand: ${sdfAudit.format(java.util.Date())}")
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        val ownerUid = intent?.getStringExtra("owner_uid") ?: ""
        val leadId = intent?.getStringExtra("lead_id") ?: ""

        // Start minimal foreground immediately for system compliance
        createNotificationChannel()
        val genericNotif = NotificationCompat.Builder(serviceContext, CHANNEL_ID)
            .setContentTitle("Reminder")
            .setContentText("Checking reminder...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, genericNotif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, genericNotif)
        }

        if ((action == AlarmActions.ACTION_SERVICE_START || action == AlarmActions.LEGACY_ACTION_SERVICE_START) && ownerUid.isNotBlank() && leadId.isNotBlank()) {
            // Validate persisted active UID
            val persistedActiveUid = ActiveAccountStore.getActiveUid(this)
            if (ownerUid != persistedActiveUid) {
                Log.w(TAG, "AlarmService rejected: ownerUid ($ownerUid) != persistedActiveUid ($persistedActiveUid)")
                stopRingtoneAndCleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            // Validate Firebase user if initialized
            val currentFirebaseUser = try {
                FirebaseAuth.getInstance().currentUser
            } catch (e: Exception) {
                null
            }
            if (currentFirebaseUser != null && currentFirebaseUser.uid != ownerUid) {
                Log.w(TAG, "AlarmService rejected: Firebase currentUser (${currentFirebaseUser.uid}) != ownerUid ($ownerUid)")
                stopRingtoneAndCleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            // Query Room for verified Lead Entity
            scope.launch {
                try {
                    val db = AppDatabase.getDatabase(serviceContext)
                    val lead = db.leadDao.getLeadById(leadId, ownerUid)
                    if (lead == null || lead.ownerUid != ownerUid) {
                        Log.w(TAG, "AlarmService rejected: Lead $leadId not found or ownerUid mismatch in Room")
                        stopRingtoneAndCleanup()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@launch
                    }

                    startRingingWithVerifiedLead(lead)
                } catch (e: Exception) {
                    Log.e(TAG, "Error validating lead in AlarmService", e)
                    stopRingtoneAndCleanup()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } else {
            stopRingtoneAndCleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startRingingWithVerifiedLead(lead: LeadEntity) {
        val sdfAudit = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
        Log.d("TIMING_AUDIT", "startRingingWithVerifiedLead: ${sdfAudit.format(java.util.Date())}")
        Log.d(TAG, "Starting ringing process for verified lead ${lead.name} (owner ${lead.ownerUid})")

        playRingtone()

        ReminderScheduler.activeRingingLead.value = lead

        // Auto-stop timer setup
        val sharedPrefs = serviceContext.getSharedPreferences("lifefresh_prefs", Context.MODE_PRIVATE)
        val reminderRingMode = sharedPrefs.getString("reminder_ring_mode", "continuous") ?: "continuous"
        if (reminderRingMode == "auto_stop") {
            autoStopJob?.cancel()
            autoStopJob = scope.launch {
                delay(120_000)
                Log.d(TAG, "Auto-stop timer elapsed for lead: ${lead.id}")
                try {
                    val db = AppDatabase.getDatabase(serviceContext)
                    val currentLead = db.leadDao.getLeadById(lead.id, lead.ownerUid)
                    if (currentLead != null && (currentLead.reminderStatus == "Pending" || currentLead.reminderStatus == "Triggered" || currentLead.reminderStatus == "PendingRenewal")) {
                        val updated = currentLead.copy(reminderStatus = "Overdue")
                        val repo = com.example.data.repository.LeadRepository(
                            db.leadDao,
                            com.example.sync.LeadSyncMutationCoordinator(
                                serviceContext, db, db.leadDao, db.leadSyncMetadataDao, db.syncDao
                            )
                        )
                        repo.insertLead(updated, com.example.sync.LeadWriteOrigin.SYSTEM_REMINDER)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating status to Overdue inside auto-stop", e)
                }
                stopRingtoneAndCleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        // Notification Action PendingIntents carrying verified owner_uid & lead_id
        val dismissAction = AlarmActions.ACTION_NOTIFICATION_DISMISS
        val dismissIntent = Intent(serviceContext, AlarmReceiver::class.java).apply {
            action = dismissAction
            data = android.net.Uri.parse("lifefresh://alarm/${lead.ownerUid}/${lead.id}/$dismissAction")
            putExtra("owner_uid", lead.ownerUid)
            putExtra("lead_id", lead.id)
        }
        val dismissPending = PendingIntent.getBroadcast(
            serviceContext,
            ("${lead.ownerUid}:${lead.id}:$dismissAction").hashCode(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeAction = AlarmActions.ACTION_NOTIFICATION_SNOOZE
        val snoozeIntent = Intent(serviceContext, AlarmReceiver::class.java).apply {
            action = snoozeAction
            data = android.net.Uri.parse("lifefresh://alarm/${lead.ownerUid}/${lead.id}/$snoozeAction")
            putExtra("owner_uid", lead.ownerUid)
            putExtra("lead_id", lead.id)
        }
        val snoozePending = PendingIntent.getBroadcast(
            serviceContext,
            ("${lead.ownerUid}:${lead.id}:$snoozeAction").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeAction = AlarmActions.ACTION_NOTIFICATION_COMPLETE
        val completeIntent = Intent(serviceContext, AlarmReceiver::class.java).apply {
            action = completeAction
            data = android.net.Uri.parse("lifefresh://alarm/${lead.ownerUid}/${lead.id}/$completeAction")
            putExtra("owner_uid", lead.ownerUid)
            putExtra("lead_id", lead.id)
        }
        val completePending = PendingIntent.getBroadcast(
            serviceContext,
            ("${lead.ownerUid}:${lead.id}:$completeAction").hashCode(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(serviceContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ringing_lead_id", lead.id)
            putExtra("owner_uid", lead.ownerUid)
        }
        val contentPending = PendingIntent.getActivity(
            serviceContext,
            ("${lead.ownerUid}:${lead.id}:content").hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(serviceContext, CHANNEL_ID)
            .setContentTitle("Reminder for ${lead.name}")
            .setContentText(lead.reminderNote.ifEmpty { "Client wellness reminder alarm is ringing." })
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(contentPending, true)
            .setContentIntent(contentPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze (5m)", snoozePending)
            .addAction(android.R.drawable.ic_menu_save, "Complete", completePending)
            .build()

        val manager = serviceContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun playRingtone() {
        stopRingtoneAndCleanup()

        val sharedPrefs = serviceContext.getSharedPreferences("lifefresh_prefs", Context.MODE_PRIVATE)
        val alarmSound = sharedPrefs.getString("alarm_sound", "holiday") ?: "holiday"
        val alarmVolume = sharedPrefs.getString("alarm_volume", "medium") ?: "medium"
        val alarmUseCustom = sharedPrefs.getBoolean("alarm_use_custom", false)
        val alarmCustomPath = sharedPrefs.getString("alarm_custom_path", null)

        var playedCustom = false
        if (alarmUseCustom && alarmCustomPath != null) {
            val customFile = java.io.File(alarmCustomPath)
            if (customFile.exists()) {
                try {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(alarmCustomPath)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            setAudioStreamType(AudioManager.STREAM_ALARM)
                        }
                        val volumeFactor = when (alarmVolume.lowercase()) {
                            "low" -> 0.20f
                            "high" -> 1.0f
                            else -> 0.60f
                        }
                        setVolume(volumeFactor, volumeFactor)
                        isLooping = true
                        prepare()
                        start()
                    }
                    playedCustom = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to play custom audio file. Falling back to asset.", e)
                }
            }
        }

        if (!playedCustom) {
            val assetName = AlarmSoundResolver.getSoundAssetPath(alarmSound)

            try {
                val fd = serviceContext.assets.openFd(assetName)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    fd.close()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setAudioStreamType(AudioManager.STREAM_ALARM)
                    }

                    val volumeFactor = AlarmSoundResolver.getVolumeFactor(alarmVolume)
                    setVolume(volumeFactor, volumeFactor)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed playing asset ringtone $assetName via MediaPlayer, falling back to AlarmSynthesizer", e)
                try {
                    AlarmSynthesizer.playAlarmSound(alarmSound, alarmVolume, loop = true)
                } catch (ex: Exception) {
                    Log.e(TAG, "Synthesizer fallback failed too", ex)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alarm Reminders"
            val desc = "System Alarm clock reminder notifications."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = desc
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            val manager = serviceContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopRingtoneAndCleanup() {
        try {
            AlarmSynthesizer.stopAlarmSound()
        } catch (e: Exception) {}
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {}
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy service")
        autoStopJob?.cancel()
        stopRingtoneAndCleanup()
        ReminderScheduler.activeRingingLead.value = null
    }
}
