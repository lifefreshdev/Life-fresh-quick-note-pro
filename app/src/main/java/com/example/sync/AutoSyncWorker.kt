package com.example.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.sync.model.SyncRunResult
import com.example.sync.model.SyncTrigger

class AutoSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val manualSync = inputData.getBoolean(KEY_MANUAL_SYNC, false)
        val triggerName = inputData.getString(KEY_TRIGGER_NAME)
        val targetUid = inputData.getString(KEY_TARGET_UID)

        val trigger = when {
            manualSync -> SyncTrigger.MANUAL
            triggerName == SyncTrigger.LOCAL_MUTATION.name -> SyncTrigger.LOCAL_MUTATION
            else -> SyncTrigger.PERIODIC
        }

        val syncRepository = SyncRuntimeFactory.getSyncRepository(applicationContext)

        return when (val result = syncRepository.sync(trigger, targetUid)) {
            is SyncRunResult.Success -> {
                Result.success(
                    workDataOf(
                        "pushed" to result.summary.pushed,
                        "pulled" to result.summary.pulled,
                        "status" to "SUCCESS"
                    )
                )
            }
            is SyncRunResult.Partial -> {
                Result.success(
                    workDataOf(
                        "pushed" to result.summary.pushed,
                        "pulled" to result.summary.pulled,
                        "quarantined" to result.summary.quarantined,
                        "status" to "PARTIAL",
                        "message" to result.message
                    )
                )
            }
            is SyncRunResult.RetryableFailure -> {
                Result.retry()
            }
            is SyncRunResult.AuthRequired -> {
                Result.failure(workDataOf("status" to "AUTH_REQUIRED"))
            }
            is SyncRunResult.AutomaticSyncDisabled -> {
                Result.success(workDataOf("status" to "AUTOMATIC_SYNC_DISABLED"))
            }
            is SyncRunResult.PermanentFailure -> {
                Result.failure(workDataOf("status" to "PERMANENT_FAILURE", "message" to result.message))
            }
        }
    }

    companion object {
        const val KEY_MANUAL_SYNC = "manualSync"
        const val KEY_TRIGGER_NAME = "triggerName"
        const val KEY_TARGET_UID = "targetUid"
        const val WORK_NAME_PERIODIC = "lifefresh_periodic_sync"
        const val WORK_NAME_MANUAL = "lifefresh_manual_sync"
        const val WORK_NAME_MUTATION = "lifefresh_mutation_sync"
    }
}
