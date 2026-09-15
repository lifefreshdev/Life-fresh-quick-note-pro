package com.example.sync.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncDao {

    // --- Outbox Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMutation(mutation: SyncOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMutationIgnore(mutation: SyncOutboxEntity): Long

    @Query("SELECT * FROM sync_outbox WHERE ownerUid = :ownerUid AND (state = 'PENDING' OR (state = 'FAILED' AND (nextAttemptAtUtc IS NULL OR nextAttemptAtUtc <= :nowUtc))) ORDER BY createdAtUtc ASC LIMIT :limit")
    suspend fun getReadyPendingMutations(ownerUid: String, nowUtc: Long, limit: Int = 50): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE ownerUid = :ownerUid AND state = :state ORDER BY createdAtUtc ASC")
    suspend fun getMutationsByState(ownerUid: String, state: String): List<SyncOutboxEntity>

    @Query("UPDATE sync_outbox SET state = 'IN_FLIGHT', updatedAtUtc = :updatedAtUtc WHERE ownerUid = :ownerUid AND mutationId IN (:mutationIds)")
    suspend fun claimBatch(ownerUid: String, mutationIds: List<String>, updatedAtUtc: Long)

    @Query("UPDATE sync_outbox SET state = 'ACKNOWLEDGED', updatedAtUtc = :updatedAtUtc WHERE ownerUid = :ownerUid AND mutationId = :mutationId")
    suspend fun acknowledgeMutation(ownerUid: String, mutationId: String, updatedAtUtc: Long)

    @Query("UPDATE sync_outbox SET state = 'FAILED', attemptCount = attemptCount + 1, lastErrorClass = :errorClass, lastErrorMessage = :errorMessage, nextAttemptAtUtc = :nextAttemptAtUtc, updatedAtUtc = :updatedAtUtc WHERE ownerUid = :ownerUid AND mutationId = :mutationId")
    suspend fun failMutation(ownerUid: String, mutationId: String, errorClass: String, errorMessage: String, nextAttemptAtUtc: Long?, updatedAtUtc: Long)

    @Query("UPDATE sync_outbox SET state = 'QUARANTINED', lastErrorClass = :errorClass, lastErrorMessage = :errorMessage, updatedAtUtc = :updatedAtUtc WHERE ownerUid = :ownerUid AND mutationId = :mutationId")
    suspend fun quarantineMutation(ownerUid: String, mutationId: String, errorClass: String, errorMessage: String, updatedAtUtc: Long)

    @Query("UPDATE sync_outbox SET state = 'PENDING', updatedAtUtc = :updatedAtUtc WHERE ownerUid = :ownerUid AND state = 'IN_FLIGHT'")
    suspend fun resetInFlightMutations(ownerUid: String, updatedAtUtc: Long)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE ownerUid = :ownerUid AND (state = 'PENDING' OR state = 'FAILED' OR state = 'IN_FLIGHT')")
    suspend fun getPendingCount(ownerUid: String): Int

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE ownerUid = :ownerUid AND state IN ('PENDING', 'IN_FLIGHT', 'FAILED', 'QUARANTINED')")
    suspend fun getUnsyncedOutboxCount(ownerUid: String): Int

    @Query("DELETE FROM sync_outbox WHERE ownerUid = :ownerUid AND state = 'ACKNOWLEDGED' AND updatedAtUtc < :timestampUtc")
    suspend fun deleteAcknowledgedBefore(ownerUid: String, timestampUtc: Long)

    @Query("DELETE FROM sync_outbox WHERE ownerUid = :ownerUid AND entityType = :entityType AND entityId = :entityId")
    suspend fun clearOutboxForEntity(ownerUid: String, entityType: String, entityId: String)

    @Query("SELECT * FROM sync_outbox WHERE ownerUid = :ownerUid AND entityType = :entityType AND entityId = :entityId AND state IN ('PENDING', 'IN_FLIGHT', 'FAILED') ORDER BY createdAtUtc DESC")
    suspend fun getPendingMutationsForEntity(ownerUid: String, entityType: String, entityId: String): List<SyncOutboxEntity>

    // --- Conflict Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: SyncConflictEntity)

    @Query("SELECT * FROM sync_conflicts WHERE ownerUid = :ownerUid AND resolved = 0 ORDER BY detectedAtUtc DESC")
    suspend fun getUnresolvedConflicts(ownerUid: String): List<SyncConflictEntity>

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE ownerUid = :ownerUid AND resolved = 0")
    suspend fun countUnresolvedConflicts(ownerUid: String): Int

    @Query("UPDATE sync_conflicts SET resolved = 1, resolvedAtUtc = :resolvedAtUtc WHERE ownerUid = :ownerUid AND conflictId = :conflictId")
    suspend fun markConflictResolved(ownerUid: String, conflictId: String, resolvedAtUtc: Long)

    @Query("DELETE FROM sync_conflicts WHERE ownerUid = :ownerUid AND resolved = 1 AND detectedAtUtc < :beforeTimestampUtc")
    suspend fun pruneResolvedConflicts(ownerUid: String, beforeTimestampUtc: Long)

    @Query("SELECT * FROM sync_conflicts WHERE ownerUid = :ownerUid AND resolved = 1 ORDER BY detectedAtUtc DESC")
    suspend fun getResolvedConflicts(ownerUid: String): List<SyncConflictEntity>

    @Query("DELETE FROM sync_conflicts WHERE ownerUid = :ownerUid AND conflictId = :conflictId")
    suspend fun deleteConflictById(ownerUid: String, conflictId: String)

    @Query("DELETE FROM sync_conflicts WHERE ownerUid = :ownerUid AND resolved = 1 AND conflictId NOT IN (SELECT conflictId FROM sync_conflicts WHERE ownerUid = :ownerUid AND resolved = 1 ORDER BY detectedAtUtc DESC LIMIT :keepLimit)")
    suspend fun pruneExcessResolvedConflicts(ownerUid: String, keepLimit: Int = 100)

    // --- Checkpoint Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCheckpoint(checkpoint: SyncCheckpointEntity)

    @Query("SELECT * FROM sync_checkpoint WHERE scopeId = :scopeId LIMIT 1")
    suspend fun getCheckpoint(scopeId: String): SyncCheckpointEntity?

    @Query("UPDATE sync_checkpoint SET lastPhase = :phase, lastMessage = :message, lastRunCompletedAtUtc = :updatedAtUtc WHERE scopeId = :scopeId")
    suspend fun updateCheckpointStatus(scopeId: String, phase: String, message: String?, updatedAtUtc: Long)

    @Query("UPDATE sync_checkpoint SET remoteCursor = :remoteCursor, lastSuccessfulSyncAtUtc = :lastSuccessfulSyncAtUtc, lastRunCompletedAtUtc = :updatedAtUtc WHERE scopeId = :scopeId")
    suspend fun commitCheckpointCursor(scopeId: String, remoteCursor: String?, lastSuccessfulSyncAtUtc: Long, updatedAtUtc: Long)

    @Query("DELETE FROM sync_outbox WHERE ownerUid = :ownerUid")
    suspend fun clearOutboxForUser(ownerUid: String)

    @Query("DELETE FROM sync_conflicts WHERE ownerUid = :ownerUid")
    suspend fun clearConflictsForUser(ownerUid: String)

    @Query("DELETE FROM sync_checkpoint WHERE scopeId = :scopeId")
    suspend fun clearCheckpointByScope(scopeId: String)
}
