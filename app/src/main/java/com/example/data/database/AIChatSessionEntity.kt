package com.example.data.database

import androidx.room.Entity

@Entity(
    tableName = "ai_chat_sessions",
    primaryKeys = ["ownerUid", "id"]
)
data class AIChatSessionEntity(
    val ownerUid: String = "",
    val id: String,
    val title: String,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)
