package com.example.sync

import com.example.data.database.AppDatabase
import com.example.data.database.LeadDao
import com.example.data.database.LeadEntity
import com.example.data.database.LeadSyncMetadataDao
import com.example.data.database.LeadSyncMetadataEntity
import com.example.sync.local.SyncConflictEntity
import com.example.sync.local.SyncDao
import com.example.sync.local.SyncLocalStore
import com.example.sync.model.*
import com.example.sync.remote.RemoteLeadMapper
import com.example.sync.remote.RemoteSyncDataSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class SyncUser(
    val uid: String,
    val isAnonymous: Boolean = false
)

class SyncRepository(
    private val database: AppDatabase,
    private val leadDao: LeadDao,
    private val metadataDao: LeadSyncMetadataDao,
    private val syncDao: SyncDao,
    private val localStore: SyncLocalStore,
    private val remoteDataSource: RemoteSyncDataSource,
    private val syncPreferences: SyncPreferences,
    private val currentUserProvider: () -> SyncUser? = { null },
    private val deviceIdProvider: () -> String = { syncPreferences.getOrCreateDeviceId() },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val reminderRescheduler: (List<LeadEntity>) -> Unit = {}
) {
    private val syncMutex = Mutex()

    suspend fun sync(trigger: SyncTrigger, targetUid: String? = null): SyncRunResult {
        if (!syncMutex.tryLock()) {
            return SyncRunResult.Partial(
                SyncRunSummary(),
                "Sync execution already in progress"
            )
        }

        try {
            val user = currentUserProvider()
            val expectedUid = targetUid?.takeIf { it.isNotBlank() } ?: user?.uid ?: ""
            if (expectedUid.isBlank() || user?.isAnonymous == true) {
                return SyncRunResult.AuthRequired("Authenticated non-anonymous user required")
            }

            val activeFirebaseUid = try {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: user?.uid
            } catch (_: Exception) {
                user?.uid
            }
            if (activeFirebaseUid != expectedUid) {
                return SyncRunResult.AuthRequired("User authentication state changed during sync execution")
            }

            if (trigger != SyncTrigger.MANUAL && !syncPreferences.isAutomaticSyncEnabled()) {
                return SyncRunResult.AutomaticSyncDisabled("Automatic sync is currently disabled")
            }

            val now = clock()
            syncPreferences.setLastSyncAttemptAt(now)
            val scopeId = "leads:$expectedUid"
            val deviceId = deviceIdProvider()

            // Step 1: Recover stale IN_FLIGHT mutations
            syncDao.resetInFlightMutations(expectedUid, now)

            var pushedCount = 0
            var pushConflictsCount = 0
            var quarantinedCount = 0

            // Step 2: Push Batch Outbox
            val batch = localStore.claimNextBatch(ownerUid = expectedUid, limit = 100, nowUtc = now)
            if (batch.isNotEmpty()) {
                val remoteMutations = mutableListOf<RemoteMutation>()

                for (mutation in batch) {
                    val meta = metadataDao.getByLeadId(mutation.entityId, expectedUid)
                    // Check if stale mutation
                    if (meta != null && mutation.localVersion < meta.localVersion) {
                        localStore.acknowledgeMutation(
                            ownerUid = expectedUid,
                            mutationId = mutation.mutationId,
                            leadId = mutation.entityId,
                            serverVersion = meta.serverVersion,
                            remoteUpdatedAt = meta.remoteUpdatedAt,
                            nowUtc = now
                        )
                        continue
                    }

                    if (mutation.operation == "UPSERT") {
                        val lead = leadDao.getLeadById(mutation.entityId, expectedUid)
                        if (lead != null) {
                            val payload = RemoteLeadMapper.leadToRemoteMap(lead, meta, deviceId)
                            remoteMutations.add(
                                RemoteMutation(
                                    mutationId = mutation.mutationId,
                                    entityId = mutation.entityId,
                                    operation = "UPSERT",
                                    localVersion = mutation.localVersion,
                                    serverVersion = meta?.serverVersion,
                                    payload = payload
                                )
                            )
                        } else if (meta?.deleted == true) {
                            val payload = RemoteLeadMapper.deleteTombstoneMap(
                                leadId = mutation.entityId,
                                deletedAtUtc = meta.deletedAt ?: now,
                                updatedAtUtc = meta.localUpdatedAt,
                                deviceId = deviceId,
                                serverVersion = meta.serverVersion
                            )
                            remoteMutations.add(
                                RemoteMutation(
                                    mutationId = mutation.mutationId,
                                    entityId = mutation.entityId,
                                    operation = "DELETE",
                                    localVersion = mutation.localVersion,
                                    serverVersion = meta.serverVersion,
                                    payload = payload
                                )
                            )
                        } else {
                            localStore.quarantineMutation(
                                ownerUid = expectedUid,
                                mutationId = mutation.mutationId,
                                errorClass = "MALFORMED_DATA",
                                errorMessage = "Lead missing for UPSERT mutation",
                                nowUtc = now
                            )
                            quarantinedCount++
                        }
                    } else if (mutation.operation == "DELETE") {
                        val payload = RemoteLeadMapper.deleteTombstoneMap(
                            leadId = mutation.entityId,
                            deletedAtUtc = meta?.deletedAt ?: now,
                            updatedAtUtc = meta?.localUpdatedAt ?: now,
                            deviceId = deviceId,
                            serverVersion = meta?.serverVersion
                        )
                        remoteMutations.add(
                            RemoteMutation(
                                mutationId = mutation.mutationId,
                                entityId = mutation.entityId,
                                operation = "DELETE",
                                localVersion = mutation.localVersion,
                                serverVersion = meta?.serverVersion,
                                payload = payload
                            )
                        )
                    }
                }

                if (remoteMutations.isNotEmpty()) {
                    val currentFirebaseUid = try {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: expectedUid
                    } catch (_: Exception) {
                        expectedUid
                    }
                    if (currentFirebaseUid != expectedUid) {
                        return SyncRunResult.AuthRequired("User state changed before remote push execution")
                    }
                    val pushResult = remoteDataSource.push(expectedUid, remoteMutations)
                    for (item in pushResult.itemResults) {
                        when (item.status) {
                            PushItemStatus.ACKNOWLEDGED -> {
                                localStore.acknowledgeMutation(
                                    ownerUid = expectedUid,
                                    mutationId = item.mutationId,
                                    leadId = item.entityId,
                                    serverVersion = item.newServerVersion,
                                    remoteUpdatedAt = item.remoteUpdatedAt,
                                    nowUtc = now
                                )
                                pushedCount++
                            }
                            PushItemStatus.CONFLICT -> {
                                pushConflictsCount++
                                val conflict = SyncConflictEntity(
                                    conflictId = UUID.randomUUID().toString(),
                                    ownerUid = expectedUid,
                                    entityType = "LEAD",
                                    entityId = item.entityId,
                                    localPayload = "{}",
                                    remotePayload = if (item.conflictRecord != null) RemoteLeadMapper.toConflictJson(item.conflictRecord) else "{}",
                                    winner = "REMOTE",
                                    reason = item.errorMessage ?: "Push conflict",
                                    detectedAtUtc = now
                                )
                                localStore.saveConflict(conflict)
                                localStore.failMutation(
                                    ownerUid = expectedUid,
                                    mutationId = item.mutationId,
                                    errorClass = "CONFLICT",
                                    errorMessage = item.errorMessage ?: "Conflict during push",
                                    nextAttemptAtUtc = now + 60_000L,
                                    nowUtc = now
                                )
                            }
                            PushItemStatus.RETRYABLE_FAILURE -> {
                                localStore.failMutation(
                                    ownerUid = expectedUid,
                                    mutationId = item.mutationId,
                                    errorClass = "NETWORK",
                                    errorMessage = item.errorMessage ?: "Temporary push error",
                                    nextAttemptAtUtc = now + 30_000L,
                                    nowUtc = now
                                )
                            }
                            PushItemStatus.PERMANENT_FAILURE,
                            PushItemStatus.MALFORMED_LOCAL_MUTATION -> {
                                localStore.quarantineMutation(
                                    ownerUid = expectedUid,
                                    mutationId = item.mutationId,
                                    errorClass = "MALFORMED_DATA",
                                    errorMessage = item.errorMessage ?: "Permanent push error",
                                    nowUtc = now
                                )
                                quarantinedCount++
                            }
                        }
                    }
                }
            }

            // Step 3: Pull Remote Pages
            var pulledCount = 0
            var deletedCount = 0
            var pullConflictsCount = 0

            val checkpoint = syncDao.getCheckpoint(scopeId)
            var cursor: String? = checkpoint?.remoteCursor

            while (true) {
                val currentPullFirebaseUid = try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: expectedUid
                } catch (_: Exception) {
                    expectedUid
                }
                if (currentPullFirebaseUid != expectedUid) {
                    return SyncRunResult.AuthRequired("User state changed before remote pull page processing")
                }
                val page = remoteDataSource.pull(expectedUid, cursor, pageSize = 100)
                val pageRecords = page.records

                val leadsToInsert = mutableListOf<LeadEntity>()
                val leadsToDeleteIds = mutableListOf<String>()
                val conflictsToSave = mutableListOf<SyncConflictEntity>()
                val ackMutationsInMerge = mutableListOf<String>()

                val allExistingLeads = leadDao.getAllLeadsList(expectedUid)

                for (record in pageRecords) {
                    if (record.schemaVersion > 1) {
                        quarantinedCount++
                        conflictsToSave.add(
                            SyncConflictEntity(
                                conflictId = UUID.randomUUID().toString(),
                                ownerUid = expectedUid,
                                entityType = "LEAD",
                                entityId = record.id,
                                localPayload = "{}",
                                remotePayload = RemoteLeadMapper.toConflictJson(record),
                                winner = "LOCAL",
                                reason = "Unsupported schema version ${record.schemaVersion}",
                                detectedAtUtc = now
                            )
                        )
                        continue
                    }

                    if (record.id.isBlank() || (!record.deleted && record.name.isBlank())) {
                        quarantinedCount++
                        conflictsToSave.add(
                            SyncConflictEntity(
                                conflictId = UUID.randomUUID().toString(),
                                ownerUid = expectedUid,
                                entityType = "LEAD",
                                entityId = record.id.ifBlank { "unknown_${UUID.randomUUID()}" },
                                localPayload = "{}",
                                remotePayload = RemoteLeadMapper.toConflictJson(record),
                                winner = "LOCAL",
                                reason = "Malformed remote record",
                                detectedAtUtc = now
                            )
                        )
                        continue
                    }

                    // Duplicate mobile check
                    if (!record.deleted && record.mobile.isNotBlank()) {
                        val normRemote = record.mobile.replace(Regex("[^0-9+]"), "")
                        val matchingLocal = allExistingLeads.firstOrNull {
                            it.id != record.id && it.mobile.replace(Regex("[^0-9+]"), "") == normRemote
                        }
                        if (matchingLocal != null) {
                            pullConflictsCount++
                            conflictsToSave.add(
                                SyncConflictEntity(
                                    conflictId = UUID.randomUUID().toString(),
                                    ownerUid = expectedUid,
                                    entityType = "LEAD",
                                    entityId = record.id,
                                    localPayload = RemoteLeadMapper.leadToJson(matchingLocal),
                                    remotePayload = RemoteLeadMapper.toConflictJson(record),
                                    winner = "LOCAL",
                                    reason = "Duplicate mobile number with existing local lead ${matchingLocal.id}",
                                    detectedAtUtc = now
                                )
                            )
                            continue
                        }
                    }

                    val localLead = leadDao.getLeadById(record.id, expectedUid)
                    val localMeta = metadataDao.getByLeadId(record.id, expectedUid)
                    val pendingMutations = syncDao.getPendingMutationsForEntity(expectedUid, "LEAD", record.id)
                    val hasLocalPending = pendingMutations.isNotEmpty() || (localMeta != null && localMeta.syncState == "PENDING")

                    if (record.deleted) {
                        if (!hasLocalPending) {
                            if (localLead != null) {
                                leadsToDeleteIds.add(record.id)
                                deletedCount++
                            }
                            metadataDao.insertOrUpdate(
                                LeadSyncMetadataEntity(
                                    ownerUid = expectedUid,
                                    leadId = record.id,
                                    localUpdatedAt = record.updatedAt,
                                    remoteUpdatedAt = record.updatedAt,
                                    syncState = "SYNCED",
                                    deleted = true,
                                    deletedAt = record.deletedAt ?: record.updatedAt,
                                    serverVersion = record.serverVersion,
                                    originDeviceId = record.deviceId
                                )
                            )
                        } else {
                            pullConflictsCount++
                            conflictsToSave.add(
                                SyncConflictEntity(
                                    conflictId = UUID.randomUUID().toString(),
                                    ownerUid = expectedUid,
                                    entityType = "LEAD",
                                    entityId = record.id,
                                    localPayload = if (localLead != null) RemoteLeadMapper.leadToJson(localLead, localMeta) else "{}",
                                    remotePayload = RemoteLeadMapper.toConflictJson(record),
                                    winner = "LOCAL",
                                    reason = "Remote tombstone ignored due to pending local mutation",
                                    detectedAtUtc = now
                                )
                            )
                        }
                    } else {
                        val isLocalDeleted = (localMeta != null && localMeta.deleted) || pendingMutations.any { it.operation == "DELETE" }
                        if (isLocalDeleted) {
                            // Local delete is pending or tombstoned. Do NOT resurrect remote lead.
                            continue
                        }
                        if (localLead == null && !hasLocalPending) {
                            val newLead = RemoteLeadMapper.remoteRecordToLeadEntity(record, expectedUid)
                            leadsToInsert.add(newLead)
                            metadataDao.insertOrUpdate(
                                LeadSyncMetadataEntity(
                                    ownerUid = expectedUid,
                                    leadId = record.id,
                                    localUpdatedAt = record.updatedAt,
                                    remoteUpdatedAt = record.updatedAt,
                                    syncState = "SYNCED",
                                    deleted = false,
                                    serverVersion = record.serverVersion,
                                    originDeviceId = record.deviceId,
                                    localVersion = localMeta?.localVersion ?: 1L
                                )
                            )
                            pulledCount++
                        } else if (!hasLocalPending) {
                            if ((record.serverVersion > (localMeta?.serverVersion ?: 0L)) || record.updatedAt > (localMeta?.localUpdatedAt ?: 0L)) {
                                val updatedLead = RemoteLeadMapper.remoteRecordToLeadEntity(record, expectedUid)
                                leadsToInsert.add(updatedLead)
                                metadataDao.insertOrUpdate(
                                    LeadSyncMetadataEntity(
                                        ownerUid = expectedUid,
                                        leadId = record.id,
                                        localUpdatedAt = record.updatedAt,
                                        remoteUpdatedAt = record.updatedAt,
                                        syncState = "SYNCED",
                                        deleted = false,
                                        serverVersion = record.serverVersion,
                                        originDeviceId = record.deviceId,
                                        localVersion = localMeta?.localVersion ?: 1L
                                    )
                                )
                                pulledCount++
                            }
                        } else {
                            val remoteServerVersion = record.serverVersion
                            val baseServerVersion = localMeta?.serverVersion ?: 0L

                            if (remoteServerVersion > baseServerVersion) {
                                pullConflictsCount++
                                val remoteWins = when {
                                    record.updatedAt > (localMeta?.localUpdatedAt ?: 0L) -> true
                                    (localMeta?.localUpdatedAt ?: 0L) > record.updatedAt -> false
                                    record.serverVersion > (localMeta?.serverVersion ?: 0L) -> true
                                    (localMeta?.serverVersion ?: 0L) > record.serverVersion -> false
                                    else -> record.deviceId >= (localMeta?.originDeviceId ?: "")
                                }

                                val conflict = SyncConflictEntity(
                                    conflictId = UUID.randomUUID().toString(),
                                    ownerUid = expectedUid,
                                    entityType = "LEAD",
                                    entityId = record.id,
                                    localPayload = if (localLead != null) RemoteLeadMapper.leadToJson(localLead, localMeta) else "{}",
                                    remotePayload = RemoteLeadMapper.toConflictJson(record),
                                    winner = if (remoteWins) "REMOTE" else "LOCAL",
                                    reason = if (remoteWins) "Remote edit newer" else "Local edit newer",
                                    detectedAtUtc = now
                                )
                                conflictsToSave.add(conflict)

                                if (remoteWins) {
                                    val updatedLead = RemoteLeadMapper.remoteRecordToLeadEntity(record, expectedUid)
                                    leadsToInsert.add(updatedLead)
                                    metadataDao.insertOrUpdate(
                                        LeadSyncMetadataEntity(
                                            ownerUid = expectedUid,
                                            leadId = record.id,
                                            localUpdatedAt = record.updatedAt,
                                            remoteUpdatedAt = record.updatedAt,
                                            syncState = "SYNCED",
                                            deleted = false,
                                            serverVersion = record.serverVersion,
                                            originDeviceId = record.deviceId,
                                            localVersion = (localMeta?.localVersion ?: 1L) + 1L
                                        )
                                    )
                                    for (m in pendingMutations) {
                                        ackMutationsInMerge.add(m.mutationId)
                                    }
                                    pulledCount++
                                }
                            }
                        }
                    }
                }

                localStore.commitRemotePage(
                    ownerUid = expectedUid,
                    scopeId = scopeId,
                    remoteCursor = page.nextCursor,
                    lastSuccessfulSyncAtUtc = now,
                    conflicts = conflictsToSave,
                    ackMutationIds = ackMutationsInMerge,
                    localMergeAction = {
                        for (delId in leadsToDeleteIds) {
                            leadDao.deleteLeadById(delId, expectedUid)
                        }
                        if (leadsToInsert.isNotEmpty()) {
                            leadDao.insertLeads(leadsToInsert)
                        }
                    },
                    nowUtc = now
                )

                if (leadsToInsert.isNotEmpty() || leadsToDeleteIds.isNotEmpty()) {
                    val updatedLeads = leadDao.getAllLeadsList(expectedUid)
                    reminderRescheduler(updatedLeads)
                }

                cursor = page.nextCursor
                if (!page.hasMore || cursor == null) {
                    break
                }
            }

            // Step 4: Clear checkpoint cursor after full scan completes
            syncDao.commitCheckpointCursor(scopeId, null, now, now)
            syncPreferences.setLastSuccessfulSyncAt(now)
            syncPreferences.clearLastSyncError()

            val pendingRemaining = syncDao.getPendingCount(expectedUid)
            val summary = SyncRunSummary(
                pushed = pushedCount,
                pulled = pulledCount,
                deleted = deletedCount,
                conflicts = pushConflictsCount + pullConflictsCount,
                quarantined = quarantinedCount,
                pendingRemaining = pendingRemaining
            )

            return if (quarantinedCount > 0) {
                SyncRunResult.Partial(summary, "Completed with $quarantinedCount quarantined item(s)")
            } else {
                SyncRunResult.Success(summary)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Sync error occurred"
            syncPreferences.setLastSyncError(msg)
            return SyncRunResult.RetryableFailure(SyncErrorClass.NETWORK, msg)
        } finally {
            syncMutex.unlock()
        }
    }
}
