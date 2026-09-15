package com.example.data

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.common.IntentSenderForResultStarter
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

class TestSplitInstallManager : SplitInstallManager {
    val listeners = mutableListOf<SplitInstallStateUpdatedListener>()
    val installedLanguagesSet = mutableSetOf<String>()
    var startInstallResult: Task<Int> = Tasks.forResult(101)
    var cancelInstallResult: Task<Void> = Tasks.forResult(null)
    var deferredLanguageInstallResult: Task<Void> = Tasks.forResult(null)
    var deferredLanguageUninstallResult: Task<Void> = Tasks.forResult(null)

    override fun registerListener(listener: SplitInstallStateUpdatedListener) {
        listeners.add(listener)
    }

    override fun unregisterListener(listener: SplitInstallStateUpdatedListener) {
        listeners.remove(listener)
    }

    override fun getInstalledLanguages(): Set<String> = installedLanguagesSet

    override fun getInstalledModules(): Set<String> = emptySet()

    override fun startInstall(request: SplitInstallRequest): Task<Int> = startInstallResult

    override fun cancelInstall(sessionId: Int): Task<Void> = cancelInstallResult

    override fun deferredInstall(modules: List<String>): Task<Void> = Tasks.forResult(null)

    override fun deferredLanguageInstall(languages: List<Locale>): Task<Void> = deferredLanguageInstallResult

    override fun deferredLanguageUninstall(languages: List<Locale>): Task<Void> = deferredLanguageUninstallResult

    override fun deferredUninstall(modules: List<String>): Task<Void> = Tasks.forResult(null)

    override fun getSessionState(sessionId: Int): Task<SplitInstallSessionState> = Tasks.forResult(null)

    override fun getSessionStates(): Task<List<SplitInstallSessionState>> = Tasks.forResult(emptyList())

    override fun startConfirmationDialogForResult(
        state: SplitInstallSessionState,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean = false

    override fun startConfirmationDialogForResult(
        state: SplitInstallSessionState,
        activity: Activity,
        requestCode: Int
    ): Boolean = false

    override fun startConfirmationDialogForResult(
        state: SplitInstallSessionState,
        starter: IntentSenderForResultStarter,
        requestCode: Int
    ): Boolean = false

    override fun zza(listener: SplitInstallStateUpdatedListener) {}

    override fun zzb(listener: SplitInstallStateUpdatedListener) {}
}

@RunWith(RobolectricTestRunner::class)
class PlayLanguageDeliveryManagerTest {

    private lateinit var context: Context
    private lateinit var testSplitManager: TestSplitInstallManager
    private lateinit var manager: PlayLanguageDeliveryManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testSplitManager = TestSplitInstallManager()
        manager = PlayLanguageDeliveryManager(context, testSplitManager)
        PlayLanguageDeliveryManager.setInstanceForTesting(manager)
    }

    @Test
    fun testLanguageToLocaleMapping() {
        val enLocale = manager.getLocaleForLanguage(AppLanguage.ENGLISH)
        val hiLocale = manager.getLocaleForLanguage(AppLanguage.HINDI)
        val urLocale = manager.getLocaleForLanguage(AppLanguage.URDU)
        val taLocale = manager.getLocaleForLanguage(AppLanguage.TAMIL)

        assertEquals("en", enLocale.language)
        assertEquals("hi", hiLocale.language)
        assertEquals("ur", urLocale.language)
        assertEquals("ta", taLocale.language)
    }

    @Test
    fun testHinglishStrictlyExcluded() {
        assertNull(manager.getLocaleForCode("hinglish"))
        assertNull(manager.getLocaleForCode("HINGLISH"))
        assertNull(manager.getLocaleForCode("hi-Latn"))
        assertNull(manager.getLocaleForCode(null))

        // Valid languages return non-null
        assertNotNull(manager.getLocaleForCode("en"))
        assertNotNull(manager.getLocaleForCode("hi"))
        assertNotNull(manager.getLocaleForCode("ur"))
        assertNotNull(manager.getLocaleForCode("ta"))
    }

    @Test
    fun testCoreLanguagesAreBundledAndAvailableWithoutDownload() {
        // The 4 core languages are bundled in the base APK (isBundled = true)
        assertTrue(manager.isLanguageAvailable(AppLanguage.ENGLISH))
        assertTrue(manager.isLanguageAvailable(AppLanguage.HINDI))
        assertTrue(manager.isLanguageAvailable(AppLanguage.URDU))
        assertTrue(manager.isLanguageAvailable(AppLanguage.TAMIL))

        assertTrue(AppLanguageManager.isLanguageAvailable(context, AppLanguage.ENGLISH))
        assertTrue(AppLanguageManager.isLanguageAvailable(context, AppLanguage.HINDI))
        assertTrue(AppLanguageManager.isLanguageAvailable(context, AppLanguage.URDU))
        assertTrue(AppLanguageManager.isLanguageAvailable(context, AppLanguage.TAMIL))
    }

    @Test
    fun testInstalledPlayLanguagesQuery() {
        testSplitManager.installedLanguagesSet.addAll(setOf("hi", "ta"))
        val installed = manager.getInstalledPlayLanguages()
        assertEquals(2, installed.size)
        assertTrue(installed.contains("hi"))
        assertTrue(installed.contains("ta"))

        val installedViaHelper = AppLanguageManager.getInstalledPlayLanguages(context)
        assertEquals(2, installedViaHelper.size)
        assertTrue(installedViaHelper.contains("hi"))
    }

    @Test
    fun testErrorMessageMappingsForPlayStoreErrors() {
        assertEquals(
            "Google Play Feature Delivery API is not available on this device.",
            manager.getErrorMessage(SplitInstallErrorCode.API_NOT_AVAILABLE)
        )
        assertEquals(
            "Network error downloading language resources from Google Play.",
            manager.getErrorMessage(SplitInstallErrorCode.NETWORK_ERROR)
        )
        assertEquals(
            "Insufficient storage space to download language resources.",
            manager.getErrorMessage(SplitInstallErrorCode.INSUFFICIENT_STORAGE)
        )
        assertEquals(
            "Access denied to Google Play language delivery.",
            manager.getErrorMessage(SplitInstallErrorCode.ACCESS_DENIED)
        )
        assertEquals(
            "Requested language resource module is unavailable on Google Play.",
            manager.getErrorMessage(SplitInstallErrorCode.MODULE_UNAVAILABLE)
        )
        assertEquals(
            "Invalid language delivery request.",
            manager.getErrorMessage(SplitInstallErrorCode.INVALID_REQUEST)
        )
        assertEquals(
            "Active download sessions limit exceeded. Please wait and try again.",
            manager.getErrorMessage(SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED)
        )
        assertEquals(
            "Installation incompatible with an existing session.",
            manager.getErrorMessage(SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION)
        )
        assertEquals(
            "Internal error occurred.",
            "Google Play internal error occurred.",
            manager.getErrorMessage(SplitInstallErrorCode.INTERNAL_ERROR)
        )
        assertEquals(
            "Installation session was not found.",
            manager.getErrorMessage(SplitInstallErrorCode.SESSION_NOT_FOUND)
        )
        assertEquals(
            "No error",
            manager.getErrorMessage(SplitInstallErrorCode.NO_ERROR)
        )
    }

    @Test
    fun testSessionStateUpdates() {
        val sessionId = 42

        // Simulate PENDING state
        val pendingState = SplitInstallSessionState.create(
            sessionId,
            SplitInstallSessionStatus.PENDING,
            SplitInstallErrorCode.NO_ERROR,
            0L,
            1000L,
            emptyList(),
            listOf("hi")
        )
        manager.handleSessionState(pendingState)
        val state1 = manager.installState.value
        assertTrue(state1 is PlayLanguageInstallState.Pending)
        assertEquals(AppLanguage.HINDI, (state1 as PlayLanguageInstallState.Pending).language)

        // Simulate DOWNLOADING state
        val downloadingState = SplitInstallSessionState.create(
            sessionId,
            SplitInstallSessionStatus.DOWNLOADING,
            SplitInstallErrorCode.NO_ERROR,
            500L,
            1000L,
            emptyList(),
            listOf("hi")
        )
        manager.handleSessionState(downloadingState)
        val state2 = manager.installState.value
        assertTrue(state2 is PlayLanguageInstallState.Downloading)
        assertEquals(500L, (state2 as PlayLanguageInstallState.Downloading).bytesDownloaded)
        assertEquals(1000L, (state2 as PlayLanguageInstallState.Downloading).totalBytesToDownload)

        // Simulate DOWNLOADED state
        val downloadedState = SplitInstallSessionState.create(
            sessionId,
            SplitInstallSessionStatus.DOWNLOADED,
            SplitInstallErrorCode.NO_ERROR,
            1000L,
            1000L,
            emptyList(),
            listOf("hi")
        )
        manager.handleSessionState(downloadedState)
        val state3 = manager.installState.value
        assertTrue(state3 is PlayLanguageInstallState.Downloaded)

        // Simulate INSTALLING state
        val installingState = SplitInstallSessionState.create(
            sessionId,
            SplitInstallSessionStatus.INSTALLING,
            SplitInstallErrorCode.NO_ERROR,
            1000L,
            1000L,
            emptyList(),
            listOf("hi")
        )
        manager.handleSessionState(installingState)
        val state4 = manager.installState.value
        assertTrue(state4 is PlayLanguageInstallState.Installing)

        // Simulate INSTALLED state
        val installedState = SplitInstallSessionState.create(
            sessionId,
            SplitInstallSessionStatus.INSTALLED,
            SplitInstallErrorCode.NO_ERROR,
            1000L,
            1000L,
            emptyList(),
            listOf("hi")
        )
        manager.handleSessionState(installedState)
        val state5 = manager.installState.value
        assertTrue(state5 is PlayLanguageInstallState.Installed)
        assertEquals(AppLanguage.HINDI, (state5 as PlayLanguageInstallState.Installed).language)

        // Simulate FAILED state
        val failedState = SplitInstallSessionState.create(
            sessionId + 1,
            SplitInstallSessionStatus.FAILED,
            SplitInstallErrorCode.NETWORK_ERROR,
            0L,
            0L,
            emptyList(),
            listOf("ur")
        )
        manager.handleSessionState(failedState)
        val state6 = manager.installState.value
        assertTrue(state6 is PlayLanguageInstallState.Failed)
        assertEquals(SplitInstallErrorCode.NETWORK_ERROR, (state6 as PlayLanguageInstallState.Failed).errorCode)
        assertEquals(AppLanguage.URDU, (state6 as PlayLanguageInstallState.Failed).language)

        // Simulate CANCELED state
        val canceledState = SplitInstallSessionState.create(
            sessionId + 2,
            SplitInstallSessionStatus.CANCELED,
            SplitInstallErrorCode.NO_ERROR,
            0L,
            0L,
            emptyList(),
            listOf("ta")
        )
        manager.handleSessionState(canceledState)
        val state7 = manager.installState.value
        assertTrue(state7 is PlayLanguageInstallState.Canceled)
        assertEquals(AppLanguage.TAMIL, (state7 as PlayLanguageInstallState.Canceled).language)

        // Reset state
        manager.resetState()
        assertEquals(PlayLanguageInstallState.Idle, manager.installState.value)
    }

    @Test
    fun testDeferredInstallAndUninstallMethods() {
        val taskInstall = manager.deferredInstallLanguage(AppLanguage.HINDI)
        assertNotNull(taskInstall)

        val taskUninstall = manager.deferredUninstallLanguage(AppLanguage.TAMIL)
        assertNotNull(taskUninstall)
    }

    @Test
    fun testRequestLanguageInstallExecution() {
        var callbackInvoked = false
        val task = manager.requestInstallLanguage(
            language = AppLanguage.HINDI,
            onSuccess = { callbackInvoked = true }
        )
        assertNotNull(task)
    }
}
