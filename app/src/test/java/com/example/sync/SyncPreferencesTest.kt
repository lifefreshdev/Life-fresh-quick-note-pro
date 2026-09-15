package com.example.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncPreferencesTest {

    private lateinit var context: Context
    private lateinit var syncPreferences: SyncPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        syncPreferences = SyncPreferences(context)
    }

    @Test
    fun testAutomaticSyncDefaultIsFalse() {
        assertFalse(syncPreferences.isAutomaticSyncEnabled())
    }

    @Test
    fun testSetAutomaticSyncEnabled() {
        syncPreferences.setAutomaticSyncEnabled(true)
        assertTrue(syncPreferences.isAutomaticSyncEnabled())

        syncPreferences.setAutomaticSyncEnabled(false)
        assertFalse(syncPreferences.isAutomaticSyncEnabled())
    }

    @Test
    fun testDeviceIdGenerationAndStability() {
        val deviceId1 = syncPreferences.getOrCreateDeviceId()
        assertNotNull(deviceId1)
        assertTrue(deviceId1.isNotBlank())

        val deviceId2 = syncPreferences.getOrCreateDeviceId()
        assertEquals(deviceId1, deviceId2)
    }

    @Test
    fun testLastSuccessfulSyncAt() {
        assertEquals(0L, syncPreferences.getLastSuccessfulSyncAt())

        val now = System.currentTimeMillis()
        syncPreferences.setLastSuccessfulSyncAt(now)
        assertEquals(now, syncPreferences.getLastSuccessfulSyncAt())
    }

    @Test
    fun testUidScopedRestoreStatus() {
        val uidA = "user_A_123"
        val uidB = "user_B_456"

        assertFalse(syncPreferences.isInitialRestoreCompleted(uidA))
        assertFalse(syncPreferences.isInitialRestoreCompleted(uidB))

        syncPreferences.setInitialRestoreCompleted(uidA, true)
        assertTrue(syncPreferences.isInitialRestoreCompleted(uidA))
        assertFalse(syncPreferences.isInitialRestoreCompleted(uidB))

        // Blank UID handling
        assertFalse(syncPreferences.isInitialRestoreCompleted(""))
        syncPreferences.setInitialRestoreCompleted("", true)
        assertFalse(syncPreferences.isInitialRestoreCompleted(""))
    }

    @Test
    fun testSyncErrorHandling() {
        assertNull(syncPreferences.getLastSyncError())

        val errorMsg = "Network connection timeout"
        syncPreferences.setLastSyncError(errorMsg)
        assertEquals(errorMsg, syncPreferences.getLastSyncError())

        syncPreferences.clearLastSyncError()
        assertNull(syncPreferences.getLastSyncError())
    }
}
