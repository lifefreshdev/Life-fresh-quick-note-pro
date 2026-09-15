package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.example.data.ActiveAccountStore
import com.example.data.database.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {
    private val TAG = "AlarmReceiver"
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun logDebug(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    private fun logWarn(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, message)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val sdfAudit = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        logDebug("TIMING_AUDIT", "AlarmReceiver.onReceive entered: ${sdfAudit.format(java.util.Date())}")
        val action = intent.action
        logDebug(TAG, "onReceive: Action received: $action")

        val ownerUid = intent.getStringExtra("owner_uid") ?: ""
        val leadId = intent.getStringExtra("lead_id") ?: ""

        // 1. Reject blank or missing values
        if (ownerUid.isBlank() || leadId.isBlank()) {
            logWarn(TAG, "onReceive rejected: ownerUid or leadId is blank")
            return
        }

        // 2. Read persisted active UID and compare
        val persistedActiveUid = ActiveAccountStore.getActiveUid(context)
        if (ownerUid != persistedActiveUid) {
            logWarn(TAG, "onReceive rejected: alarm ownerUid does not match active account")
            return
        }

        // 3. If FirebaseAuth.currentUser is non-null, verify match
        val currentFirebaseUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        }
        if (currentFirebaseUser != null && currentFirebaseUser.uid != ownerUid) {
            logWarn(TAG, "onReceive rejected: Firebase currentUser does not match alarm owner")
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context.applicationContext)
                val lead = db.leadDao.getLeadById(leadId, ownerUid)
                if (lead == null || lead.ownerUid != ownerUid) {
                    logWarn(TAG, "onReceive rejected: Lead not found or ownership mismatch")
                    return@launch
                }

                when (action) {
                    AlarmActions.ACTION_START_ALARM, AlarmActions.LEGACY_ACTION_START_ALARM -> {
                        if (lead.reminderStatus == "Pending" && lead.status != "Complete" && !lead.archived) {
                            logDebug(TAG, "Received START_ALARM verified for lead")
                            AlarmService.startService(context, ownerUid, leadId)

                            val coordinator = com.example.sync.LeadSyncMutationCoordinator(
                                context.applicationContext,
                                db,
                                db.leadDao,
                                db.leadSyncMetadataDao,
                                db.syncDao
                            )
                            val updated = lead.copy(reminderStatus = "Triggered")
                            coordinator.upsertLead(updated, com.example.sync.LeadWriteOrigin.SYSTEM_REMINDER)
                            logDebug(TAG, "Successfully updated status for lead to Triggered")
                        } else {
                            logDebug(TAG, "START_ALARM ignored: Lead status is '${lead.reminderStatus}', archived=${lead.archived}")
                        }
                    }
                    AlarmActions.ACTION_NOTIFICATION_DISMISS, AlarmActions.LEGACY_ACTION_NOTIFICATION_DISMISS -> {
                        logDebug(TAG, "Notification Dismissed for lead")
                        AlarmService.stopService(context, ownerUid)
                        val coordinator = com.example.sync.LeadSyncMutationCoordinator(
                            context.applicationContext,
                            db,
                            db.leadDao,
                            db.leadSyncMetadataDao,
                            db.syncDao
                        )
                        val updated = lead.copy(reminderStatus = "Dismissed")
                        coordinator.upsertLead(updated, com.example.sync.LeadWriteOrigin.SYSTEM_REMINDER)
                        ReminderScheduler.activeRingingLead.value = null
                    }
                    AlarmActions.ACTION_NOTIFICATION_SNOOZE, AlarmActions.LEGACY_ACTION_NOTIFICATION_SNOOZE -> {
                        logDebug(TAG, "Notification Snoozed for lead")
                        AlarmService.stopService(context, ownerUid)
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.MINUTE, 5)

                        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val sdfTime = SimpleDateFormat("HH:mm", Locale.US)

                        val snoozeDate = sdfDate.format(calendar.time)
                        val snoozeTime = sdfTime.format(calendar.time)

                        val updated = lead.copy(
                            reminderDate = snoozeDate,
                            reminderTime = snoozeTime,
                            reminderStatus = "Pending"
                        )
                        val coordinator = com.example.sync.LeadSyncMutationCoordinator(
                            context.applicationContext,
                            db,
                            db.leadDao,
                            db.leadSyncMetadataDao,
                            db.syncDao
                        )
                        coordinator.upsertLead(updated, com.example.sync.LeadWriteOrigin.SYSTEM_REMINDER)
                        ReminderScheduler.scheduleReminder(context.applicationContext, updated)
                        ReminderScheduler.activeRingingLead.value = null
                    }
                    AlarmActions.ACTION_NOTIFICATION_COMPLETE, AlarmActions.LEGACY_ACTION_NOTIFICATION_COMPLETE -> {
                        logDebug(TAG, "Notification Completed for lead")
                        AlarmService.stopService(context, ownerUid)
                        val coordinator = com.example.sync.LeadSyncMutationCoordinator(
                            context.applicationContext,
                            db,
                            db.leadDao,
                            db.leadSyncMetadataDao,
                            db.syncDao
                        )
                        val updated = lead.copy(
                            status = "Complete",
                            reminderStatus = "Completed"
                        )
                        coordinator.upsertLead(updated, com.example.sync.LeadWriteOrigin.SYSTEM_REMINDER)
                        ReminderScheduler.activeRingingLead.value = null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing AlarmReceiver action $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
