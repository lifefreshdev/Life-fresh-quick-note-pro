package com.example.sync.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_checkpoint")
data class SyncCheckpointEntity(
    @PrimaryKey val scopeId: String,
    val remoteCursor: String? = null,
    val lastRunId: String? = null,
    val lastRunStartedAtUtc: Long = 0L,
    val lastRunCompletedAtUtc: Long = 0L,
    val lastSuccessfulSyncAtUtc: Long = 0L,
    val lastPhase: String = "IDLE",
    val lastMessage: String? = null,
    val schemaVersion: Int = 1
)
