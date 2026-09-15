package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import com.example.sync.local.SyncCheckpointEntity
import com.example.sync.local.SyncConflictEntity
import com.example.sync.local.SyncDao
import com.example.sync.local.SyncOutboxEntity

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS review_queue")
        db.execSQL("DROP TABLE IF EXISTS audit_entries")
        db.execSQL("DROP TABLE IF EXISTS knowledge_cache")
        db.execSQL("DROP TABLE IF EXISTS chat_sessions")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ai_chat_sessions` (
                `id` TEXT NOT NULL, 
                `title` TEXT NOT NULL, 
                `createdTimestamp` INTEGER NOT NULL, 
                `updatedTimestamp` INTEGER NOT NULL, 
                `isPinned` INTEGER NOT NULL, 
                PRIMARY KEY(`id`)
            )
        """)
        
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
                PRIMARY KEY(`id`), 
                FOREIGN KEY(`sessionId`) REFERENCES `ai_chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
        """)
        
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_chat_messages_sessionId` ON `ai_chat_messages` (`sessionId`)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
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
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_outbox` (
                `mutationId` TEXT NOT NULL, 
                `entityType` TEXT NOT NULL, 
                `entityId` TEXT NOT NULL, 
                `operation` TEXT NOT NULL, 
                `localVersion` INTEGER NOT NULL, 
                `payloadHash` TEXT NOT NULL, 
                `state` TEXT NOT NULL, 
                `attemptCount` INTEGER NOT NULL, 
                `createdAtUtc` INTEGER NOT NULL, 
                `updatedAtUtc` INTEGER NOT NULL, 
                `nextAttemptAtUtc` INTEGER, 
                `lastErrorClass` TEXT, 
                `lastErrorMessage` TEXT, 
                PRIMARY KEY(`mutationId`)
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_state` ON `sync_outbox` (`state`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_entityType_entityId` ON `sync_outbox` (`entityType`, `entityId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_nextAttemptAtUtc` ON `sync_outbox` (`nextAttemptAtUtc`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_createdAtUtc` ON `sync_outbox` (`createdAtUtc`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_entityType_entityId_operation_localVersion` ON `sync_outbox` (`entityType`, `entityId`, `operation`, `localVersion`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_conflicts` (
                `conflictId` TEXT NOT NULL, 
                `entityType` TEXT NOT NULL, 
                `entityId` TEXT NOT NULL, 
                `localPayload` TEXT NOT NULL, 
                `remotePayload` TEXT NOT NULL, 
                `winner` TEXT NOT NULL, 
                `reason` TEXT NOT NULL, 
                `detectedAtUtc` INTEGER NOT NULL, 
                `resolved` INTEGER NOT NULL, 
                `resolvedAtUtc` INTEGER, 
                PRIMARY KEY(`conflictId`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_checkpoint` (
                `scopeId` TEXT NOT NULL, 
                `remoteCursor` TEXT, 
                `lastRunId` TEXT, 
                `lastRunStartedAtUtc` INTEGER NOT NULL, 
                `lastRunCompletedAtUtc` INTEGER NOT NULL, 
                `lastSuccessfulSyncAtUtc` INTEGER NOT NULL, 
                `lastPhase` TEXT NOT NULL, 
                `lastMessage` TEXT, 
                `schemaVersion` INTEGER NOT NULL, 
                PRIMARY KEY(`scopeId`)
            )
        """.trimIndent())

        db.execSQL("ALTER TABLE `lead_sync_metadata` ADD COLUMN `localVersion` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `lead_sync_metadata` ADD COLUMN `serverVersion` INTEGER NULL")
        db.execSQL("ALTER TABLE `lead_sync_metadata` ADD COLUMN `originDeviceId` TEXT NOT NULL DEFAULT ''")

        db.execSQL("""
            INSERT OR IGNORE INTO `lead_sync_metadata` (
                `leadId`, `localUpdatedAt`, `remoteUpdatedAt`, `syncState`, `deleted`, `deletedAt`, `lastError`, `retryCount`, `localVersion`, `serverVersion`, `originDeviceId`
            )
            SELECT 
                `id`, 
                `timestamp`, 
                NULL, 
                'PENDING', 
                0, 
                NULL, 
                NULL, 
                0, 
                1, 
                NULL, 
                '' 
            FROM `leads`
            WHERE `id` NOT IN (SELECT `leadId` FROM `lead_sync_metadata`)
        """.trimIndent())
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE leads ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Rebuild leads table with composite PK (ownerUid, id)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `leads_new` (
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
                `notes` TEXT NOT NULL DEFAULT '',
                `archived` INTEGER NOT NULL DEFAULT 0,
                `lastCall` TEXT,
                `timestamp` INTEGER NOT NULL DEFAULT 0,
                `ownerUid` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`ownerUid`, `id`)
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `leads_new` (
                `id`, `name`, `mobile`, `diseases`, `otherDisease`, `relation`, `otherRelation`,
                `status`, `reminderDate`, `reminderTime`, `reminderNote`, `reminderStatus`,
                `notes`, `archived`, `lastCall`, `timestamp`, `ownerUid`
            )
            SELECT 
                `id`, `name`, `mobile`, `diseases`, `otherDisease`, `relation`, `otherRelation`,
                `status`, `reminderDate`, `reminderTime`, `reminderNote`, `reminderStatus`,
                `notes`, `archived`, `lastCall`, `timestamp`, `ownerUid`
            FROM `leads`
        """.trimIndent())

        db.execSQL("DROP TABLE `leads`")
        db.execSQL("ALTER TABLE `leads_new` RENAME TO `leads`")

        // 2. Rebuild lead_sync_metadata table with composite PK (ownerUid, leadId)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `lead_sync_metadata_new` (
                `ownerUid` TEXT NOT NULL DEFAULT '',
                `leadId` TEXT NOT NULL,
                `localUpdatedAt` INTEGER NOT NULL,
                `remoteUpdatedAt` INTEGER,
                `syncState` TEXT NOT NULL,
                `deleted` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                `lastError` TEXT,
                `retryCount` INTEGER NOT NULL,
                `localVersion` INTEGER NOT NULL DEFAULT 0,
                `serverVersion` INTEGER,
                `originDeviceId` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`ownerUid`, `leadId`)
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `lead_sync_metadata_new` (
                `ownerUid`, `leadId`, `localUpdatedAt`, `remoteUpdatedAt`, `syncState`,
                `deleted`, `deletedAt`, `lastError`, `retryCount`, `localVersion`, `serverVersion`, `originDeviceId`
            )
            SELECT 
                COALESCE(l.`ownerUid`, ''),
                m.`leadId`,
                m.`localUpdatedAt`,
                m.`remoteUpdatedAt`,
                m.`syncState`,
                m.`deleted`,
                m.`deletedAt`,
                m.`lastError`,
                m.`retryCount`,
                m.`localVersion`,
                m.`serverVersion`,
                m.`originDeviceId`
            FROM `lead_sync_metadata` m
            LEFT JOIN `leads` l ON m.`leadId` = l.`id`
        """.trimIndent())

        db.execSQL("DROP TABLE `lead_sync_metadata`")
        db.execSQL("ALTER TABLE `lead_sync_metadata_new` RENAME TO `lead_sync_metadata`")

        // 3. Rebuild sync_outbox table with ownerUid column and updated unique index
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_outbox_new` (
                `mutationId` TEXT NOT NULL,
                `ownerUid` TEXT NOT NULL DEFAULT '',
                `entityType` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `operation` TEXT NOT NULL,
                `localVersion` INTEGER NOT NULL,
                `payloadHash` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `attemptCount` INTEGER NOT NULL,
                `createdAtUtc` INTEGER NOT NULL,
                `updatedAtUtc` INTEGER NOT NULL,
                `nextAttemptAtUtc` INTEGER,
                `lastErrorClass` TEXT,
                `lastErrorMessage` TEXT,
                PRIMARY KEY(`mutationId`)
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `sync_outbox_new` (
                `mutationId`, `ownerUid`, `entityType`, `entityId`, `operation`, `localVersion`,
                `payloadHash`, `state`, `attemptCount`, `createdAtUtc`, `updatedAtUtc`,
                `nextAttemptAtUtc`, `lastErrorClass`, `lastErrorMessage`
            )
            SELECT 
                o.`mutationId`,
                COALESCE(l.`ownerUid`, m.`ownerUid`, ''),
                o.`entityType`,
                o.`entityId`,
                o.`operation`,
                o.`localVersion`,
                o.`payloadHash`,
                CASE WHEN COALESCE(l.`ownerUid`, m.`ownerUid`, '') = '' THEN 'QUARANTINED' ELSE o.`state` END,
                o.`attemptCount`,
                o.`createdAtUtc`,
                o.`updatedAtUtc`,
                o.`nextAttemptAtUtc`,
                CASE WHEN COALESCE(l.`ownerUid`, m.`ownerUid`, '') = '' THEN 'MIGRATION_UNOWNED_MUTATION' ELSE o.`lastErrorClass` END,
                CASE WHEN COALESCE(l.`ownerUid`, m.`ownerUid`, '') = '' THEN 'Unowned mutation quarantined during migration' ELSE o.`lastErrorMessage` END
            FROM `sync_outbox` o
            LEFT JOIN `leads` l ON o.`entityId` = l.`id`
            LEFT JOIN `lead_sync_metadata` m ON o.`entityId` = m.`leadId`
        """.trimIndent())

        db.execSQL("DROP TABLE `sync_outbox`")
        db.execSQL("ALTER TABLE `sync_outbox_new` RENAME TO `sync_outbox`")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_state` ON `sync_outbox` (`state`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_entityType_entityId` ON `sync_outbox` (`entityType`, `entityId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_nextAttemptAtUtc` ON `sync_outbox` (`nextAttemptAtUtc`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_createdAtUtc` ON `sync_outbox` (`createdAtUtc`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_ownerUid` ON `sync_outbox` (`ownerUid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_ownerUid_entityType_entityId_operation_localVersion` ON `sync_outbox` (`ownerUid`, `entityType`, `entityId`, `operation`, `localVersion`)")

        // 4. Rebuild sync_conflicts table with ownerUid column
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_conflicts_new` (
                `conflictId` TEXT NOT NULL,
                `ownerUid` TEXT NOT NULL DEFAULT '',
                `entityType` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `localPayload` TEXT NOT NULL,
                `remotePayload` TEXT NOT NULL,
                `winner` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `detectedAtUtc` INTEGER NOT NULL,
                `resolved` INTEGER NOT NULL,
                `resolvedAtUtc` INTEGER,
                PRIMARY KEY(`conflictId`)
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `sync_conflicts_new` (
                `conflictId`, `ownerUid`, `entityType`, `entityId`, `localPayload`, `remotePayload`,
                `winner`, `reason`, `detectedAtUtc`, `resolved`, `resolvedAtUtc`
            )
            SELECT 
                c.`conflictId`,
                COALESCE(l.`ownerUid`, ''),
                c.`entityType`,
                c.`entityId`,
                c.`localPayload`,
                c.`remotePayload`,
                c.`winner`,
                c.`reason`,
                c.`detectedAtUtc`,
                c.`resolved`,
                c.`resolvedAtUtc`
            FROM `sync_conflicts` c
            LEFT JOIN `leads` l ON c.`entityId` = l.`id`
        """.trimIndent())

        db.execSQL("DROP TABLE `sync_conflicts`")
        db.execSQL("ALTER TABLE `sync_conflicts_new` RENAME TO `sync_conflicts`")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Rebuild ai_chat_sessions with ownerUid column and composite PK (ownerUid, id)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ai_chat_sessions_new` (
                `ownerUid` TEXT NOT NULL DEFAULT '',
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `createdTimestamp` INTEGER NOT NULL,
                `updatedTimestamp` INTEGER NOT NULL,
                `isPinned` INTEGER NOT NULL,
                PRIMARY KEY(`ownerUid`, `id`)
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `ai_chat_sessions_new` (
                `ownerUid`, `id`, `title`, `createdTimestamp`, `updatedTimestamp`, `isPinned`
            )
            SELECT 
                '', `id`, `title`, `createdTimestamp`, `updatedTimestamp`, `isPinned`
            FROM `ai_chat_sessions`
        """.trimIndent())

        db.execSQL("DROP TABLE `ai_chat_sessions`")
        db.execSQL("ALTER TABLE `ai_chat_sessions_new` RENAME TO `ai_chat_sessions`")

        // 2. Rebuild ai_chat_messages with ownerUid column, composite PK (ownerUid, id), composite FK and indices
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ai_chat_messages_new` (
                `ownerUid` TEXT NOT NULL DEFAULT '',
                `id` TEXT NOT NULL,
                `sessionId` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `sender` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `isError` INTEGER NOT NULL,
                `isOfflineWarning` INTEGER NOT NULL,
                `isConfirmation` INTEGER NOT NULL,
                `actionCardType` TEXT,
                PRIMARY KEY(`ownerUid`, `id`),
                FOREIGN KEY(`ownerUid`, `sessionId`) REFERENCES `ai_chat_sessions`(`ownerUid`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `ai_chat_messages_new` (
                `ownerUid`, `id`, `sessionId`, `text`, `sender`, `timestamp`,
                `isError`, `isOfflineWarning`, `isConfirmation`, `actionCardType`
            )
            SELECT 
                '', `id`, `sessionId`, `text`, `sender`, `timestamp`,
                `isError`, `isOfflineWarning`, `isConfirmation`, `actionCardType`
            FROM `ai_chat_messages`
        """.trimIndent())

        db.execSQL("DROP TABLE `ai_chat_messages`")
        db.execSQL("ALTER TABLE `ai_chat_messages_new` RENAME TO `ai_chat_messages`")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_chat_messages_ownerUid_sessionId` ON `ai_chat_messages` (`ownerUid`, `sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_chat_messages_sessionId` ON `ai_chat_messages` (`sessionId`)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v12 did not persist the actual time of note/reminder edits.
        // Keep legacy rows at 0 rather than inventing historical timestamps.
        db.execSQL("ALTER TABLE `leads` ADD COLUMN `notesUpdatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `leads` ADD COLUMN `reminderUpdatedAt` INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        LeadEntity::class, 
        AIChatSessionEntity::class, 
        AIChatMessageEntity::class, 
        LeadSyncMetadataEntity::class,
        SyncOutboxEntity::class,
        SyncConflictEntity::class,
        SyncCheckpointEntity::class
    ], 
    version = 13, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val leadDao: LeadDao
    abstract val aiChatDao: AIChatDao
    abstract val leadSyncMetadataDao: LeadSyncMetadataDao
    abstract val syncDao: SyncDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DATABASE_NAME = "lifefresh_database"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

