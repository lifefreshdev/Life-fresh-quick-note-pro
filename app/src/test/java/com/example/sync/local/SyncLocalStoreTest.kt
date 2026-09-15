package com.example.sync.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncLocalStoreTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var syncLocalStore: SyncLocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncLocalStore = SyncLocalStore(
            database = database,
            syncDao = database.syncDao,
            metadataDao = database.leadSyncMetadataDao
        )
    }

    @After
    fun tearDown() {
        if (database.isOpen) {
            database.close()
        }
    }

    @Test
    fun testEnqueueLeadUpsertPersistsOutboxAndMetadata() = runBlocking {
        val mutation = syncLocalStore.enqueueLeadUpsert(
            ownerUid = "u1",
            leadId = "lead_100",
            payloadHash = "hash_100",
            localVersion = 1,
            timestampUtc = 10000L
        )

        assertNotNull(mutation.mutationId)
        assertEquals("LEAD", mutation.entityType)
        assertEquals("lead_100", mutation.entityId)
        assertEquals("UPSERT", mutation.operation)
        assertEquals(1L, mutation.localVersion)
        assertEquals("PENDING", mutation.state)

        val meta = database.leadSyncMetadataDao.getByLeadId("lead_100", "u1")
        assertNotNull(meta)
        assertEquals("PENDING", meta?.syncState)
        assertEquals(1L, meta?.localVersion)
        assertEquals(10000L, meta?.localUpdatedAt)
        assertFalse(meta!!.deleted)
    }

    @Test
    fun testEnqueueLeadDeleteSurvivesLeadDeletionAndPreservesTombstone() = runBlocking {
        // Enqueue delete for lead
        val deleteMutation = syncLocalStore.enqueueLeadDelete(
            ownerUid = "u1",
            leadId = "lead_200",
            deletedAtUtc = 20000L
        )

        assertEquals("DELETE", deleteMutation.operation)
        assertEquals("lead_200", deleteMutation.entityId)

        val meta = database.leadSyncMetadataDao.getByLeadId("lead_200", "u1")
        assertNotNull(meta)
        assertTrue(meta!!.deleted)
        assertEquals(20000L, meta.deletedAt)
        assertEquals("PENDING", meta.syncState)

        // Verify outbox retains the row
        val outboxRows = database.syncDao.getMutationsByState("u1", "PENDING")
        assertEquals(1, outboxRows.size)
        assertEquals("DELETE", outboxRows[0].operation)
    }

    @Test
    fun testDeterministicBatchClaimingOrder() = runBlocking {
        syncLocalStore.enqueueLeadUpsert("u1", "lead_a", "hash_a", 1, 1000L)
        syncLocalStore.enqueueLeadUpsert("u1", "lead_b", "hash_b", 1, 2000L)
        syncLocalStore.enqueueLeadUpsert("u1", "lead_c", "hash_c", 1, 1500L)

        val batch = syncLocalStore.claimNextBatch(ownerUid = "u1", limit = 2, nowUtc = 3000L)
        assertEquals(2, batch.size)
        // First created should be claimed first (1000L, then 1500L)
        assertEquals("lead_a", batch[0].entityId)
        assertEquals("lead_c", batch[1].entityId)

        val pendingRemaining = syncLocalStore.getPendingCount("u1")
        // 1 unclaimed PENDING + 2 IN_FLIGHT
        assertEquals(3, pendingRemaining)
    }

    @Test
    fun testFailMutationIncrementsAttemptAndPersists() = runBlocking {
        val mutation = syncLocalStore.enqueueLeadUpsert("u1", "lead_fail", "hash_fail", 1, 1000L)

        syncLocalStore.failMutation(
            ownerUid = "u1",
            mutationId = mutation.mutationId,
            errorClass = "NETWORK",
            errorMessage = "HTTP 503 Service Unavailable",
            nextAttemptAtUtc = 5000L,
            nowUtc = 2000L
        )

        val failedMutations = database.syncDao.getMutationsByState("u1", "FAILED")
        assertEquals(1, failedMutations.size)
        assertEquals(1, failedMutations[0].attemptCount)
        assertEquals("NETWORK", failedMutations[0].lastErrorClass)
        assertEquals("HTTP 503 Service Unavailable", failedMutations[0].lastErrorMessage)
        assertEquals(5000L, failedMutations[0].nextAttemptAtUtc)
    }

    @Test
    fun testAcknowledgeMutationUpdatesOutboxAndMetadata() = runBlocking {
        val mutation = syncLocalStore.enqueueLeadUpsert("u1", "lead_ack", "hash_ack", 1, 1000L)

        syncLocalStore.acknowledgeMutation(
            ownerUid = "u1",
            mutationId = mutation.mutationId,
            leadId = "lead_ack",
            serverVersion = 100L,
            remoteUpdatedAt = 2500L,
            nowUtc = 3000L
        )

        val ackMutations = database.syncDao.getMutationsByState("u1", "ACKNOWLEDGED")
        assertEquals(1, ackMutations.size)
        assertEquals(mutation.mutationId, ackMutations[0].mutationId)

        val meta = database.leadSyncMetadataDao.getByLeadId("lead_ack", "u1")
        assertEquals("SYNCED", meta?.syncState)
        assertEquals(100L, meta?.serverVersion)
        assertEquals(2500L, meta?.remoteUpdatedAt)
    }

    @Test
    fun testCommitRemotePageTransactionRollsBackOnFailure() = runBlocking {
        val scope = "leads:user_test"

        val conflict = SyncConflictEntity(
            conflictId = "c1",
            ownerUid = "user_test",
            entityType = "LEAD",
            entityId = "lead_err",
            localPayload = "{}",
            remotePayload = "{}",
            winner = "REMOTE",
            reason = "Remote newer",
            detectedAtUtc = 1000L
        )

        try {
            syncLocalStore.commitRemotePage(
                scopeId = scope,
                ownerUid = "user_test",
                remoteCursor = "cursor_fail",
                lastSuccessfulSyncAtUtc = 5000L,
                conflicts = listOf(conflict),
                ackMutationIds = emptyList(),
                localMergeAction = {
                    throw IllegalStateException("Simulated merge exception!")
                },
                nowUtc = 2000L
            )
            fail("Expected exception was not thrown")
        } catch (e: IllegalStateException) {
            assertEquals("Simulated merge exception!", e.message)
        }

        // Verify transaction rolled back: no cursor updated, no conflict saved
        assertNull(database.syncDao.getCheckpoint(scope))
        assertEquals(0, database.syncDao.getUnresolvedConflicts("user_test").size)
    }

    @Test
    fun testCommitRemotePageSuccessSavesCursorAndConflicts() = runBlocking {
        val scope = "leads:user_test_success"

        val conflict = SyncConflictEntity(
            conflictId = "c_ok",
            ownerUid = "user_test_success",
            entityType = "LEAD",
            entityId = "lead_ok",
            localPayload = "{}",
            remotePayload = "{}",
            winner = "LOCAL",
            reason = "Local newer",
            detectedAtUtc = 1000L
        )

        var mergeExecuted = false

        syncLocalStore.commitRemotePage(
            scopeId = scope,
            ownerUid = "user_test_success",
            remoteCursor = "cursor_success_v1",
            lastSuccessfulSyncAtUtc = 8000L,
            conflicts = listOf(conflict),
            ackMutationIds = emptyList(),
            localMergeAction = {
                mergeExecuted = true
            },
            nowUtc = 3000L
        )

        assertTrue(mergeExecuted)

        val checkpoint = database.syncDao.getCheckpoint(scope)
        assertNotNull(checkpoint)
        assertEquals("cursor_success_v1", checkpoint?.remoteCursor)
        assertEquals(8000L, checkpoint?.lastSuccessfulSyncAtUtc)

        assertEquals(1, database.syncDao.getUnresolvedConflicts("user_test_success").size)
    }
}
