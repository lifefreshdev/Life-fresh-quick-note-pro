package com.example.audio

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActiveAccountStore
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import com.example.sync.LeadSyncMutationCoordinator
import com.example.sync.LeadWriteOrigin
import com.example.sync.SyncPreferences
import com.example.sync.SyncRepository
import com.example.sync.SyncUser
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
class GuestReminderAlarmOwnershipTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var prefs: SyncPreferences

    private class FakeRemoteSyncDataSource : RemoteSyncDataSource {
        override suspend fun push(uid: String, mutations: List<RemoteMutation>): PushBatchResult {
            throw AssertionError("Remote push must NEVER be called for guest user")
        }
        override suspend fun pull(uid: String, cursor: String?, pageSize: Int): PullPage {
            throw AssertionError("Remote pull must NEVER be called for guest user")
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = SyncPreferences(context)
        ActiveAccountStore.clearActiveUid(context)
    }

    @After
    fun tearDown() {
        ActiveAccountStore.clearActiveUid(context)
        if (database.isOpen) {
            database.close()
        }
    }

    @Test
    fun testActiveAccountStore_persistsAndRetrievesAnonymousUid() {
        val anonymousGuestUid = "guest_anon_abc123"
        assertEquals("", ActiveAccountStore.getActiveUid(context))

        ActiveAccountStore.setActiveUid(context, anonymousGuestUid)
        assertEquals(anonymousGuestUid, ActiveAccountStore.getActiveUid(context))

        ActiveAccountStore.clearActiveUid(context)
        assertEquals("", ActiveAccountStore.getActiveUid(context))
    }

    @Test
    fun testGuestLeadReminder_localOnlyAndZeroCloudMutations() = runBlocking {
        val guestUid = "guest_anon_789"
        ActiveAccountStore.setActiveUid(context, guestUid)

        val guestUser = SyncUser(uid = guestUid, isAnonymous = true)
        val coordinator = LeadSyncMutationCoordinator(
            context = context,
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            syncPreferences = prefs,
            currentUserProvider = { guestUser }
        )

        val lead = LeadEntity(
            id = "lead_guest_rem_1",
            name = "Guest Client",
            mobile = "9876543210",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "2026-09-01",
            reminderTime = "10:30",
            reminderNote = "Guest follow-up",
            reminderStatus = "Pending",
            notes = "Test lead notes",
            archived = false,
            lastCall = null,
            timestamp = System.currentTimeMillis(),
            ownerUid = guestUid
        )

        // 1. Upsert lead locally
        coordinator.upsertLead(lead, LeadWriteOrigin.LOCAL_USER)

        // 2. Verify lead exists in local database with correct owner UID
        val storedLead = database.leadDao.getLeadById("lead_guest_rem_1", guestUid)
        assertNotNull("Guest lead must exist in Room", storedLead)
        assertEquals("Guest Client", storedLead?.name)
        assertEquals(guestUid, storedLead?.ownerUid)

        // 3. Verify ActiveAccountStore matches the lead's ownerUid for alarm verification
        assertEquals(storedLead?.ownerUid, ActiveAccountStore.getActiveUid(context))

        // 4. Verify ZERO cloud mutations and ZERO metadata are created for guest
        val mutations = database.syncDao.getMutationsByState(guestUid, "PENDING")
        assertTrue("Guest operations must produce zero outbox mutations", mutations.isEmpty())
        val meta = database.leadSyncMetadataDao.getByLeadId("lead_guest_rem_1", guestUid)
        assertNull("Guest operations must produce zero metadata records", meta)

        // 5. Verify system reminder status update is also local-only
        val triggered = lead.copy(reminderStatus = "Triggered")
        coordinator.upsertLead(triggered, LeadWriteOrigin.SYSTEM_REMINDER)
        val updatedLead = database.leadDao.getLeadById("lead_guest_rem_1", guestUid)
        assertEquals("Triggered", updatedLead?.reminderStatus)

        val mutationsAfterTrigger = database.syncDao.getMutationsByState(guestUid, "PENDING")
        assertTrue("Triggered update for guest must not produce sync mutations", mutationsAfterTrigger.isEmpty())
    }

    @Test
    fun testGuestSync_explicitlyBlockedWithAuthRequired() = runBlocking {
        val guestUid = "guest_anon_xyz"
        val guestUser = SyncUser(uid = guestUid, isAnonymous = true)
        val localStore = SyncLocalStore(database, database.syncDao, database.leadSyncMetadataDao)

        val syncRepository = SyncRepository(
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            localStore = localStore,
            remoteDataSource = FakeRemoteSyncDataSource(),
            syncPreferences = prefs,
            currentUserProvider = { guestUser }
        )

        val result = syncRepository.sync(SyncTrigger.MANUAL, guestUid)
        assertTrue("Manual sync must return AuthRequired for guest user", result is SyncRunResult.AuthRequired)
    }
}
