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
class SyncDaoTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var syncDao: SyncDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncDao = database.syncDao
    }

    @After
    fun tearDown() {
        if (database.isOpen) {
            database.close()
        }
    }

    @Test
    fun testOutboxInsertAndClaim() = runBlocking {
        val mutation1 = SyncOutboxEntity(
            mutationId = "m1",
            ownerUid = "u1",
            entityType = "LEAD",
            entityId = "lead1",
            operation = "UPSERT",
            localVersion = 1,
            payloadHash = "hash1",
            state = "PENDING",
            createdAtUtc = 1000L,
            updatedAtUtc = 1000L
        )
        val mutation2 = SyncOutboxEntity(
            mutationId = "m2",
            ownerUid = "u1",
            entityType = "LEAD",
            entityId = "lead2",
            operation = "DELETE",
            localVersion = 1,
            payloadHash = "",
            state = "PENDING",
            createdAtUtc = 2000L,
            updatedAtUtc = 2000L
        )

        syncDao.insertMutation(mutation1)
        syncDao.insertMutation(mutation2)

        val pending = syncDao.getReadyPendingMutations("u1", 3000L, limit = 10)
        assertEquals(2, pending.size)
        assertEquals("m1", pending[0].mutationId)

        syncDao.claimBatch("u1", listOf("m1"), 2500L)

        val inFlight = syncDao.getMutationsByState("u1", "IN_FLIGHT")
        assertEquals(1, inFlight.size)
        assertEquals("m1", inFlight[0].mutationId)

        val remainingPending = syncDao.getReadyPendingMutations("u1", 3000L, limit = 10)
        assertEquals(1, remainingPending.size)
        assertEquals("m2", remainingPending[0].mutationId)
    }

    @Test
    fun testDuplicateMutationSameVersionIsIgnored() = runBlocking {
        val mutation1 = SyncOutboxEntity(
            mutationId = "m1",
            ownerUid = "u1",
            entityType = "LEAD",
            entityId = "lead1",
            operation = "UPSERT",
            localVersion = 1,
            payloadHash = "hash1",
            state = "PENDING",
            createdAtUtc = 1000L,
            updatedAtUtc = 1000L
        )
        val mutation2 = SyncOutboxEntity(
            mutationId = "m2",
            ownerUid = "u1",
            entityType = "LEAD",
            entityId = "lead1",
            operation = "UPSERT",
            localVersion = 1,
            payloadHash = "hash1_dup",
            state = "PENDING",
            createdAtUtc = 1005L,
            updatedAtUtc = 1005L
        )

        val res1 = syncDao.insertMutationIgnore(mutation1)
        assertTrue(res1 > 0)

        val res2 = syncDao.insertMutationIgnore(mutation2)
        assertEquals(-1L, res2)

        val all = syncDao.getMutationsByState("u1", "PENDING")
        assertEquals(1, all.size)
        assertEquals("m1", all[0].mutationId)
    }

    @Test
    fun testFailAndQuarantineMutation() = runBlocking {
        val mutation = SyncOutboxEntity(
            mutationId = "m1",
            ownerUid = "u1",
            entityType = "LEAD",
            entityId = "lead1",
            operation = "UPSERT",
            localVersion = 1,
            payloadHash = "hash1",
            state = "PENDING",
            createdAtUtc = 1000L,
            updatedAtUtc = 1000L
        )
        syncDao.insertMutation(mutation)

        syncDao.failMutation("u1", "m1", "NETWORK", "Connect timeout", 5000L, 2000L)

        val failedList = syncDao.getMutationsByState("u1", "FAILED")
        assertEquals(1, failedList.size)
        assertEquals(1, failedList[0].attemptCount)
        assertEquals("NETWORK", failedList[0].lastErrorClass)
        assertEquals(5000L, failedList[0].nextAttemptAtUtc)

        // Ready test when nowUtc < nextAttemptAtUtc
        val readyEarly = syncDao.getReadyPendingMutations("u1", 3000L)
        assertTrue(readyEarly.isEmpty())

        // Ready test when nowUtc >= nextAttemptAtUtc
        val readyLate = syncDao.getReadyPendingMutations("u1", 5000L)
        assertEquals(1, readyLate.size)

        // Test quarantine
        syncDao.quarantineMutation("u1", "m1", "MALFORMED_DATA", "Bad payload", 6000L)
        val quarantined = syncDao.getMutationsByState("u1", "QUARANTINED")
        assertEquals(1, quarantined.size)
        assertEquals("MALFORMED_DATA", quarantined[0].lastErrorClass)
    }

    @Test
    fun testResetInFlightAndAcknowledge() = runBlocking {
        val m1 = SyncOutboxEntity("m1", "u1", "LEAD", "l1", "UPSERT", 1, "h1", "IN_FLIGHT", 0, 1000L, 1000L)
        syncDao.insertMutation(m1)

        syncDao.resetInFlightMutations("u1", 2000L)
        val pending = syncDao.getMutationsByState("u1", "PENDING")
        assertEquals(1, pending.size)

        syncDao.acknowledgeMutation("u1", "m1", 3000L)
        val ack = syncDao.getMutationsByState("u1", "ACKNOWLEDGED")
        assertEquals(1, ack.size)

        syncDao.deleteAcknowledgedBefore("u1", 4000L)
        val afterClean = syncDao.getMutationsByState("u1", "ACKNOWLEDGED")
        assertTrue(afterClean.isEmpty())
    }

    @Test
    fun testConflictStorageAndPruning() = runBlocking {
        for (i in 1..5) {
            syncDao.insertConflict(
                SyncConflictEntity(
                    conflictId = "c$i",
                    ownerUid = "u1",
                    entityType = "LEAD",
                    entityId = "l$i",
                    localPayload = "{local}",
                    remotePayload = "{remote}",
                    winner = "LOCAL",
                    reason = "Newer local edit",
                    detectedAtUtc = 1000L * i,
                    resolved = false
                )
            )
        }

        assertEquals(5, syncDao.countUnresolvedConflicts("u1"))
        assertEquals(5, syncDao.getUnresolvedConflicts("u1").size)

        syncDao.markConflictResolved("u1", "c1", 6000L)
        assertEquals(4, syncDao.countUnresolvedConflicts("u1"))
        assertEquals(1, syncDao.getResolvedConflicts("u1").size)

        // Prune resolved
        syncDao.pruneResolvedConflicts("u1", 7000L)
        assertTrue(syncDao.getResolvedConflicts("u1").isEmpty())

        // Unresolved conflicts are still intact
        assertEquals(4, syncDao.countUnresolvedConflicts("u1"))
    }

    @Test
    fun testPruneExcessResolvedConflicts() = runBlocking {
        for (i in 1..105) {
            syncDao.insertConflict(
                SyncConflictEntity(
                    conflictId = "c$i",
                    ownerUid = "u1",
                    entityType = "LEAD",
                    entityId = "l$i",
                    localPayload = "{local}",
                    remotePayload = "{remote}",
                    winner = "REMOTE",
                    reason = "Remote winner",
                    detectedAtUtc = 1000L * i,
                    resolved = true,
                    resolvedAtUtc = 2000L * i
                )
            )
        }

        assertEquals(105, syncDao.getResolvedConflicts("u1").size)
        syncDao.pruneExcessResolvedConflicts("u1", keepLimit = 100)
        assertEquals(100, syncDao.getResolvedConflicts("u1").size)
    }

    @Test
    fun testCheckpointOperations() = runBlocking {
        val checkpoint = SyncCheckpointEntity(
            scopeId = "leads:user123",
            remoteCursor = "cursor1",
            lastRunId = "run1",
            lastRunStartedAtUtc = 1000L,
            lastRunCompletedAtUtc = 2000L,
            lastSuccessfulSyncAtUtc = 2000L,
            lastPhase = "COMPLETED",
            lastMessage = "OK",
            schemaVersion = 1
        )

        syncDao.insertOrUpdateCheckpoint(checkpoint)

        val fetched = syncDao.getCheckpoint("leads:user123")
        assertNotNull(fetched)
        assertEquals("cursor1", fetched?.remoteCursor)

        syncDao.commitCheckpointCursor("leads:user123", "cursor2", 3000L, 3000L)
        val updated = syncDao.getCheckpoint("leads:user123")
        assertEquals("cursor2", updated?.remoteCursor)
        assertEquals(3000L, updated?.lastSuccessfulSyncAtUtc)

        // Another scope check
        assertNull(syncDao.getCheckpoint("leads:user456"))
    }
}
