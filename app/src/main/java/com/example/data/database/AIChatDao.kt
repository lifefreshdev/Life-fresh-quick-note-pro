package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AIChatDao {
    @Transaction
    @Query("SELECT * FROM ai_chat_sessions WHERE ownerUid = :ownerUid AND ownerUid != '' ORDER BY isPinned DESC, updatedTimestamp DESC")
    fun getAllSessionsWithMessages(ownerUid: String): Flow<List<SessionWithMessages>>

    @Query("SELECT * FROM ai_chat_sessions WHERE ownerUid = :ownerUid AND ownerUid != '' ORDER BY isPinned DESC, updatedTimestamp DESC")
    fun getAllSessions(ownerUid: String): Flow<List<AIChatSessionEntity>>

    @Query("SELECT * FROM ai_chat_sessions WHERE ownerUid = :ownerUid AND ownerUid != ''")
    suspend fun getAllSessionsList(ownerUid: String): List<AIChatSessionEntity>

    @Query("SELECT * FROM ai_chat_sessions WHERE ownerUid = :ownerUid AND id = :sessionId AND ownerUid != '' LIMIT 1")
    suspend fun getSessionById(ownerUid: String, sessionId: String): AIChatSessionEntity?

    @Query("SELECT * FROM ai_chat_messages WHERE ownerUid = :ownerUid AND sessionId = :sessionId AND ownerUid != '' ORDER BY timestamp ASC")
    fun getMessagesForSession(ownerUid: String, sessionId: String): Flow<List<AIChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages WHERE ownerUid = :ownerUid AND sessionId = :sessionId AND ownerUid != '' ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionList(ownerUid: String, sessionId: String): List<AIChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AIChatSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AIChatMessageEntity)

    @Query("DELETE FROM ai_chat_sessions WHERE ownerUid = :ownerUid AND id = :sessionId")
    suspend fun deleteSessionById(ownerUid: String, sessionId: String)

    @Query("UPDATE ai_chat_sessions SET isPinned = :isPinned, updatedTimestamp = :updatedTimestamp WHERE ownerUid = :ownerUid AND id = :sessionId")
    suspend fun updateSessionPinStatus(ownerUid: String, sessionId: String, isPinned: Boolean, updatedTimestamp: Long)

    @Query("UPDATE ai_chat_sessions SET title = :title, updatedTimestamp = :updatedTimestamp WHERE ownerUid = :ownerUid AND id = :sessionId")
    suspend fun updateSessionTitle(ownerUid: String, sessionId: String, title: String, updatedTimestamp: Long)

    @Query("UPDATE ai_chat_sessions SET updatedTimestamp = :updatedTimestamp WHERE ownerUid = :ownerUid AND id = :sessionId")
    suspend fun updateSessionTimestamp(ownerUid: String, sessionId: String, updatedTimestamp: Long)

    @Query("DELETE FROM ai_chat_sessions WHERE ownerUid = :ownerUid")
    suspend fun clearSessionsForUser(ownerUid: String)

    @Query("DELETE FROM ai_chat_messages WHERE ownerUid = :ownerUid")
    suspend fun clearMessagesForUser(ownerUid: String)
}
