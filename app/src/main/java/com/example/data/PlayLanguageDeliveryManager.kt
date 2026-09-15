package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Installation state for Google Play Additional Language Delivery.
 */
sealed class PlayLanguageInstallState {
    data object Idle : PlayLanguageInstallState()

    data class Pending(
        val sessionId: Int,
        val language: AppLanguage
    ) : PlayLanguageInstallState()

    data class Downloading(
        val sessionId: Int,
        val language: AppLanguage,
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long
    ) : PlayLanguageInstallState()

    data class Downloaded(
        val sessionId: Int,
        val language: AppLanguage
    ) : PlayLanguageInstallState()

    data class Installing(
        val sessionId: Int,
        val language: AppLanguage
    ) : PlayLanguageInstallState()

    data class Installed(
        val sessionId: Int,
        val language: AppLanguage
    ) : PlayLanguageInstallState()

    data class Failed(
        val sessionId: Int,
        val language: AppLanguage?,
        val errorCode: Int,
        val errorMessage: String
    ) : PlayLanguageInstallState()

    data class Canceled(
        val sessionId: Int,
        val language: AppLanguage?
    ) : PlayLanguageInstallState()

    data class RequiresUserConfirmation(
        val sessionId: Int,
        val language: AppLanguage
    ) : PlayLanguageInstallState()
}

/**
 * Dedicated Google Play Additional Language Delivery Manager.
 *
 * Implements native on-demand language delivery using SplitInstallManager and SplitCompat.
 * 
 * Distinct concepts:
 * 1. Bundled / Base Language Resources: Core XML resources shipped in base APK (en, hi, ur, ta).
 * 2. Installed Play Language Resources: Language splits downloaded and installed via Google Play.
 * 3. Application Language Preference: The user's persisted language choice in AppLanguageManager.
 */
open class PlayLanguageDeliveryManager(
    private val context: Context,
    val splitInstallManager: SplitInstallManager = SplitInstallManagerFactory.create(context.applicationContext)
) {
    companion object {
        private const val TAG = "PlayLanguageDelivery"

        @Volatile
        private var instance: PlayLanguageDeliveryManager? = null

        fun getInstance(context: Context): PlayLanguageDeliveryManager {
            return instance ?: synchronized(this) {
                instance ?: PlayLanguageDeliveryManager(context.applicationContext).also { instance = it }
            }
        }

        fun setInstanceForTesting(testInstance: PlayLanguageDeliveryManager?) {
            instance = testInstance
        }
    }

    private val activeSessionLanguages = ConcurrentHashMap<Int, AppLanguage>()
    private val _installState = MutableStateFlow<PlayLanguageInstallState>(PlayLanguageInstallState.Idle)
    val installState: StateFlow<PlayLanguageInstallState> = _installState.asStateFlow()

    private val stateListener = SplitInstallStateUpdatedListener { state ->
        handleSessionState(state)
    }

    init {
        try {
            splitInstallManager.registerListener(stateListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register SplitInstallStateUpdatedListener", e)
        }
    }

    /**
     * Unregisters the listener if the manager lifecycle terminates.
     */
    fun unregisterListener() {
        try {
            splitInstallManager.unregisterListener(stateListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to unregister SplitInstallStateUpdatedListener", e)
        }
    }

    /**
     * Maps an AppLanguage enum to its corresponding Java/Android Locale.
     * Hinglish is deprecated and strictly excluded.
     */
    fun getLocaleForLanguage(language: AppLanguage): Locale {
        return AppLanguageManager.getJavaLocale(language)
    }

    /**
     * Resolves a locale from language code, returning null if invalid or deprecated Hinglish.
     */
    fun getLocaleForCode(code: String?): Locale? {
        if (code == null || code.equals("hinglish", ignoreCase = true) || code.equals("hi-Latn", ignoreCase = true)) {
            return null
        }
        val language = AppLanguage.fromCode(code)
        return getLocaleForLanguage(language)
    }

    /**
     * Returns the set of language codes directly reported by Google Play Feature Delivery as installed.
     * Queries the official SplitInstallManager.installedLanguages API.
     */
    fun getInstalledPlayLanguages(): Set<String> {
        return try {
            splitInstallManager.installedLanguages
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to query installed languages from SplitInstallManager", e)
            emptySet()
        }
    }

    /**
     * Checks if a language split APK is installed via Play Feature Delivery.
     */
    fun isPlayLanguageInstalled(language: AppLanguage): Boolean {
        val installed = getInstalledPlayLanguages().map { it.lowercase() }.toSet()
        return installed.contains(language.code.lowercase()) || installed.contains(language.localeTag.lowercase())
    }

    /**
     * Checks if a language is available for immediate use without downloading.
     * The 4 core languages (English, Hindi, Urdu, Tamil) are bundled (isBundled = true).
     */
    fun isLanguageAvailable(language: AppLanguage): Boolean {
        return language.isBundled || isPlayLanguageInstalled(language)
    }

    /**
     * Requests on-demand installation of language resources from Google Play.
     */
    fun requestInstallLanguage(
        language: AppLanguage,
        onSuccess: (sessionId: Int) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ): Task<Int>? {
        val locale = getLocaleForLanguage(language)
        val request = SplitInstallRequest.newBuilder()
            .addLanguage(locale)
            .build()

        return try {
            val task = splitInstallManager.startInstall(request)
            task.addOnSuccessListener { sessionId ->
                activeSessionLanguages[sessionId] = language
                _installState.value = PlayLanguageInstallState.Pending(sessionId, language)
                onSuccess(sessionId)
            }.addOnFailureListener { exception ->
                val errorCode = if (exception is SplitInstallException) exception.errorCode else -1
                val message = if (exception is SplitInstallException) {
                    getErrorMessage(errorCode)
                } else {
                    exception.message ?: "Failed to start language installation"
                }
                _installState.value = PlayLanguageInstallState.Failed(
                    sessionId = -1,
                    language = language,
                    errorCode = errorCode,
                    errorMessage = message
                )
                onError(exception)
            }
            task
        } catch (e: Exception) {
            val message = e.message ?: "Failed to initiate language installation"
            _installState.value = PlayLanguageInstallState.Failed(
                sessionId = -1,
                language = language,
                errorCode = -1,
                errorMessage = message
            )
            onError(e)
            null
        }
    }

    /**
     * Requests deferred installation of language resources.
     */
    fun deferredInstallLanguage(language: AppLanguage): Task<Void>? {
        val locale = getLocaleForLanguage(language)
        return try {
            splitInstallManager.deferredLanguageInstall(listOf(locale))
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to schedule deferred language install for ${language.code}", e)
            null
        }
    }

    /**
     * Requests deferred uninstall of language resources.
     * Note: Must NEVER be called automatically when another language is selected.
     */
    fun deferredUninstallLanguage(language: AppLanguage): Task<Void>? {
        val locale = getLocaleForLanguage(language)
        return try {
            splitInstallManager.deferredLanguageUninstall(listOf(locale))
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to schedule deferred language uninstall for ${language.code}", e)
            null
        }
    }

    /**
     * Cancels an ongoing language installation session.
     */
    fun cancelInstall(sessionId: Int): Task<Void>? {
        return try {
            splitInstallManager.cancelInstall(sessionId)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to cancel install session $sessionId", e)
            null
        }
    }

    /**
     * Resets the install state back to Idle.
     */
    fun resetState() {
        _installState.value = PlayLanguageInstallState.Idle
    }

    /**
     * Completes post-install application of the newly installed language:
     * 1. Applies the language through AppLanguageManager
     * 2. Installs SplitCompat on the Activity
     * 3. Safely recreates the Activity to apply newly installed resources
     */
    fun applyInstalledLanguage(activity: Activity, language: AppLanguage) {
        AppLanguageManager.setLanguage(activity, language)
        try {
            SplitCompat.installActivity(activity)
        } catch (e: Throwable) {
            Log.w(TAG, "SplitCompat.installActivity failed or skipped", e)
        }
        activity.recreate()
    }

    /**
     * Handles session state updates received from SplitInstallManager.
     */
    fun handleSessionState(state: SplitInstallSessionState) {
        val sessionId = state.sessionId()
        val language = activeSessionLanguages[sessionId] ?: resolveLanguageFromState(state)

        when (state.status()) {
            SplitInstallSessionStatus.PENDING -> {
                if (language != null) {
                    _installState.value = PlayLanguageInstallState.Pending(sessionId, language)
                }
            }
            SplitInstallSessionStatus.DOWNLOADING -> {
                if (language != null) {
                    _installState.value = PlayLanguageInstallState.Downloading(
                        sessionId = sessionId,
                        language = language,
                        bytesDownloaded = state.bytesDownloaded(),
                        totalBytesToDownload = state.totalBytesToDownload()
                    )
                }
            }
            SplitInstallSessionStatus.DOWNLOADED -> {
                if (language != null) {
                    _installState.value = PlayLanguageInstallState.Downloaded(sessionId, language)
                }
            }
            SplitInstallSessionStatus.INSTALLING -> {
                if (language != null) {
                    _installState.value = PlayLanguageInstallState.Installing(sessionId, language)
                }
            }
            SplitInstallSessionStatus.INSTALLED -> {
                if (language != null) {
                    _installState.value = PlayLanguageInstallState.Installed(sessionId, language)
                }
                activeSessionLanguages.remove(sessionId)
            }
            SplitInstallSessionStatus.FAILED -> {
                val errorCode = state.errorCode()
                val message = getErrorMessage(errorCode)
                _installState.value = PlayLanguageInstallState.Failed(
                    sessionId = sessionId,
                    language = language,
                    errorCode = errorCode,
                    errorMessage = message
                )
                activeSessionLanguages.remove(sessionId)
            }
            SplitInstallSessionStatus.CANCELED -> {
                _installState.value = PlayLanguageInstallState.Canceled(
                    sessionId = sessionId,
                    language = language
                )
                activeSessionLanguages.remove(sessionId)
            }
            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                if (language != null) {
                    _installState.value = PlayLanguageInstallState.RequiresUserConfirmation(sessionId, language)
                }
            }
            else -> {
                // Ignore transient UNKNOWN, CANCELING states
            }
        }
    }

    private fun resolveLanguageFromState(state: SplitInstallSessionState): AppLanguage? {
        val languages = state.languages()
        if (!languages.isNullOrEmpty()) {
            val code = languages.first()
            return AppLanguage.fromCode(code)
        }
        val modules = state.moduleNames()
        if (!modules.isNullOrEmpty()) {
            val code = modules.first()
            return AppLanguage.fromCode(code)
        }
        return null
    }

    /**
     * Translates SplitInstallErrorCode into a human-readable, safe error message.
     */
    fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SplitInstallErrorCode.NO_ERROR -> "No error"
            SplitInstallErrorCode.API_NOT_AVAILABLE -> "Google Play Feature Delivery API is not available on this device."
            SplitInstallErrorCode.NETWORK_ERROR -> "Network error downloading language resources from Google Play."
            SplitInstallErrorCode.INSUFFICIENT_STORAGE -> "Insufficient storage space to download language resources."
            SplitInstallErrorCode.ACCESS_DENIED -> "Access denied to Google Play language delivery."
            SplitInstallErrorCode.MODULE_UNAVAILABLE -> "Requested language resource module is unavailable on Google Play."
            SplitInstallErrorCode.INVALID_REQUEST -> "Invalid language delivery request."
            SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED -> "Active download sessions limit exceeded. Please wait and try again."
            SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION -> "Installation incompatible with an existing session."
            SplitInstallErrorCode.SERVICE_DIED -> "Google Play service died during language delivery."
            SplitInstallErrorCode.INTERNAL_ERROR -> "Google Play internal error occurred."
            SplitInstallErrorCode.SESSION_NOT_FOUND -> "Installation session was not found."
            else -> "Google Play language delivery error (Code: $errorCode)"
        }
    }
}
