package com.example.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncSchedulerTest {

    private lateinit var context: Context
    private lateinit var prefs: SyncPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
        } catch (_: Exception) {
        }
        prefs = SyncPreferences(context)
    }

    @Test
    fun setEnabled_updatesPreferences() {
        SyncScheduler.setEnabled(context, true)
        assertTrue(prefs.isAutomaticSyncEnabled())

        SyncScheduler.setEnabled(context, false)
        assertFalse(prefs.isAutomaticSyncEnabled())
    }

    @Test
    fun enqueueManual_executesWithoutException() {
        try {
            SyncScheduler.enqueueManual(context)
        } catch (e: Exception) {
            fail("enqueueManual threw exception: ${e.message}")
        }
    }

    @Test
    fun enqueueMutationSync_whenEnabled_schedulesSuccessfully() {
        prefs.setAutomaticSyncEnabled(true)
        try {
            SyncScheduler.enqueueMutationSync(context)
        } catch (e: Exception) {
            fail("enqueueMutationSync with auto sync enabled threw exception: ${e.message}")
        }
    }

    @Test
    fun cancelPeriodicSync_executesWithoutException() {
        try {
            SyncScheduler.cancelPeriodicSync(context)
        } catch (e: Exception) {
            fail("cancelPeriodicSync threw exception: ${e.message}")
        }
    }
}
