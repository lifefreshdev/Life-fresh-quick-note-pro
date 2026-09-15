package com.example.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoSyncWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SyncRuntimeFactory.resetOverrides()
    }

    @After
    fun tearDown() {
        SyncRuntimeFactory.resetOverrides()
    }

    @Test
    fun worker_whenRepositoryReturnsAuthRequired_returnsFailure() = runBlocking {
        SyncRuntimeFactory.setTestOverrides(
            userProvider = { null }
        )

        val inputData = workDataOf("manualSync" to true)
        val worker = TestListenableWorkerBuilder<AutoSyncWorker>(context)
            .setInputData(inputData)
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun worker_whenRepositoryReturnsDisabled_returnsSuccess() = runBlocking {
        SyncRuntimeFactory.setTestOverrides(
            userProvider = { SyncUser("user_1") }
        )
        SyncPreferences(context).setAutomaticSyncEnabled(false)

        val inputData = workDataOf("manualSync" to false)
        val worker = TestListenableWorkerBuilder<AutoSyncWorker>(context)
            .setInputData(inputData)
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }
}
