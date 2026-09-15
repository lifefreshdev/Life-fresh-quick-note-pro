package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.example.data.ActiveAccountStore
import com.example.data.database.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BOOT_RECEIVED: Received boot broadcast action: $action")
        
        val isUnlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
            userManager?.isUserUnlocked ?: true
        } else {
            true
        }
        
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            if (!isUnlocked) {
                Log.w(TAG, "Database is unavailable during LOCKED_BOOT_COMPLETED because device is locked. Skipping reminder restoration.")
                return
            }

            val persistedUid = ActiveAccountStore.getActiveUid(context)
            if (persistedUid.isBlank()) {
                Log.d(TAG, "No active persisted UID found on boot. Skipping boot recovery.")
                return
            }

            val currentFirebaseUser = try {
                FirebaseAuth.getInstance().currentUser
            } catch (e: Exception) {
                null
            }

            if (currentFirebaseUser != null && currentFirebaseUser.uid != persistedUid) {
                Log.w(TAG, "Firebase currentUser (${currentFirebaseUser.uid}) != persistedUid ($persistedUid). Skipping boot recovery.")
                return
            }

            val pendingResult = goAsync()
            val appContext = context.applicationContext
            scope.launch {
                try {
                    Log.d(TAG, "Accessing database to retrieve leads for owner $persistedUid.")
                    val db = AppDatabase.getDatabase(appContext)
                    val leads = db.leadDao.getAllLeadsList(persistedUid)
                    
                    val pendingLeads = leads.filter { lead ->
                        lead.ownerUid == persistedUid &&
                        lead.reminderStatus == "Pending" &&
                        lead.status != "Complete" &&
                        !lead.archived &&
                        lead.reminderDate.isNotEmpty() &&
                        lead.reminderTime.isNotEmpty()
                    }
                    
                    Log.d(TAG, "Found ${pendingLeads.size} active pending reminders to reschedule for owner $persistedUid.")
                    
                    for (lead in pendingLeads) {
                        try {
                            ReminderScheduler.scheduleReminder(appContext, lead)
                            Log.d(TAG, "Rescheduled reminder for lead ID: ${lead.id}, Name: ${lead.name}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to reschedule reminder for lead ID: ${lead.id}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in boot receiver recovery", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
