package com.example.sync.local

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.database.LeadSyncMetadataDao
import com.example.data.database.LeadSyncMetadataEntity
import java.util.UUID

class SyncLocalStore(
    private val database: AppDatabase,
    private val syncDao: SyncDao,
    private val metadataDao: LeadSyncMetadataDao
) {

    suspend fun enqueueLeadUpsert(
        ownerUid: String,
        leadId: String,
        payloadHash: String,
        localVersion: Long,
        timestampUtc: Long
    ): SyncOutboxEntity = database.withTransaction {
        val existingMeta = metadataDao.getByLeadId(leadId, ownerUid)
        if (existingMeta != null) {
            metadataDao.insertOrUpdate(
                existingMeta.copy(
                    ownerUid = ownerUid,
                    localVersion = localVersion,
                    localUpdatedAt = timestampUtc,
                    syncState = "PENDING",
                    deleted = false
                )
            )
        } else {
            metadataDao.insertOrUpdate(
                LeadSyncMetadataEntity(
                    ownerUid = ownerUid,
                    leadId = leadId,
                    localUpdatedAt = timestampUtc,
                    syncState = "PENDING",
                    localVersion = localVersion
                )
            )
        }

        val mutation = SyncOutboxEntity(
            mutationId = UUID.randomUUID().toString(),
            ownerUid = ownerUid,
            entityType = "LEAD",
            entityId = leadId,
            operation = "UPSERT",
            localVersion = localVersion,
            payloadHash = payloadHash,
            state = "PENDING",
            attemptCount = 0,
            createdAtUtc = timestampUtc,
            updatedAtUtc = timestampUtc
        )
        syncDao.insertMutation(mutation)
        mutation
    }

    suspend fun enqueueLeadDelete(
        ownerUid: String,
        leadId: String,
        deletedAtUtc: Long
    ): SyncOutboxEntity = database.withTransaction {
        val existingMeta = metadataDao.getByLeadId(leadId, ownerUid)
        val newVersion = (existingMeta?.localVersion ?: 0L) + 1L

        if (existingMeta != null) {
            metadataDao.insertOrUpdate(
                existingMeta.copy(
                    ownerUid = ownerUid,
                    deleted = true,
                    deletedAt = deletedAtUtc,
                    localUpdatedAt = deletedAtUtc,
                    syncState = "PENDING",
                    localVersion = newVersion
                )
            )
        } else {
            metadataDao.insertOrUpdate(
                LeadSyncMetadataEntity(
                    ownerUid = ownerUid,
                    leadId = leadId,
                    localUpdatedAt = deletedAtUtc,
                    deleted = true,
                    deletedAt = deletedAtUtc,
                    syncState = "PENDING",
                    localVersion = newVersion
                )
            )
        }

        val mutation = SyncOutboxEntity(
            mutationId = UUID.randomUUID().toString(),
            ownerUid = ownerUid,
            entityType = "LEAD",
            entityId = leadId,
            operation = "DELETE",
            localVersion = newVersion,
            payloadHash = "",
            state = "PENDING",
            attemptCount = 0,
            createdAtUtc = deletedAtUtc,
            updatedAtUtc = deletedAtUtc
        )
        syncDao.insertMutation(mutation)
        mutation
    }

    suspend fun claimNextBatch(
        ownerUid: String,
        limit: Int = 50,
        nowUtc: Long
    ): List<SyncOutboxEntity> = database.withTransaction {
        val ready = syncDao.getReadyPendingMutations(ownerUid, nowUtc, limit)
        if (ready.isNotEmpty()) {
            val ids = ready.map { it.mutationId }
            syncDao.claimBatch(ownerUid, ids, nowUtc)
            ready.map { it.copy(state = "IN_FLIGHT", updatedAtUtc = nowUtc) }
        } else {
            emptyList()
        }
    }

    suspend fun acknowledgeMutation(
        ownerUid: String,
        mutationId: String,
        leadId: String?,
        serverVersion: Long?,
        remoteUpdatedAt: Long?,
        nowUtc: Long
    ) = database.withTransaction {
        syncDao.acknowledgeMutation(ownerUid, mutationId, nowUtc)
        if (!leadId.isNullOrBlank()) {
            metadataDao.markAcknowledged(
                leadId = leadId,
                ownerUid = ownerUid,
                serverVersion = serverVersion,
                remoteUpdatedAt = remoteUpdatedAt ?: nowUtc
            )
        }
    }

    suspend fun failMutation(
        ownerUid: String,
        mutationId: String,
        errorClass: String,
        errorMessage: String,
        nextAttemptAtUtc: Long?,
        nowUtc: Long
    ) = database.withTransaction {
        syncDao.failMutation(ownerUid, mutationId, errorClass, errorMessage, nextAttemptAtUtc, nowUtc)
    }

    suspend fun quarantineMutation(
        ownerUid: String,
        mutationId: String,
        errorClass: String,
        errorMessage: String,
        nowUtc: Long
    ) = database.withTransaction {
        syncDao.quarantineMutation(ownerUid, mutationId, errorClass, errorMessage, nowUtc)
    }

    suspend fun saveConflict(conflict: SyncConflictEntity) {
        syncDao.insertConflict(conflict)
    }

    suspend fun commitRemotePage(
        ownerUid: String,
        scopeId: String,
        remoteCursor: String?,
        lastSuccessfulSyncAtUtc: Long,
        conflicts: List<SyncConflictEntity>,
        ackMutationIds: List<String>,
        localMergeAction: suspend () -> Unit,
        nowUtc: Long
    ) = database.withTransaction {
        localMergeAction()
        for (conflict in conflicts) {
            syncDao.insertConflict(conflict)
        }
        for (mutationId in ackMutationIds) {
            syncDao.acknowledgeMutation(ownerUid, mutationId, nowUtc)
        }
        val existingCheckpoint = syncDao.getCheckpoint(scopeId)
        if (existingCheckpoint != null) {
            syncDao.commitCheckpointCursor(scopeId, remoteCursor, lastSuccessfulSyncAtUtc, nowUtc)
        } else {
            syncDao.insertOrUpdateCheckpoint(
                SyncCheckpointEntity(
                    scopeId = scopeId,
                    remoteCursor = remoteCursor,
                    lastSuccessfulSyncAtUtc = lastSuccessfulSyncAtUtc,
                    lastRunCompletedAtUtc = nowUtc
                )
            )
        }
    }

    suspend fun getPendingCount(ownerUid: String): Int {
        return syncDao.getPendingCount(ownerUid)
    }
}
