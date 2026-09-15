package com.example.sync.model

enum class SyncTrigger {
    MANUAL,
    LOCAL_MUTATION,
    PERIODIC,
    STARTUP
}

enum class SyncOperation {
    UPSERT,
    DELETE
}

enum class SyncMutationState {
    PENDING,
    IN_FLIGHT,
    ACKNOWLEDGED,
    FAILED,
    QUARANTINED
}

enum class SyncRunPhase {
    IDLE,
    BOOTSTRAP,
    PULL,
    PUSH,
    CLEANUP,
    COMPLETED,
    FAILED
}

enum class SyncErrorClass {
    NETWORK,
    AUTH,
    RATE_LIMIT,
    SERVER,
    MALFORMED_DATA,
    SCHEMA_MISMATCH,
    CONFLICT,
    UNKNOWN
}

data class SyncRunSummary(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val deleted: Int = 0,
    val conflicts: Int = 0,
    val quarantined: Int = 0,
    val pendingRemaining: Int = 0
)

sealed class SyncRunResult {
    data class Success(val summary: SyncRunSummary) : SyncRunResult()
    data class Partial(val summary: SyncRunSummary, val message: String) : SyncRunResult()
    data class RetryableFailure(val errorClass: SyncErrorClass, val message: String) : SyncRunResult()
    data class AuthRequired(val message: String = "Authentication required") : SyncRunResult()
    data class AutomaticSyncDisabled(val message: String = "Automatic sync is disabled") : SyncRunResult()
    data class PermanentFailure(val errorClass: SyncErrorClass, val message: String) : SyncRunResult()
}

data class RemoteLeadRecord(
    val id: String,
    val name: String,
    val mobile: String,
    val diseases: String = "[]",
    val otherDisease: String = "",
    val relation: String = "",
    val otherRelation: String = "",
    val status: String = "Pending",
    val reminderDate: String = "",
    val reminderTime: String = "",
    val reminderNote: String = "",
    val reminderStatus: String = "Pending",
    val notes: String = "",
    val archived: Boolean = false,
    val lastCall: String? = null,
    val timestamp: Long = 0L,
    val notesUpdatedAt: Long = 0L,
    val reminderUpdatedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
    val schemaVersion: Int = 1,
    val deviceId: String = "",
    val serverVersion: Long = 0L
)

data class RemoteMutation(
    val mutationId: String,
    val entityId: String,
    val operation: String,
    val localVersion: Long,
    val serverVersion: Long? = null,
    val payload: Map<String, Any?> = emptyMap()
)

enum class PushItemStatus {
    ACKNOWLEDGED,
    CONFLICT,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    MALFORMED_LOCAL_MUTATION
}

data class PushItemResult(
    val mutationId: String,
    val entityId: String,
    val status: PushItemStatus,
    val newServerVersion: Long? = null,
    val remoteUpdatedAt: Long? = null,
    val conflictRecord: RemoteLeadRecord? = null,
    val errorMessage: String? = null
)

data class PushBatchResult(
    val itemResults: List<PushItemResult> = emptyList()
)

data class PullPage(
    val records: List<RemoteLeadRecord> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)

data class RetryClassification(
    val isRetryable: Boolean,
    val errorClass: SyncErrorClass,
    val message: String
)
