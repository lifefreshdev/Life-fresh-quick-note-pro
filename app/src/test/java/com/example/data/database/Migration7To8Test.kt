package com.example.data.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration7To8Test {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun testMigration7To8PreservesLeadDataAndCreatesMetadataTable() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val dbConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_7_8.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `leads` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `mobile` TEXT NOT NULL,
                            `diseases` TEXT NOT NULL,
                            `otherDisease` TEXT NOT NULL,
                            `relation` TEXT NOT NULL,
                            `otherRelation` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `reminderDate` TEXT NOT NULL,
                            `reminderTime` TEXT NOT NULL,
                            `reminderNote` TEXT NOT NULL,
                            `reminderStatus` TEXT NOT NULL,
                            `notes` TEXT NOT NULL,
                            `archived` INTEGER NOT NULL,
                            `lastCall` TEXT,
                            `timestamp` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `ai_chat_sessions` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `createdTimestamp` INTEGER NOT NULL,
                            `updatedTimestamp` INTEGER NOT NULL,
                            `isPinned` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `ai_chat_messages` (
                            `id` TEXT NOT NULL,
                            `sessionId` TEXT NOT NULL,
                            `text` TEXT NOT NULL,
                            `sender` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `isError` INTEGER NOT NULL,
                            `isOfflineWarning` INTEGER NOT NULL,
                            `isConfirmation` INTEGER NOT NULL,
                            `actionCardType` TEXT,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val sqliteDb = helperFactory.create(dbConfig).writableDatabase

        // Insert test lead at version 7
        sqliteDb.execSQL("""
            INSERT INTO `leads` (
                id, name, mobile, diseases, otherDisease, relation, otherRelation,
                status, reminderDate, reminderTime, reminderNote, reminderStatus,
                notes, archived, lastCall, timestamp
            ) VALUES (
                'lead_101', 'John Doe', '09171234567', '["Diabetes"]', '', 'Self', '',
                'Pending', '2026-08-01', '10:00', 'Follow up call', 'Pending',
                'VIP lead', 0, NULL, 1700000000000
            )
        """.trimIndent())

        // Run Migration 7 -> 8
        MIGRATION_7_8.migrate(sqliteDb)

        // Verify lead data survived migration
        val cursor = sqliteDb.query("SELECT id, name, mobile FROM leads WHERE id = 'lead_101'")
        assertTrue(cursor.moveToFirst())
        assertEquals("lead_101", cursor.getString(0))
        assertEquals("John Doe", cursor.getString(1))
        assertEquals("09171234567", cursor.getString(2))
        cursor.close()

        // Verify lead_sync_metadata table exists and works
        sqliteDb.execSQL("""
            INSERT INTO `lead_sync_metadata` (
                leadId, localUpdatedAt, remoteUpdatedAt, syncState, deleted, deletedAt, lastError, retryCount
            ) VALUES (
                'lead_101', 1700000000000, NULL, 'PENDING', 0, NULL, NULL, 0
            )
        """.trimIndent())

        val metaCursor = sqliteDb.query("SELECT leadId, syncState FROM lead_sync_metadata WHERE leadId = 'lead_101'")
        assertTrue(metaCursor.moveToFirst())
        assertEquals("lead_101", metaCursor.getString(0))
        assertEquals("PENDING", metaCursor.getString(1))
        metaCursor.close()

        sqliteDb.close()
    }

    @Test
    fun testLeadSyncMetadataDaoOperationsInAppDatabaseV8() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val leadDao = database.leadDao
        val metadataDao = database.leadSyncMetadataDao

        val testLead = LeadEntity(
            id = "lead_202",
            name = "Jane Smith",
            mobile = "09189876543",
            diseases = "[]",
            otherDisease = "",
            relation = "Client",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending"
        )
        leadDao.insertLead(testLead)

        val metadata = LeadSyncMetadataEntity(
            leadId = "lead_202",
            localUpdatedAt = 1700000000000L,
            syncState = "PENDING"
        )
        metadataDao.insertOrUpdate(metadata)

        // Verify query
        val fetchedMeta = metadataDao.getByLeadId("lead_202", "")
        assertNotNull(fetchedMeta)
        assertEquals("lead_202", fetchedMeta?.leadId)
        assertEquals("PENDING", fetchedMeta?.syncState)

        // Verify pending query
        val pendingItems = metadataDao.getPendingSyncItems("")
        assertEquals(1, pendingItems.size)

        // Test markSynced
        metadataDao.markSynced("lead_202", "", 1700000500000L)
        val syncedMeta = metadataDao.getByLeadId("lead_202", "")
        assertEquals("SYNCED", syncedMeta?.syncState)
        assertEquals(1700000500000L, syncedMeta?.remoteUpdatedAt)
        assertEquals(0, syncedMeta?.retryCount)
        assertNull(syncedMeta?.lastError)

        // Delete the lead entity locally but keep tombstone metadata
        leadDao.deleteLeadById("lead_202", "")
        assertNull(leadDao.getLeadById("lead_202", ""))

        // Tombstone metadata remains independently
        metadataDao.markDeleted("lead_202", "", 1700001000000L)
        val tombstone = metadataDao.getByLeadId("lead_202", "")
        assertNotNull(tombstone)
        assertTrue(tombstone!!.deleted)
        assertEquals("PENDING", tombstone.syncState)
    }
}
