package com.example.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
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
class SyncRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var localStore: SyncLocalStore
    private lateinit var prefs: SyncPreferences
    private lateinit var fakeRemoteDataSource: FakeRemoteSyncDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        prefs = SyncPreferences(context)
        prefs.setAutomaticSyncEnabled(false)

        localStore = SyncLocalStore(database, database.syncDao, database.leadSyncMetadataDao)
        fakeRemoteDataSource = FakeRemoteSyncDataSource()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sync_whenNoUser_returnsAuthRequired() = runBlocking {
        val repository = SyncRepository(
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            localStore = localStore,
            remoteDataSource = fakeRemoteDataSource,
            syncPreferences = prefs,
            currentUserProvider = { null }
        )

        val result = repository.sync(SyncTrigger.MANUAL)
        assertTrue(result is SyncRunResult.AuthRequired)
    }

    @Test
    fun sync_whenAutomaticDisabled_andPeriodicTrigger_returnsDisabled() = runBlocking {
        prefs.setAutomaticSyncEnabled(false)

        val repository = SyncRepository(
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            localStore = localStore,
            remoteDataSource = fakeRemoteDataSource,
            syncPreferences = prefs,
            currentUserProvider = { SyncUser("user_123") }
        )

        val result = repository.sync(SyncTrigger.PERIODIC)
        assertTrue(result is SyncRunResult.AutomaticSyncDisabled)
    }

    @Test
    fun sync_manualTrigger_pushesLocalOutboxAndPullsRemoteRecords() = runBlocking {
        prefs.setAutomaticSyncEnabled(false)

        // 1. Insert local lead & outbox mutation
        val lead = LeadEntity(
            id = "local_1",
            ownerUid = "user_123",
            name = "Local Lead",
            mobile = "1112223333",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending"
        )
        database.leadDao.insertLead(lead)
        localStore.enqueueLeadUpsert("user_123", "local_1", "hash1", 1L, System.currentTimeMillis())

        // 2. Setup fake remote datasource with a remote record
        fakeRemoteDataSource.remoteRecords.add(
            RemoteLeadRecord(
                id = "remote_1",
                name = "Remote Lead",
                mobile = "9998887777",
                diseases = "[]",
                status = "Pending",
                serverVersion = 1L,
                updatedAt = System.currentTimeMillis()
            )
        )

        val repository = SyncRepository(
            database = database,
            leadDao = database.leadDao,
            metadataDao = database.leadSyncMetadataDao,
            syncDao = database.syncDao,
            localStore = localStore,
            remoteDataSource = fakeRemoteDataSource,
            syncPreferences = prefs,
            currentUserProvider = { SyncUser("user_123") }
        )

        val result = repository.sync(SyncTrigger.MANUAL)
        assertTrue("Expected Success result, got $result", result is SyncRunResult.Success)

        val summary = (result as SyncRunResult.Success).summary
        assertEquals(1, summary.pushed)
        assertEquals(1, summary.pulled)

        // Verify remote record was inserted into Room
        val pulledLead = database.leadDao.getLeadById("remote_1", "user_123")
        assertNotNull(pulledLead)
        assertEquals("Remote Lead", pulledLead?.name)
    }

    private class FakeRemoteSyncDataSource : RemoteSyncDataSource {
        val remoteRecords = mutableListOf<RemoteLeadRecord>()

        override suspend fun push(uid: String, mutations: List<RemoteMutation>): PushBatchResult {
            val items = mutations.map { m ->
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
}
