package com.example.sync

import android.content.Context
import androidx.work.*
import com.example.sync.model.SyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private fun isGuestUser(): Boolean {
        return try {
            val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            fbUser == null || fbUser.isAnonymous || fbUser.uid.isBlank()
        } catch (_: Exception) {
            true
        }
    }

    fun enqueueManual(context: Context, ownerUid: String? = null) {
        if (isGuestUser()) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = workDataOf(
            AutoSyncWorker.KEY_MANUAL_SYNC to true,
            AutoSyncWorker.KEY_TRIGGER_NAME to SyncTrigger.MANUAL.name,
            AutoSyncWorker.KEY_TARGET_UID to ownerUid
        )

        val request = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()

        val workName = if (!ownerUid.isNullOrBlank()) "${AutoSyncWorker.WORK_NAME_MANUAL}_$ownerUid" else AutoSyncWorker.WORK_NAME_MANUAL

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    fun enqueueMutationSync(context: Context, ownerUid: String? = null) {
        if (isGuestUser()) return
        val prefs = SyncPreferences(context)
        if (!prefs.isAutomaticSyncEnabled()) {
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = workDataOf(
            AutoSyncWorker.KEY_MANUAL_SYNC to false,
            AutoSyncWorker.KEY_TRIGGER_NAME to SyncTrigger.LOCAL_MUTATION.name,
            AutoSyncWorker.KEY_TARGET_UID to ownerUid
        )

        val request = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()

        val workName = if (!ownerUid.isNullOrBlank()) "${AutoSyncWorker.WORK_NAME_MUTATION}_$ownerUid" else AutoSyncWorker.WORK_NAME_MUTATION

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    fun schedulePeriodicSync(context: Context, ownerUid: String? = null) {
        if (isGuestUser()) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val data = workDataOf(
            AutoSyncWorker.KEY_MANUAL_SYNC to false,
            AutoSyncWorker.KEY_TRIGGER_NAME to SyncTrigger.PERIODIC.name,
            AutoSyncWorker.KEY_TARGET_UID to ownerUid
        )

        val periodicRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .build()

        val workName = if (!ownerUid.isNullOrBlank()) "${AutoSyncWorker.WORK_NAME_PERIODIC}_$ownerUid" else AutoSyncWorker.WORK_NAME_PERIODIC

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
    }

    fun cancelPeriodicSync(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(AutoSyncWorker.WORK_NAME_PERIODIC)
    }

    /** Cancels and waits for every sync chain that can target the deleting account. */
    suspend fun cancelAllForUser(context: Context, ownerUid: String) {
        if (ownerUid.isBlank()) return
        withContext(Dispatchers.IO) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            val names = listOf(
                "${AutoSyncWorker.WORK_NAME_MANUAL}_$ownerUid",
                "${AutoSyncWorker.WORK_NAME_MUTATION}_$ownerUid",
                "${AutoSyncWorker.WORK_NAME_PERIODIC}_$ownerUid",
                AutoSyncWorker.WORK_NAME_MANUAL,
                AutoSyncWorker.WORK_NAME_MUTATION,
                AutoSyncWorker.WORK_NAME_PERIODIC
            )
            names.distinct().forEach { workName ->
                workManager.cancelUniqueWork(workName).result.get(15, TimeUnit.SECONDS)
            }
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = SyncPreferences(context)
        prefs.setAutomaticSyncEnabled(enabled)
        if (enabled) {
            schedulePeriodicSync(context)
        } else {
            cancelPeriodicSync(context)
        }
    }
}
