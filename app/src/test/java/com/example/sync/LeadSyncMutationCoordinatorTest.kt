package com.example.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import com.example.sync.SyncUser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LeadSyncMutationCoordinatorTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var coordinator: LeadSyncMutationCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coordinator = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            currentUserProvider = { SyncUser("u1") }
        )
    }

    @After
    fun tearDown() {
        if (database.isOpen) {
            database.close()
        }
    }

    private fun createTestLead(
        id: String,
        name: String,
        status: String = "Pending",
        notes: String = "",
        reminderDate: String = "",
        reminderTime: String = "",
        archived: Boolean = false,
        lastCall: String? = null,
        ownerUid: String = "u1"
    ) = LeadEntity(
        id = id,
        name = name,
        mobile = "1234567890",
        diseases = "",
        otherDisease = "",
        relation = "",
        otherRelation = "",
        status = status,
        reminderDate = reminderDate,
        reminderTime = reminderTime,
        reminderNote = "",
        reminderStatus = "Pending",
        notes = notes,
        archived = archived,
        lastCall = lastCall,
        ownerUid = ownerUid
    )

    @Test
    fun testNewLeadInsertCreatesMetadataAndUpsertOutbox() = runBlocking {
        val lead = createTestLead(id = "lead_1", name = "Alice", status = "Pending")
        coordinator.upsertLead(lead, LeadWriteOrigin.LOCAL_USER)

        val meta = database.leadSyncMetadataDao.getByLeadId("lead_1", "u1")
        assertNotNull(meta)
        assertEquals(1L, meta?.localVersion)
        assertEquals("PENDING", meta?.syncState)
        assertFalse(meta!!.deleted)

        val mutations = database.syncDao.getMutationsByState("u1", "PENDING")
        assertEquals(1, mutations.size)
        assertEquals("LEAD", mutations[0].entityType)
        assertEquals("lead_1", mutations[0].entityId)
        assertEquals("UPSERT", mutations[0].operation)
        assertEquals(1L, mutations[0].localVersion)
        assertFalse(mutations[0].payloadHash.isNullOrBlank())
    }

    @Test
    fun testLeadUpdateIncrementsVersionAndCreatesNewerUpsert() = runBlocking {
        val lead1 = createTestLead(id = "lead_1", name = "Alice", status = "Pending")
        coordinator.upsertLead(lead1, LeadWriteOrigin.LOCAL_USER)

        val lead2 = lead1.copy(status = "In Progress")
        coordinator.upsertLead(lead2, LeadWriteOrigin.LOCAL_USER)

        val meta = database.leadSyncMetadataDao.getByLeadId("lead_1", "u1")
        assertNotNull(meta)
        assertEquals(2L, meta?.localVersion)

        val mutations = database.syncDao.getMutationsByState("u1", "PENDING")
        assertEquals(2, mutations.size)
        assertEquals(2L, mutations[1].localVersion)
    }

    @Test
    fun testStatusNoteReminderArchiveLastCallChangesCreateUpsert() = runBlocking {
        val lead = createTestLead(id = "lead_mut", name = "Bob", status = "Pending")
        coordinator.upsertLead(lead, LeadWriteOrigin.LOCAL_USER)

        // Status change
        coordinator.upsertLead(lead.copy(status = "Complete"), LeadWriteOrigin.LOCAL_USER)
        // Note change
        coordinator.upsertLead(lead.copy(notes = "Met in conference"), LeadWriteOrigin.LOCAL_USER)
        // Reminder change
        coordinator.upsertLead(lead.copy(reminderDate = "2026-08-01", reminderTime = "10:00"), LeadWriteOrigin.LOCAL_USER)
        // Archive change
        coordinator.upsertLead(lead.copy(archived = true), LeadWriteOrigin.LOCAL_USER)
        // Last call change
        coordinator.upsertLead(lead.copy(lastCall = "2026-07-22T08:00:00Z"), LeadWriteOrigin.LOCAL_USER)

        val meta = database.leadSyncMetadataDao.getByLeadId("lead_mut", "u1")
        assertNotNull(meta)
        assertEquals(6L, meta?.localVersion)

        val mutations = database.syncDao.getMutationsByState("u1", "PENDING")
        assertEquals(6, mutations.size)
    }

    @Test
    fun testPermanentDeleteCreatesTombstoneAndSurvivesRemoval() = runBlocking {
        val lead = createTestLead(id = "lead_del", name = "Charlie", status = "Pending")
        coordinator.upsertLead(lead, LeadWriteOrigin.LOCAL_USER)

        coordinator.deleteLead("lead_del", "u1", LeadWriteOrigin.LOCAL_USER)

        // Lead row deleted
        assertNull(database.leadDao.getLeadById("lead_del", "u1"))

        // Metadata tombstone preserved
        val meta = database.leadSyncMetadataDao.getByLeadId("lead_del", "u1")
        assertNotNull(meta)
        assertTrue(meta!!.deleted)
        assertEquals("PENDING", meta.syncState)

        // DELETE outbox row exists
        val mutations = database.syncDao.getMutationsByState("u1", "PENDING")
        val deleteMut = mutations.find { it.operation == "DELETE" }
        assertNotNull(deleteMut)
        assertEquals("lead_del", deleteMut?.entityId)
        assertFalse(deleteMut!!.payloadHash.isNullOrBlank())
    }

    @Test
    fun testRemoteMergeDoesNotCreateOutboxMutation() = runBlocking {
        val lead = createTestLead(id = "lead_remote", name = "Remote Lead", status = "Synced")
        coordinator.upsertLead(lead, LeadWriteOrigin.REMOTE_SYNC)

        val meta = database.leadSyncMetadataDao.getByLeadId("lead_remote", "u1")
        assertNotNull(meta)
        assertEquals("SYNCED", meta?.syncState)

        val mutations = database.syncDao.getMutationsByState("u1", "PENDING")
        assertTrue(mutations.isEmpty())
        assertEquals(0, database.syncDao.getPendingCount("u1"))
        assertTrue(database.leadSyncMetadataDao.getPendingSyncItems("u1").isEmpty())
    }

    @Test
    fun testRemoteRestoreEntersCleanSyncedStateWithoutOutboxOrPendingCount() = runBlocking {
        val lead = createTestLead(id = "lead_restore_1", name = "Restored Lead", status = "Pending")
        coordinator.upsertLead(lead, LeadWriteOrigin.REMOTE_RESTORE)

        // 1. Lead exists in Room
        val savedLead = database.leadDao.getLeadById("lead_restore_1", "u1")
        assertNotNull(savedLead)
        assertEquals("Restored Lead", savedLead?.name)

        // 2. Metadata exists with SYNCED state
        val meta = database.leadSyncMetadataDao.getByLeadId("lead_restore_1", "u1")
        assertNotNull(meta)
        assertEquals("SYNCED", meta?.syncState)
        assertFalse(meta!!.deleted)
        assertEquals(1L, meta.localVersion)

        // 3. Outbox has 0 pending mutations
        val mutations = database.syncDao.getMutationsByState("u1", "PENDING")
        assertTrue(mutations.isEmpty())

        // 4. Pending counters report 0
        assertEquals(0, database.syncDao.getPendingCount("u1"))
        assertEquals(0, database.syncDao.getUnsyncedOutboxCount("u1"))
        assertTrue(database.leadSyncMetadataDao.getPendingSyncItems("u1").isEmpty())
    }

    @Test
    fun testRemoteRestorePreservesLocalPendingEdits() = runBlocking {
        val localLead = createTestLead(id = "lead_conflict", name = "Local Edit Name", notes = "Local Note")
        coordinator.upsertLead(localLead, LeadWriteOrigin.LOCAL_USER)

        // Attempt restore with older cloud version
        val cloudLead = createTestLead(id = "lead_conflict", name = "Cloud Old Name", notes = "Cloud Note")
        coordinator.upsertLead(cloudLead, LeadWriteOrigin.REMOTE_RESTORE)

        // Local edit preserved
        val currentLead = database.leadDao.getLeadById("lead_conflict", "u1")
        assertEquals("Local Edit Name", currentLead?.name)
        val meta = database.leadSyncMetadataDao.getByLeadId("lead_conflict", "u1")
        assertEquals("PENDING", meta?.syncState)
    }

    @Test
    fun testRemoteRestoreDoesNotResurrectLocallyDeletedLead() = runBlocking {
        val lead = createTestLead(id = "lead_to_del", name = "To Delete")
        coordinator.upsertLead(lead, LeadWriteOrigin.LOCAL_USER)
        coordinator.deleteLead("lead_to_del", "u1", LeadWriteOrigin.LOCAL_USER)

        // Attempt to restore deleted lead
        val cloudLead = createTestLead(id = "lead_to_del", name = "Resurrected Name")
        coordinator.upsertLead(cloudLead, LeadWriteOrigin.REMOTE_RESTORE)

        // Lead remains deleted
        assertNull(database.leadDao.getLeadById("lead_to_del", "u1"))
        val meta = database.leadSyncMetadataDao.getByLeadId("lead_to_del", "u1")
        assertNotNull(meta)
        assertTrue(meta!!.deleted)
    }

    @Test
    fun testPayloadHashIsDeterministic() = runBlocking {
        val lead = createTestLead(id = "lead_hash", name = "Test", status = "Pending")
        val hash1 = coordinator.computePayloadHash(lead, 1L)
        val hash2 = coordinator.computePayloadHash(lead, 1L)
        assertEquals(hash1, hash2)
        assertFalse(hash1.contains("Test")) // no raw PII in hash
    }

    @Test
    fun testDeleteHashContainsNoPii() = runBlocking {
        val hash = coordinator.computeDeleteHash("lead_123", "u1", 2L)
        assertNotNull(hash)
        assertFalse(hash.contains("lead_123"))
    }
}
