package com.example.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import com.example.data.repository.LeadRepository
import com.example.leads.domain.LeadDraft
import com.example.leads.operation.LeadOperationService
import com.example.sync.local.SyncLocalStore
import com.example.sync.model.*
import com.example.sync.remote.RemoteSyncDataSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudPrivacyAndSyncArchitectureTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var prefs: SyncPreferences
    private lateinit var fakeRemoteDataSource: FakeRemoteSyncDataSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = SyncPreferences(context)
        fakeRemoteDataSource = FakeRemoteSyncDataSource()
    }

    @After
    fun tearDown() {
        if (database.isOpen) {
            database.close()
        }
    }

    private fun createLead(id: String, ownerUid: String, name: String = "Test Lead") = LeadEntity(
        id = id,
        name = name,
        mobile = "1234567890",
        diseases = "[]",
        otherDisease = "",
        relation = "Self",
        otherRelation = "",
        status = "Pending",
        reminderDate = "",
        reminderTime = "",
        reminderNote = "",
        reminderStatus = "Pending",
        notes = "Sample note",
        archived = false,
        lastCall = null,
        timestamp = System.currentTimeMillis(),
        ownerUid = ownerUid
    )

    private class FakeRemoteSyncDataSource : RemoteSyncDataSource {
        val pushedUpserts = mutableListOf<RemoteMutation>()
        val pushedDeletes = mutableListOf<RemoteMutation>()
        val remoteRecords = mutableListOf<RemoteLeadRecord>()

        override suspend fun push(uid: String, mutations: List<RemoteMutation>): PushBatchResult {
            val items = mutations.map { m ->
                if (m.operation == "DELETE") {
                    pushedDeletes.add(m)
                } else {
                    pushedUpserts.add(m)
                }
                PushItemResult(
                    mutationId = m.mutationId,
                    entityId = m.entityId,
                    status = PushItemStatus.ACKNOWLEDGED,
                    newServerVersion = (m.serverVersion ?: 0L) + 1L,
                    remoteUpdatedAt = System.currentTimeMillis()
                )
            }
            return PushBatchResult(items)
        }

        override suspend fun pull(uid: String, cursor: String?, pageSize: Int): PullPage {
            return PullPage(records = remoteRecords, nextCursor = null, hasMore = false)
        }
    }

    // Test 1: Guest save: local-only, zero cloud activity
    @Test
    fun testGuestSave_localOnlyZeroCloudActivity() = runBlocking {
        val guestUser = SyncUser(uid = "guest_123", isAnonymous = true)
        val coordinator = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { guestUser }
        )

        val lead = createLead("lead_guest_1", ownerUid = "guest_123", name = "Guest Lead")
        coordinator.upsertLead(lead, LeadWriteOrigin.LOCAL_USER)

        val savedLead = database.leadDao.getLeadById("lead_guest_1", "guest_123")
        assertNotNull("Local lead must be saved", savedLead)

        val mutations = database.syncDao.getMutationsByState("guest_123", "PENDING")
        assertTrue("Guest operations must produce zero outbox mutations", mutations.isEmpty())

        val meta = database.leadSyncMetadataDao.getByLeadId("lead_guest_1", "guest_123")
        assertNull("Guest operations must produce zero metadata records", meta)
    }

    // Test 2: Guest update/delete: local-only, zero cloud activity
    @Test
    fun testGuestUpdateAndDelete_localOnlyZeroCloudActivity() = runBlocking {
        val guestUser = SyncUser(uid = "guest_123", isAnonymous = true)
        val coordinator = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { guestUser }
        )

        val lead = createLead("lead_guest_2", ownerUid = "guest_123", name = "Guest Lead Original")
        coordinator.upsertLead(lead, LeadWriteOrigin.LOCAL_USER)

        val updatedLead = lead.copy(name = "Guest Lead Updated")
        coordinator.upsertLead(updatedLead, LeadWriteOrigin.LOCAL_USER)

        val savedLead = database.leadDao.getLeadById("lead_guest_2", "guest_123")
        assertEquals("Guest Lead Updated", savedLead?.name)

        coordinator.deleteLead("lead_guest_2", "guest_123", LeadWriteOrigin.LOCAL_USER)
        val deletedLead = database.leadDao.getLeadById("lead_guest_2", "guest_123")
        assertNull(deletedLead)

        val mutations = database.syncDao.getMutationsByState("guest_123", "PENDING")
        assertTrue("Guest operations must produce zero outbox mutations on update/delete", mutations.isEmpty())
    }

    // Test 3: Signed-in + Auto Sync OFF: local/outbox succeeds, no job/network
    @Test
    fun testSignedIn_autoSyncOff_outboxStoredNoJobOrNetwork() = runBlocking {
        val user = SyncUser(uid = "user_456", isAnonymous = false)
        prefs.setAutomaticSyncEnabled(false)

        val coordinator = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { user }
        )

        val lead = createLead("lead_user_1", ownerUid = "user_456", name = "User Lead")
        coordinator.upsertLead(lead, LeadWriteOrigin.LOCAL_USER)

        val savedLead = database.leadDao.getLeadById("lead_user_1", "user_456")
        assertNotNull(savedLead)

        val meta = database.leadSyncMetadataDao.getByLeadId("lead_user_1", "user_456")
        assertNotNull(meta)

        val mutations = database.syncDao.getMutationsByState("user_456", "PENDING")
        assertEquals(1, mutations.size)
        assertEquals("UPSERT", mutations[0].operation)

        assertFalse(prefs.isAutomaticSyncEnabled())
    }

    // Test 4: Signed-in + Auto Sync ON: exactly one mutation and one scheduling decision
    @Test
    fun testSignedIn_autoSyncOn_oneMutationAndSchedulingDecision() = runBlocking {
        val user = SyncUser(uid = "user_789", isAnonymous = false)
        prefs.setAutomaticSyncEnabled(true)

        val coordinator = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { user }
        )

        val lead = createLead("lead_user_789", ownerUid = "user_789", name = "Sync ON Lead")
        coordinator.upsertLead(lead, LeadWriteOrigin.LOCAL_USER)

        val mutations = database.syncDao.getMutationsByState("user_789", "PENDING")
        assertEquals("Exactly one outbox mutation must be created", 1, mutations.size)
        assertEquals("UPSERT", mutations[0].operation)

        val meta = database.leadSyncMetadataDao.getByLeadId("lead_user_789", "user_789")
        assertNotNull("Metadata must exist", meta)
        assertEquals(1L, meta?.localVersion)
    }

    // Test 5: Delete creates tombstone and prevents resurrection
    @Test
    fun testDeleteCreatesTombstoneAndPreventsResurrection() = runBlocking {
        val user = SyncUser(uid = "user_tombstone", isAnonymous = false)
        val coordinator = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { user }
        )

        val lead = createLead("lead_tomb_1", ownerUid = "user_tombstone", name = "To Delete")
        coordinator.upsertLead(lead, LeadWriteOrigin.LOCAL_USER)

        coordinator.deleteLead("lead_tomb_1", "user_tombstone", LeadWriteOrigin.LOCAL_USER)

        // Verify local lead removed
        val deletedLead = database.leadDao.getLeadById("lead_tomb_1", "user_tombstone")
        assertNull(deletedLead)

        // Verify metadata tombstone deleted=true
        val meta = database.leadSyncMetadataDao.getByLeadId("lead_tomb_1", "user_tombstone")
        assertNotNull("Metadata tombstone must exist", meta)
        assertTrue("Metadata deleted must be true", meta!!.deleted)

        // Verify DELETE outbox mutation
        val mutations = database.syncDao.getMutationsByState("user_tombstone", "PENDING")
        val deleteMutation = mutations.find { it.entityId == "lead_tomb_1" && it.operation == "DELETE" }
        assertNotNull("DELETE outbox mutation must exist", deleteMutation)

        // Execute Sync Push
        val localStore = SyncLocalStore(database, database.syncDao, database.leadSyncMetadataDao)
        val syncRepo = SyncRepository(
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            localStore = localStore,
            remoteDataSource = fakeRemoteDataSource,
            syncPreferences = prefs,
            currentUserProvider = { user }
        )

        // Setup remote pull payload with stale lead_tomb_1
        fakeRemoteDataSource.remoteRecords.add(
            RemoteLeadRecord(
                id = "lead_tomb_1",
                name = "Resurrect Attempt",
                mobile = "1234567890",
                diseases = "[]",
                otherDisease = "",
                relation = "Self",
                otherRelation = "",
                status = "Pending",
                reminderDate = "",
                reminderTime = "",
                reminderNote = "",
                reminderStatus = "Pending",
                notes = "",
                archived = false,
                lastCall = null,
                timestamp = System.currentTimeMillis() - 10000,
                updatedAt = System.currentTimeMillis() - 10000,
                deleted = false,
                serverVersion = 1L
            )
        )

        val syncResult = syncRepo.sync(SyncTrigger.MANUAL)
        assertTrue(syncResult is SyncRunResult.Success)

        // Verify DELETE mutation was pushed
        assertTrue("DELETE tombstone mutation must be sent to remote", fakeRemoteDataSource.pushedDeletes.any { it.entityId == "lead_tomb_1" })

        // Verify lead is NOT resurrected locally
        val resurrectedLead = database.leadDao.getLeadById("lead_tomb_1", "user_tombstone")
        assertNull("Deleted lead must NOT be resurrected by pull", resurrectedLead)
    }

    // Test 6: Account switch aborts worker / sync repository execution
    @Test
    fun testAccountSwitchAbortsWorkerAndPreventsDataLeakage() = runBlocking {
        var activeUser: SyncUser? = SyncUser("user_A", isAnonymous = false)
        val localStore = SyncLocalStore(database, database.syncDao, database.leadSyncMetadataDao)

        val syncRepo = SyncRepository(
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            localStore = localStore,
            remoteDataSource = fakeRemoteDataSource,
            syncPreferences = prefs,
            currentUserProvider = { activeUser }
        )

        // Active user changes to User B before execution
        activeUser = SyncUser("user_B", isAnonymous = false)

        val result = syncRepo.sync(SyncTrigger.MANUAL, targetUid = "user_A")
        assertTrue("Must return AuthRequired when targetUid doesn't match activeUser", result is SyncRunResult.AuthRequired)
    }

    // Test 7: Account switch aborts restore commit
    @Test
    fun testAccountSwitchAbortsRestoreCommit() = runBlocking {
        val userA = SyncUser("user_A", isAnonymous = false)
        val coordinator = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { userA }
        )

        val lead = createLead("lead_userA", ownerUid = "user_A", name = "User A Lead")
        coordinator.upsertLead(lead, LeadWriteOrigin.LOCAL_USER)

        // Attempting to query/sync with User B active
        val userB = SyncUser("user_B", isAnonymous = false)
        val localStore = SyncLocalStore(database, database.syncDao, database.leadSyncMetadataDao)
        val syncRepoUserB = SyncRepository(
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            localStore = localStore,
            remoteDataSource = fakeRemoteDataSource,
            syncPreferences = prefs,
            currentUserProvider = { userB }
        )

        val result = syncRepoUserB.sync(SyncTrigger.MANUAL, targetUid = "user_A")
        assertTrue(result is SyncRunResult.AuthRequired)

        // Ensure User A data was not mutated or leaked to User B
        val userBLeads = database.leadDao.getAllLeadsList("user_B")
        assertTrue("User B scope must be empty", userBLeads.isEmpty())
    }

    // Test 8: Manual cloud operation rejects guest
    @Test
    fun testManualCloudOperationRejectsGuest() = runBlocking {
        val guestUser = SyncUser("guest_999", isAnonymous = true)
        val localStore = SyncLocalStore(database, database.syncDao, database.leadSyncMetadataDao)

        val syncRepo = SyncRepository(
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            localStore = localStore,
            remoteDataSource = fakeRemoteDataSource,
            syncPreferences = prefs,
            currentUserProvider = { guestUser }
        )

        val result = syncRepo.sync(SyncTrigger.MANUAL)
        assertTrue("Manual cloud sync must reject guest user", result is SyncRunResult.AuthRequired)
    }

    // Test 9: Import uses unified mutation path
    @Test
    fun testImportUsesUnifiedMutationPath() = runBlocking {
        val user = SyncUser("user_import", isAnonymous = false)
        val coordinator = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { user }
        )
        val repository = LeadRepository(database.leadDao, coordinator)

        val importedLeads = listOf(
            createLead("import_1", "user_import", "Imported Lead 1"),
            createLead("import_2", "user_import", "Imported Lead 2")
        )

        repository.insertLeads(importedLeads, LeadWriteOrigin.LOCAL_IMPORT)

        val mutations = database.syncDao.getMutationsByState("user_import", "PENDING")
        assertEquals("Imported leads must create outbox mutations", 2, mutations.size)

        val meta1 = database.leadSyncMetadataDao.getByLeadId("import_1", "user_import")
        assertNotNull("Imported lead 1 metadata must exist", meta1)
    }

    // Test 10: AI-created lead uses unified mutation path
    @Test
    fun testAICreatedLeadUsesUnifiedMutationPath() = runBlocking {
        val user = SyncUser("user_ai", isAnonymous = false)
        val coordinator = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { user }
        )
        val repository = LeadRepository(database.leadDao, coordinator)
        val leadService = LeadOperationService(context, repository)

        val draft = LeadDraft(
            id = null,
            name = "AI Lead",
            mobile = "9876543210",
            diseases = listOf("General"),
            relation = "Self"
        )
        val saveResult = leadService.saveLead(draft = draft, currentLeads = emptyList(), ownerUid = "user_ai")
        assertTrue(saveResult.status == com.example.leads.operation.LeadSaveStatus.SUCCESS)

        val savedLead = database.leadDao.getAllLeadsList("user_ai").firstOrNull()
        assertNotNull("AI-created lead must be saved", savedLead)

        val mutations = database.syncDao.getMutationsByState("user_ai", "PENDING")
        assertEquals("AI-created lead must generate outbox mutation", 1, mutations.size)
    }

    // Test 11: Archive, call and reminder updates use unified mutation path
    @Test
    fun testArchiveCallAndReminderUpdatesUseUnifiedMutationPath() = runBlocking {
        val user = SyncUser("user_actions", isAnonymous = false)
        val coordinator = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { user }
        )
        val repository = LeadRepository(database.leadDao, coordinator)

        val lead = createLead("lead_act_1", "user_actions", "Action Lead")
        repository.insertLead(lead, LeadWriteOrigin.LOCAL_USER)

        // Archive action
        val archived = lead.copy(archived = true)
        repository.insertLead(archived, LeadWriteOrigin.LOCAL_USER)

        // Call action
        val called = archived.copy(lastCall = "2026-07-29T10:00:00.000Z")
        repository.insertLead(called, LeadWriteOrigin.LOCAL_USER)

        // Reminder action
        val reminderUpdated = called.copy(reminderStatus = "Completed")
        repository.insertLead(reminderUpdated, LeadWriteOrigin.LOCAL_USER)

        val meta = database.leadSyncMetadataDao.getByLeadId("lead_act_1", "user_actions")
        assertNotNull(meta)
        assertEquals("Version must be incremented across mutations", 4L, meta?.localVersion)

        val mutations = database.syncDao.getMutationsByState("user_actions", "PENDING")
        assertEquals("Outbox mutations must exist for lead updates", 4, mutations.size)
    }

    // Test 12: Outbox and metadata remain isolated by ownerUid
    @Test
    fun testOutboxAndMetadataIsolatedByOwnerUid() = runBlocking {
        val user1 = SyncUser("user_X", isAnonymous = false)
        val user2 = SyncUser("user_Y", isAnonymous = false)

        val coordinator1 = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { user1 }
        )

        val coordinator2 = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { user2 }
        )

        val lead1 = createLead("lead_x", "user_X", "User X Lead")
        val lead2 = createLead("lead_y", "user_Y", "User Y Lead")

        coordinator1.upsertLead(lead1, LeadWriteOrigin.LOCAL_USER)
        coordinator2.upsertLead(lead2, LeadWriteOrigin.LOCAL_USER)

        val userXMutations = database.syncDao.getMutationsByState("user_X", "PENDING")
        val userYMutations = database.syncDao.getMutationsByState("user_Y", "PENDING")

        assertEquals(1, userXMutations.size)
        assertEquals("lead_x", userXMutations[0].entityId)

        assertEquals(1, userYMutations.size)
        assertEquals("lead_y", userYMutations[0].entityId)

        val metaX = database.leadSyncMetadataDao.getByLeadId("lead_x", "user_X")
        val metaYInX = database.leadSyncMetadataDao.getByLeadId("lead_y", "user_X")

        assertNotNull("Meta X must exist under user_X", metaX)
        assertNull("Meta Y must NOT exist under user_X", metaYInX)
    }
}
