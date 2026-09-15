package com.example.data

import android.content.Context
import android.content.SharedPreferences

object ActiveAccountStore {
    private const val PREFS_NAME = "lifefresh_active_account_prefs"
    private const val KEY_ACTIVE_UID = "active_owner_uid"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getActiveUid(context: Context): String {
        return getPrefs(context).getString(KEY_ACTIVE_UID, "") ?: ""
    }

    fun setActiveUid(context: Context, uid: String) {
        if (uid.isBlank()) {
            clearActiveUid(context)
        } else {
            getPrefs(context).edit().putString(KEY_ACTIVE_UID, uid).apply()
        }
    }

    fun clearActiveUid(context: Context) {
        getPrefs(context).edit().remove(KEY_ACTIVE_UID).apply()
    }
}
