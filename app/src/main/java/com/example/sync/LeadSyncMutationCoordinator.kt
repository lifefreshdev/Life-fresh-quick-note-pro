package com.example.sync

import android.content.Context
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.database.LeadDao
import com.example.data.database.LeadEntity
import com.example.data.database.LeadSyncMetadataDao
import com.example.data.database.LeadSyncMetadataEntity
import com.example.sync.local.SyncDao
import com.example.sync.local.SyncOutboxEntity
import java.security.MessageDigest
import java.util.UUID

enum class LeadWriteOrigin {
    LOCAL_USER,
    LOCAL_AI,
    LOCAL_IMPORT,
    SYSTEM_REMINDER,
    REMOTE_SYNC,
    REMOTE_RESTORE
}

class LeadSyncMutationCoordinator(
    private val context: Context,
    private val database: AppDatabase,
    private val leadDao: LeadDao,
    private val metadataDao: LeadSyncMetadataDao,
    private val syncDao: SyncDao,
    private val syncPreferences: SyncPreferences = SyncPreferences(context),
    private val currentUserProvider: () -> SyncUser? = {
        try {
            val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            fbUser?.let { SyncUser(uid = it.uid, isAnonymous = it.isAnonymous) }
        } catch (_: Exception) {
            null
        }
    }
) {

    private fun isGuestUser(ownerUid: String): Boolean {
        val user = currentUserProvider()
        val activeFirebaseUid = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
        return user == null || user.isAnonymous || user.uid.isBlank() || ownerUid != user.uid || (activeFirebaseUid != null && activeFirebaseUid != ownerUid)
    }

    suspend fun upsertLead(lead: LeadEntity, origin: LeadWriteOrigin = LeadWriteOrigin.LOCAL_USER) {
        require(lead.ownerUid.isNotBlank()) { "Cannot upsert lead with blank ownerUid" }
        val now = System.currentTimeMillis()
        val isGuest = isGuestUser(lead.ownerUid)

        val isRemote = origin == LeadWriteOrigin.REMOTE_SYNC || origin == LeadWriteOrigin.REMOTE_RESTORE

        if (isGuest) {
            if (isRemote) {
                leadDao.insertLeadSync(lead)
            } else {
                leadDao.insertLead(lead)
            }
            return
        }

        database.withTransaction {
            val existingMeta = metadataDao.getByLeadId(lead.id, lead.ownerUid)
            val pendingMutations = syncDao.getPendingMutationsForEntity(lead.ownerUid, "LEAD", lead.id)
            val isLocalDeleted = (existingMeta != null && existingMeta.deleted) || pendingMutations.any { it.operation == "DELETE" }
            if (isRemote && isLocalDeleted) {
                return@withTransaction
            }

            val hasLocalPendingUpsert = (existingMeta != null && existingMeta.syncState == "PENDING" && !existingMeta.deleted) || pendingMutations.any { it.operation == "UPSERT" }
            if (origin == LeadWriteOrigin.REMOTE_RESTORE && hasLocalPendingUpsert) {
                return@withTransaction
            }

            val newVersion = if (isRemote) {
                existingMeta?.localVersion ?: 1L
            } else if (existingMeta != null) {
                existingMeta.localVersion + 1L
            } else {
                1L
            }

            if (isRemote) {
                leadDao.insertLeadSync(lead)
            } else {
                leadDao.insertLead(lead)
            }

            val deviceId = syncPreferences.getOrCreateDeviceId()
            val targetSyncState = if (isRemote) "SYNCED" else "PENDING"
            val targetLocalUpdatedAt = if (origin == LeadWriteOrigin.REMOTE_RESTORE) (lead.timestamp.takeIf { it > 0L } ?: now) else now
            val targetRemoteUpdatedAt = if (isRemote) (lead.timestamp.takeIf { it > 0L } ?: now) else existingMeta?.remoteUpdatedAt

            if (existingMeta != null) {
                metadataDao.insertOrUpdate(
                    existingMeta.copy(
                        ownerUid = lead.ownerUid,
                        localVersion = newVersion,
                        localUpdatedAt = targetLocalUpdatedAt,
                        remoteUpdatedAt = targetRemoteUpdatedAt,
                        syncState = targetSyncState,
                        deleted = false,
                        deletedAt = null,
                        lastError = null,
                        retryCount = if (isRemote) 0 else existingMeta.retryCount,
                        serverVersion = if (isRemote) (existingMeta.serverVersion ?: 1L) else existingMeta.serverVersion,
                        originDeviceId = existingMeta.originDeviceId.ifBlank { deviceId }
                    )
                )
            } else {
                metadataDao.insertOrUpdate(
                    LeadSyncMetadataEntity(
                        ownerUid = lead.ownerUid,
                        leadId = lead.id,
                        localUpdatedAt = targetLocalUpdatedAt,
                        remoteUpdatedAt = targetRemoteUpdatedAt,
                        syncState = targetSyncState,
                        deleted = false,
                        deletedAt = null,
                        lastError = null,
                        retryCount = 0,
                        localVersion = newVersion,
                        serverVersion = if (isRemote) 1L else null,
                        originDeviceId = deviceId
                    )
                )
            }

            if (!isRemote) {
                val payloadHash = computePayloadHash(lead, newVersion)
                val mutation = SyncOutboxEntity(
                    mutationId = UUID.randomUUID().toString(),
                    ownerUid = lead.ownerUid,
                    entityType = "LEAD",
                    entityId = lead.id,
                    operation = "UPSERT",
                    localVersion = newVersion,
                    payloadHash = payloadHash,
                    state = "PENDING",
                    attemptCount = 0,
                    createdAtUtc = now,
                    updatedAtUtc = now
                )
                syncDao.insertMutation(mutation)
            }
        }

        if (!isRemote && syncPreferences.isAutomaticSyncEnabled()) {
            try {
                SyncScheduler.enqueueMutationSync(context, lead.ownerUid)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun upsertLeads(leads: List<LeadEntity>, origin: LeadWriteOrigin = LeadWriteOrigin.LOCAL_USER) {
        for (lead in leads) {
            upsertLead(lead, origin)
        }
    }

    suspend fun deleteLead(leadId: String, ownerUid: String, origin: LeadWriteOrigin = LeadWriteOrigin.LOCAL_USER) {
        require(ownerUid.isNotBlank()) { "Cannot delete lead with blank ownerUid" }
        val now = System.currentTimeMillis()
        val isGuest = isGuestUser(ownerUid)

        if (isGuest) {
            leadDao.deleteLeadById(leadId, ownerUid)
            return
        }

        val isRemote = origin == LeadWriteOrigin.REMOTE_SYNC || origin == LeadWriteOrigin.REMOTE_RESTORE

        database.withTransaction {
            val existingMeta = metadataDao.getByLeadId(leadId, ownerUid)
            val newVersion = if (isRemote) {
                existingMeta?.localVersion ?: 1L
            } else {
                (existingMeta?.localVersion ?: 0L) + 1L
            }
            val deviceId = syncPreferences.getOrCreateDeviceId()
            val targetSyncState = if (isRemote) "SYNCED" else "PENDING"

            if (existingMeta != null) {
                metadataDao.insertOrUpdate(
                    existingMeta.copy(
                        ownerUid = ownerUid,
                        deleted = true,
                        deletedAt = now,
                        localUpdatedAt = now,
                        syncState = targetSyncState,
                        localVersion = newVersion,
                        originDeviceId = existingMeta.originDeviceId.ifBlank { deviceId }
                    )
                )
            } else {
                metadataDao.insertOrUpdate(
                    LeadSyncMetadataEntity(
                        ownerUid = ownerUid,
                        leadId = leadId,
                        localUpdatedAt = now,
                        deleted = true,
                        deletedAt = now,
                        syncState = targetSyncState,
                        localVersion = newVersion,
                        originDeviceId = deviceId
                    )
                )
            }

            if (!isRemote) {
                val payloadHash = computeDeleteHash(leadId, ownerUid, newVersion)
                val mutation = SyncOutboxEntity(
                    mutationId = UUID.randomUUID().toString(),
                    ownerUid = ownerUid,
                    entityType = "LEAD",
                    entityId = leadId,
                    operation = "DELETE",
                    localVersion = newVersion,
                    payloadHash = payloadHash,
                    state = "PENDING",
                    attemptCount = 0,
                    createdAtUtc = now,
                    updatedAtUtc = now
                )
                syncDao.insertMutation(mutation)
            }

            leadDao.deleteLeadById(leadId, ownerUid)
        }

        if (!isRemote && syncPreferences.isAutomaticSyncEnabled()) {
            try {
                SyncScheduler.enqueueMutationSync(context, ownerUid)
            } catch (_: Exception) {
            }
        }
    }

    fun computePayloadHash(lead: LeadEntity, version: Long): String {
        val canonical = buildString {
            append("LEAD|")
            append(lead.ownerUid).append("|")
            append(lead.id).append("|")
            append(lead.name).append("|")
            append(lead.mobile).append("|")
            append(lead.diseases).append("|")
            append(lead.relation).append("|")
            append(lead.status).append("|")
            append(lead.notes).append("|")
            append(lead.reminderDate).append("|")
            append(lead.reminderTime).append("|")
            append(lead.reminderStatus).append("|")
            append(lead.archived).append("|")
            append(lead.lastCall).append("|")
            append(lead.timestamp).append("|")
            append(version)
        }
        return sha256(canonical)
    }

    fun computeDeleteHash(leadId: String, ownerUid: String, version: Long): String {
        val canonical = "DELETE|${ownerUid}|${leadId}|${version}"
        return sha256(canonical)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
