package com.example.data.database

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithMessages(
    @Embedded val session: AIChatSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val messages: List<AIChatMessageEntity>
)
