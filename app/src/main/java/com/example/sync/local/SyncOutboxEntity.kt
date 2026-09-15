package com.example.sync.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["state"]),
        Index(value = ["entityType", "entityId"]),
        Index(value = ["nextAttemptAtUtc"]),
        Index(value = ["createdAtUtc"]),
        Index(value = ["ownerUid"]),
        Index(value = ["ownerUid", "entityType", "entityId", "operation", "localVersion"], unique = true)
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey val mutationId: String,
    val ownerUid: String = "",
    val entityType: String,
    val entityId: String,
    val operation: String,
    val localVersion: Long,
    val payloadHash: String,
    val state: String = "PENDING",
    val attemptCount: Int = 0,
    val createdAtUtc: Long,
    val updatedAtUtc: Long,
    val nextAttemptAtUtc: Long? = null,
    val lastErrorClass: String? = null,
    val lastErrorMessage: String? = null
)
