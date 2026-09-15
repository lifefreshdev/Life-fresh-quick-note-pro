package com.example.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration8To9Test {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        context.deleteDatabase("test_migration_8_9.db")
    }

    @Test
    fun testMigration8To9PreservesDataAndBackfillsUnmatchedLeads() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val dbConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_8_9.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Leads table
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

                    // Lead sync metadata table at version 8
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `lead_sync_metadata` (
                            `leadId` TEXT NOT NULL, 
                            `localUpdatedAt` INTEGER NOT NULL, 
                            `remoteUpdatedAt` INTEGER, 
                            `syncState` TEXT NOT NULL, 
                            `deleted` INTEGER NOT NULL, 
                            `deletedAt` INTEGER, 
                            `lastError` TEXT, 
                            `retryCount` INTEGER NOT NULL, 
                            PRIMARY KEY(`leadId`)
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val sqliteDb = helperFactory.create(dbConfig).writableDatabase

        // Insert lead WITH existing metadata at version 8
        sqliteDb.execSQL("""
            INSERT INTO `leads` (
                id, name, mobile, diseases, otherDisease, relation, otherRelation,
                status, reminderDate, reminderTime, reminderNote, reminderStatus,
                notes, archived, lastCall, timestamp
            ) VALUES (
                'lead_with_meta', 'Lead One', '09171111111', '[]', '', 'Self', '',
                'Pending', '', '', '', 'Pending', '', 0, NULL, 1700000000000
            )
        """.trimIndent())

        sqliteDb.execSQL("""
            INSERT INTO `lead_sync_metadata` (
                leadId, localUpdatedAt, remoteUpdatedAt, syncState, deleted, deletedAt, lastError, retryCount
            ) VALUES (
                'lead_with_meta', 1700000000000, 1700000100000, 'SYNCED', 0, NULL, NULL, 0
            )
        """.trimIndent())

        // Insert lead WITHOUT metadata at version 8 (simulating legacy data)
        sqliteDb.execSQL("""
            INSERT INTO `leads` (
                id, name, mobile, diseases, otherDisease, relation, otherRelation,
                status, reminderDate, reminderTime, reminderNote, reminderStatus,
                notes, archived, lastCall, timestamp
            ) VALUES (
                'lead_unmatched', 'Lead Two', '09172222222', '[]', '', 'Self', '',
                'Pending', '', '', '', 'Pending', '', 0, NULL, 1700000200000
            )
        """.trimIndent())

        // Execute Migration 8 -> 9
        MIGRATION_8_9.migrate(sqliteDb)

        // 1. Verify lead_with_meta preserved existing metadata and gained default column values
        val cursorMeta1 = sqliteDb.query("SELECT leadId, syncState, localVersion, serverVersion, originDeviceId FROM lead_sync_metadata WHERE leadId = 'lead_with_meta'")
        assertTrue(cursorMeta1.moveToFirst())
        assertEquals("lead_with_meta", cursorMeta1.getString(0))
        assertEquals("SYNCED", cursorMeta1.getString(1))
        assertEquals(0L, cursorMeta1.getLong(2)) // localVersion default = 0
        assertTrue(cursorMeta1.isNull(3)) // serverVersion default = null
        assertEquals("", cursorMeta1.getString(4)) // originDeviceId default = ""
        cursorMeta1.close()

        // 2. Verify lead_unmatched was backfilled into lead_sync_metadata
        val cursorMeta2 = sqliteDb.query("SELECT leadId, syncState, localVersion, localUpdatedAt FROM lead_sync_metadata WHERE leadId = 'lead_unmatched'")
        assertTrue(cursorMeta2.moveToFirst())
        assertEquals("lead_unmatched", cursorMeta2.getString(0))
        assertEquals("PENDING", cursorMeta2.getString(1))
        assertEquals(1L, cursorMeta2.getLong(2)) // localVersion backfill = 1
        assertEquals(1700000200000L, cursorMeta2.getLong(3)) // localUpdatedAt = lead timestamp
        cursorMeta2.close()

        // 3. Verify sync_outbox, sync_conflicts, sync_checkpoint tables exist
        val cursorOutbox = sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('sync_outbox', 'sync_conflicts', 'sync_checkpoint')")
        var tableCount = 0
        while (cursorOutbox.moveToNext()) {
            tableCount++
        }
        cursorOutbox.close()
        assertEquals(3, tableCount)

        sqliteDb.close()
    }
}
