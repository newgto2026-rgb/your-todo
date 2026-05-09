package com.neo.yourtodo.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `category` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `colorHex` TEXT,
                    `icon` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_name` ON `category` (`name`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `todo_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `isDone` INTEGER NOT NULL,
                    `dueDateEpochDay` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `categoryId` INTEGER,
                    FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `todo_new` (`id`, `title`, `isDone`, `dueDateEpochDay`, `createdAt`, `updatedAt`, `categoryId`)
                SELECT `id`, `title`, `isDone`, `dueDateEpochDay`, `createdAt`, `updatedAt`, NULL
                FROM `todo`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `todo`")
            db.execSQL("ALTER TABLE `todo_new` RENAME TO `todo`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_categoryId` ON `todo` (`categoryId`)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reminder` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `note` TEXT,
                    `triggerAtEpochMillis` INTEGER NOT NULL,
                    `repeatType` TEXT NOT NULL,
                    `repeatDaysMask` INTEGER NOT NULL,
                    `isEnabled` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `lastTriggeredAtEpochMillis` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reminder_isEnabled_triggerAtEpochMillis` ON `reminder` (`isEnabled`, `triggerAtEpochMillis`)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_updatedAt` ON `reminder` (`updatedAt`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `reminderAtEpochMillis` INTEGER")
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `isReminderEnabled` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_todo_isReminderEnabled_reminderAtEpochMillis` ON `todo` (`isReminderEnabled`, `reminderAtEpochMillis`)"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `reminderRepeatType` TEXT NOT NULL DEFAULT 'NONE'")
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `reminderRepeatDaysMask` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `dueTimeMinutes` INTEGER")
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `reminderLeadMinutes` INTEGER")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `priority` TEXT NOT NULL DEFAULT 'MEDIUM'")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `serverId` TEXT")
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `clientId` TEXT")
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `ownerUserId` TEXT")
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `serverRevision` TEXT")
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `deletedAt` INTEGER")
            db.execSQL("ALTER TABLE `todo` ADD COLUMN `lastSyncError` TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_todo_ownerUserId_serverId` ON `todo` (`ownerUserId`, `serverId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_todo_ownerUserId_clientId` ON `todo` (`ownerUserId`, `clientId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_syncStatus` ON `todo` (`syncStatus`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `todo_outbox` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `ownerUserId` TEXT NOT NULL,
                    `clientMutationId` TEXT NOT NULL,
                    `todoLocalId` INTEGER,
                    `serverId` TEXT,
                    `clientId` TEXT,
                    `type` TEXT NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `retryCount` INTEGER NOT NULL DEFAULT 0,
                    `lastError` TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_todo_outbox_clientMutationId` ON `todo_outbox` (`clientMutationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_outbox_todoLocalId` ON `todo_outbox` (`todoLocalId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_outbox_ownerUserId_createdAt` ON `todo_outbox` (`ownerUserId`, `createdAt`)")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `assigned_todo` (
                    `ownerUserId` TEXT NOT NULL,
                    `id` TEXT NOT NULL,
                    `cacheKey` TEXT NOT NULL,
                    `bundleId` TEXT,
                    `title` TEXT NOT NULL,
                    `description` TEXT,
                    `dueDateEpochDay` INTEGER,
                    `dueTimeMinutes` INTEGER,
                    `priority` TEXT NOT NULL,
                    `category` TEXT,
                    `status` TEXT NOT NULL,
                    `terminalReason` TEXT,
                    `progressPercent` INTEGER NOT NULL,
                    `senderUserId` TEXT,
                    `senderNickname` TEXT,
                    `receiverUserId` TEXT,
                    `receiverNickname` TEXT,
                    `reminderAt` TEXT,
                    `reminderEnabled` INTEGER,
                    `createdAtEpochMillis` INTEGER,
                    `completedAtEpochMillis` INTEGER,
                    `receivedCached` INTEGER NOT NULL,
                    `sentCached` INTEGER NOT NULL,
                    `cacheUpdatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`ownerUserId`, `id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `assigned_todo_checklist_item` (
                    `ownerUserId` TEXT NOT NULL,
                    `assignedTodoId` TEXT NOT NULL,
                    `assignedTodoCacheKey` TEXT NOT NULL,
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`ownerUserId`, `assignedTodoId`, `id`),
                    FOREIGN KEY(`ownerUserId`, `assignedTodoId`)
                        REFERENCES `assigned_todo`(`ownerUserId`, `id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_assigned_todo_ownerUserId_receivedCached_status` ON `assigned_todo` (`ownerUserId`, `receivedCached`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_assigned_todo_ownerUserId_sentCached_status` ON `assigned_todo` (`ownerUserId`, `sentCached`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_assigned_todo_ownerUserId_senderUserId_status` ON `assigned_todo` (`ownerUserId`, `senderUserId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_assigned_todo_ownerUserId_receiverUserId_status` ON `assigned_todo` (`ownerUserId`, `receiverUserId`, `status`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_assigned_todo_cacheKey` ON `assigned_todo` (`cacheKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_assigned_todo_checklist_item_ownerUserId_assignedTodoId` ON `assigned_todo_checklist_item` (`ownerUserId`, `assignedTodoId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_assigned_todo_checklist_item_assignedTodoCacheKey` ON `assigned_todo_checklist_item` (`assignedTodoCacheKey`)")
        }
    }
}
