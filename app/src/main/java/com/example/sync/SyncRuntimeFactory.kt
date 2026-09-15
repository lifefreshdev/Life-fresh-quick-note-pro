package com.example.sync

import android.content.Context
import com.example.audio.ReminderScheduler
import com.example.data.database.AppDatabase
import com.example.sync.local.SyncLocalStore
import com.example.sync.remote.FirestoreSyncDataSource
import com.example.sync.remote.RemoteSyncDataSource
import com.google.firebase.auth.FirebaseAuth

object SyncRuntimeFactory {

    @Volatile
    private var syncRepositoryInstance: SyncRepository? = null

    @Volatile
    private var customRemoteDataSource: RemoteSyncDataSource? = null

    @Volatile
    private var customUserProvider: (() -> SyncUser?)? = null

    fun setTestOverrides(
        remoteDataSource: RemoteSyncDataSource? = null,
        userProvider: (() -> SyncUser?)? = null
    ) {
        synchronized(this) {
            customRemoteDataSource = remoteDataSource
            customUserProvider = userProvider
            syncRepositoryInstance = null
        }
    }

    fun resetOverrides() {
        synchronized(this) {
            customRemoteDataSource = null
            customUserProvider = null
            syncRepositoryInstance = null
        }
    }

    fun getSyncRepository(context: Context): SyncRepository {
        return syncRepositoryInstance ?: synchronized(this) {
            syncRepositoryInstance ?: createSyncRepository(context.applicationContext).also {
                syncRepositoryInstance = it
            }
        }
    }

    private fun createSyncRepository(appContext: Context): SyncRepository {
        val database = AppDatabase.getDatabase(appContext)
        val leadDao = database.leadDao
        val metadataDao = database.leadSyncMetadataDao
        val syncDao = database.syncDao
        val localStore = SyncLocalStore(database, syncDao, metadataDao)
        val remoteDataSource = customRemoteDataSource ?: FirestoreSyncDataSource()
        val syncPreferences = SyncPreferences(appContext)

        val userProvider: () -> SyncUser? = customUserProvider ?: {
            val fbUser = try {
                FirebaseAuth.getInstance().currentUser
            } catch (e: Exception) {
                null
            }
            fbUser?.let { SyncUser(uid = it.uid, isAnonymous = it.isAnonymous) }
        }

        return SyncRepository(
            database = database,
            leadDao = leadDao,
            metadataDao = metadataDao,
            syncDao = syncDao,
            localStore = localStore,
            remoteDataSource = remoteDataSource,
            syncPreferences = syncPreferences,
            currentUserProvider = userProvider,
            deviceIdProvider = { syncPreferences.getOrCreateDeviceId() },
            clock = { System.currentTimeMillis() },
            reminderRescheduler = { leads ->
                try {
                    ReminderScheduler.rescheduleAllReminders(appContext, leads)
                } catch (e: Exception) {
                    // Ignore alarm scheduling errors in background sync
                }
            }
        )
    }
}
