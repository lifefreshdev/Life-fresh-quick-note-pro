package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "lead_sync_metadata",
    primaryKeys = ["ownerUid", "leadId"]
)
data class LeadSyncMetadataEntity(
    val ownerUid: String = "",
    val leadId: String,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long? = null,
    val syncState: String = "PENDING",
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
    val lastError: String? = null,
    val retryCount: Int = 0,
    val localVersion: Long = 0L,
    val serverVersion: Long? = null,
    val originDeviceId: String = ""
)
