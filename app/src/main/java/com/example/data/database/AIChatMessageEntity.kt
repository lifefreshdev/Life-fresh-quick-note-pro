package com.example.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "ai_chat_messages",
    primaryKeys = ["ownerUid", "id"],
    foreignKeys = [
        ForeignKey(
            entity = AIChatSessionEntity::class,
            parentColumns = ["ownerUid", "id"],
            childColumns = ["ownerUid", "sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ownerUid", "sessionId"]),
        Index(value = ["sessionId"])
    ]
)
data class AIChatMessageEntity(
    val ownerUid: String = "",
    val id: String,
    val sessionId: String,
    val text: String,
    val sender: String, // "USER" or "AI"
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isOfflineWarning: Boolean = false,
    val isConfirmation: Boolean = false,
    val actionCardType: String? = null
)
