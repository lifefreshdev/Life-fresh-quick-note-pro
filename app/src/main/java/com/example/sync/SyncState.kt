package com.example.sync

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Synced(val lastSyncAt: Long) : SyncState
    data object Offline : SyncState
    data class Failed(
        val message: String,
        val isRetryable: Boolean = true
    ) : SyncState
    data object SignInRequired : SyncState
    data object AutomaticSyncDisabled : SyncState
}
