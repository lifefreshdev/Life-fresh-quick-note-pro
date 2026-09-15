package com.example.sync

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class SyncPreferences(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun isAutomaticSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTOMATIC_SYNC_ENABLED, false)
    }

    fun setAutomaticSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOMATIC_SYNC_ENABLED, enabled).apply()
    }

    fun getLastSuccessfulSyncAt(): Long {
        return prefs.getLong(KEY_LAST_SUCCESSFUL_SYNC_AT, 0L)
    }

    fun setLastSuccessfulSyncAt(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SUCCESSFUL_SYNC_AT, timestamp).apply()
    }

    fun getLastSyncAttemptAt(): Long {
        return prefs.getLong(KEY_LAST_SYNC_ATTEMPT_AT, 0L)
    }

    fun setLastSyncAttemptAt(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_ATTEMPT_AT, timestamp).apply()
    }

    @Synchronized
    fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun isInitialRestoreCompleted(uid: String): Boolean {
        if (uid.isBlank()) return false
        return prefs.getBoolean(KEY_PREFIX_INITIAL_RESTORE + uid, false)
    }

    fun setInitialRestoreCompleted(uid: String, completed: Boolean) {
        if (uid.isBlank()) return
        prefs.edit().putBoolean(KEY_PREFIX_INITIAL_RESTORE + uid, completed).apply()
    }

    fun getLastSyncError(): String? {
        return prefs.getString(KEY_LAST_SYNC_ERROR, null)
    }

    fun setLastSyncError(message: String?) {
        if (message == null) {
            clearLastSyncError()
        } else {
            prefs.edit().putString(KEY_LAST_SYNC_ERROR, message).apply()
        }
    }

    fun clearLastSyncError() {
        prefs.edit().remove(KEY_LAST_SYNC_ERROR).apply()
    }

    /** Clears sync state that belongs to the account being permanently deleted. */
    fun clearAccountState(ownerUid: String) {
        if (ownerUid.isBlank()) return
        prefs.edit()
            .remove(KEY_PREFIX_INITIAL_RESTORE + ownerUid)
            .remove(KEY_AUTOMATIC_SYNC_ENABLED)
            .remove(KEY_LAST_SUCCESSFUL_SYNC_AT)
            .remove(KEY_LAST_SYNC_ATTEMPT_AT)
            .remove(KEY_LAST_SYNC_ERROR)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "lifefresh_sync_preferences"
        private const val KEY_AUTOMATIC_SYNC_ENABLED = "automatic_sync_enabled"
        private const val KEY_LAST_SUCCESSFUL_SYNC_AT = "last_successful_sync_at"
        private const val KEY_LAST_SYNC_ATTEMPT_AT = "last_sync_attempt_at"
        private const val KEY_DEVICE_ID = "sync_device_id"
        private const val KEY_PREFIX_INITIAL_RESTORE = "initial_restore_completed_"
        private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
    }
}
