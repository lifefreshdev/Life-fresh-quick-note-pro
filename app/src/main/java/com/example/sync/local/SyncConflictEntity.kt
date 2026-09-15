package com.example.sync.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey val conflictId: String,
    val ownerUid: String = "",
    val entityType: String,
    val entityId: String,
    val localPayload: String,
    val remotePayload: String,
    val winner: String,
    val reason: String,
    val detectedAtUtc: Long,
    val resolved: Boolean = false,
    val resolvedAtUtc: Long? = null
)
