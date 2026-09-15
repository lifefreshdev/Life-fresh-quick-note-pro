package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadSyncMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: LeadSyncMetadataEntity)

    @Query("SELECT * FROM lead_sync_metadata WHERE leadId = :leadId AND ownerUid = :ownerUid LIMIT 1")
    suspend fun getByLeadId(leadId: String, ownerUid: String): LeadSyncMetadataEntity?

    @Query("SELECT * FROM lead_sync_metadata WHERE leadId = :leadId AND ownerUid = :ownerUid LIMIT 1")
    fun observeByLeadId(leadId: String, ownerUid: String): Flow<LeadSyncMetadataEntity?>

    @Query("SELECT * FROM lead_sync_metadata WHERE ownerUid = :ownerUid AND (syncState = 'PENDING' OR (syncState = 'FAILED' AND retryCount < 5) OR (deleted = 1 AND syncState != 'SYNCED'))")
    suspend fun getPendingSyncItems(ownerUid: String): List<LeadSyncMetadataEntity>

    @Query("SELECT * FROM lead_sync_metadata WHERE ownerUid = :ownerUid AND syncState = 'FAILED'")
    suspend fun getFailedSyncItems(ownerUid: String): List<LeadSyncMetadataEntity>

    @Query("SELECT * FROM lead_sync_metadata WHERE ownerUid = :ownerUid AND deleted = 1 AND syncState != 'SYNCED'")
    suspend fun getDeletedPendingItems(ownerUid: String): List<LeadSyncMetadataEntity>

    @Query("UPDATE lead_sync_metadata SET syncState = 'PENDING', localUpdatedAt = :updatedAt WHERE leadId = :leadId AND ownerUid = :ownerUid")
    suspend fun markPending(leadId: String, ownerUid: String, updatedAt: Long)

    @Query("UPDATE lead_sync_metadata SET syncState = 'SYNCING' WHERE leadId = :leadId AND ownerUid = :ownerUid")
    suspend fun markSyncing(leadId: String, ownerUid: String)

    @Query("UPDATE lead_sync_metadata SET syncState = 'SYNCED', remoteUpdatedAt = :remoteUpdatedAt, lastError = NULL, retryCount = 0 WHERE leadId = :leadId AND ownerUid = :ownerUid")
    suspend fun markSynced(leadId: String, ownerUid: String, remoteUpdatedAt: Long)

    @Query("UPDATE lead_sync_metadata SET syncState = 'FAILED', lastError = :error, retryCount = retryCount + 1 WHERE leadId = :leadId AND ownerUid = :ownerUid")
    suspend fun markFailed(leadId: String, ownerUid: String, error: String)

    @Query("UPDATE lead_sync_metadata SET syncState = 'PENDING', localVersion = :localVersion, localUpdatedAt = :updatedAt WHERE leadId = :leadId AND ownerUid = :ownerUid")
    suspend fun markPendingUpsert(leadId: String, ownerUid: String, localVersion: Long, updatedAt: Long)

    @Query("UPDATE lead_sync_metadata SET deleted = 1, deletedAt = :deletedAt, syncState = 'PENDING', localUpdatedAt = :deletedAt WHERE leadId = :leadId AND ownerUid = :ownerUid")
    suspend fun markDeleted(leadId: String, ownerUid: String, deletedAt: Long)

    @Query("UPDATE lead_sync_metadata SET syncState = 'SYNCED', serverVersion = :serverVersion, remoteUpdatedAt = :remoteUpdatedAt, lastError = NULL, retryCount = 0 WHERE leadId = :leadId AND ownerUid = :ownerUid")
    suspend fun markAcknowledged(leadId: String, ownerUid: String, serverVersion: Long?, remoteUpdatedAt: Long)

    @Query("UPDATE lead_sync_metadata SET localVersion = localVersion + 1, syncState = 'PENDING', localUpdatedAt = :updatedAt WHERE leadId = :leadId AND ownerUid = :ownerUid")
    suspend fun incrementLocalVersion(leadId: String, ownerUid: String, updatedAt: Long)

    @Query("DELETE FROM lead_sync_metadata WHERE leadId = :leadId AND ownerUid = :ownerUid")
    suspend fun deleteMetadata(leadId: String, ownerUid: String)

    @Query("DELETE FROM lead_sync_metadata WHERE ownerUid = :ownerUid")
    suspend fun clearMetadataForUser(ownerUid: String)

    @Query("DELETE FROM lead_sync_metadata")
    suspend fun clearAllMetadata()
}
