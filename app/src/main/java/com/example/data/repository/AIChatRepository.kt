package com.example.data.repository

import com.example.data.database.AIChatDao
import com.example.data.database.AIChatSessionEntity
import com.example.data.database.AIChatMessageEntity
import com.example.data.database.SessionWithMessages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class AIChatRepository(private val aiChatDao: AIChatDao) {
    fun getAllSessions(ownerUid: String): Flow<List<AIChatSessionEntity>> {
        if (ownerUid.isBlank()) return flowOf(emptyList())
        return aiChatDao.getAllSessions(ownerUid)
    }

    fun getAllSessionsWithMessages(ownerUid: String): Flow<List<SessionWithMessages>> {
        if (ownerUid.isBlank()) return flowOf(emptyList())
        return aiChatDao.getAllSessionsWithMessages(ownerUid)
    }

    fun getMessagesForSession(ownerUid: String, sessionId: String): Flow<List<AIChatMessageEntity>> {
        if (ownerUid.isBlank() || sessionId.isBlank()) return flowOf(emptyList())
        return aiChatDao.getMessagesForSession(ownerUid, sessionId)
    }

    suspend fun getMessagesForSessionList(ownerUid: String, sessionId: String): List<AIChatMessageEntity> {
        if (ownerUid.isBlank() || sessionId.isBlank()) return emptyList()
        return aiChatDao.getMessagesForSessionList(ownerUid, sessionId)
    }

    suspend fun getSessionById(ownerUid: String, sessionId: String): AIChatSessionEntity? {
        if (ownerUid.isBlank() || sessionId.isBlank()) return null
        return aiChatDao.getSessionById(ownerUid, sessionId)
    }

    suspend fun insertSession(session: AIChatSessionEntity) {
        if (session.ownerUid.isBlank()) return
        aiChatDao.insertSession(session)
    }

    suspend fun insertMessage(message: AIChatMessageEntity) {
        if (message.ownerUid.isBlank()) return
        aiChatDao.insertMessage(message)
    }

    suspend fun deleteSessionById(ownerUid: String, sessionId: String) {
        if (ownerUid.isBlank() || sessionId.isBlank()) return
        aiChatDao.deleteSessionById(ownerUid, sessionId)
    }

    suspend fun updateSessionPinStatus(ownerUid: String, sessionId: String, isPinned: Boolean) {
        if (ownerUid.isBlank() || sessionId.isBlank()) return
        aiChatDao.updateSessionPinStatus(ownerUid, sessionId, isPinned, System.currentTimeMillis())
    }

    suspend fun updateSessionTitle(ownerUid: String, sessionId: String, title: String) {
        if (ownerUid.isBlank() || sessionId.isBlank()) return
        aiChatDao.updateSessionTitle(ownerUid, sessionId, title, System.currentTimeMillis())
    }

    suspend fun updateSessionTimestamp(ownerUid: String, sessionId: String) {
        if (ownerUid.isBlank() || sessionId.isBlank()) return
        aiChatDao.updateSessionTimestamp(ownerUid, sessionId, System.currentTimeMillis())
    }

    suspend fun clearChatHistoryForUser(ownerUid: String) {
        if (ownerUid.isBlank()) return
        aiChatDao.clearMessagesForUser(ownerUid)
        aiChatDao.clearSessionsForUser(ownerUid)
    }
}
