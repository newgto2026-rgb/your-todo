package com.neo.yourtodo.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.database.AppDatabaseMigrations.MIGRATION_7_8
import com.neo.yourtodo.core.database.AppDatabaseMigrations.MIGRATION_8_9
import com.neo.yourtodo.core.database.AppDatabaseMigrations.MIGRATION_9_10
import com.neo.yourtodo.core.database.AppDatabaseMigrations.MIGRATION_10_11
import com.neo.yourtodo.core.database.AppDatabaseMigrations.MIGRATION_11_12
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    @Test
    fun migration7To8_existingTodosBecomeLocalOnlyAndOutboxStartsEmpty() {
        val testDb = createVersion7Database(TEST_DB)
        testDb.database.apply {
            execSQL(
                """
                INSERT INTO todo (
                    id, title, isDone, dueDateEpochDay, createdAt, updatedAt, categoryId,
                    reminderAtEpochMillis, isReminderEnabled, reminderRepeatType,
                    reminderRepeatDaysMask, dueTimeMinutes, reminderLeadMinutes, priority
                ) VALUES (
                    1, 'existing local todo', 0, NULL, 100, 100, NULL,
                    NULL, 0, 'NONE', 0, NULL, NULL, 'MEDIUM'
                )
                """.trimIndent()
            )
        }

        val migrated = testDb.database.apply { MIGRATION_7_8.migrate(this) }

        migrated.query(
            """
            SELECT title, syncStatus, serverId, clientId, ownerUserId, serverRevision, deletedAt, lastSyncError
            FROM todo
            WHERE id = 1
            """.trimIndent()
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("existing local todo")
            assertThat(cursor.getString(1)).isEqualTo("LOCAL_ONLY")
            assertThat(cursor.isNull(2)).isTrue()
            assertThat(cursor.isNull(3)).isTrue()
            assertThat(cursor.isNull(4)).isTrue()
            assertThat(cursor.isNull(5)).isTrue()
            assertThat(cursor.isNull(6)).isTrue()
            assertThat(cursor.isNull(7)).isTrue()
        }
        migrated.query("SELECT COUNT(*) FROM todo_outbox").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        testDb.close()
    }

    @Test
    fun migration7To8_createsSyncColumnsOutboxTableAndIndexes() {
        val testDb = createVersion7Database("${TEST_DB}_schema")

        val migrated = testDb.database.apply { MIGRATION_7_8.migrate(this) }

        assertThat(migrated.columnNames("todo")).containsAtLeast(
            "serverId",
            "clientId",
            "ownerUserId",
            "syncStatus",
            "serverRevision",
            "deletedAt",
            "lastSyncError"
        )
        assertThat(migrated.columnNames("todo_outbox")).containsAtLeast(
            "ownerUserId",
            "clientMutationId",
            "todoLocalId",
            "serverId",
            "clientId",
            "type",
            "payloadJson",
            "createdAt",
            "retryCount",
            "lastError"
        )
        assertThat(migrated.indexNames("todo")).containsAtLeast(
            "index_todo_ownerUserId_serverId",
            "index_todo_ownerUserId_clientId",
            "index_todo_syncStatus"
        )
        assertThat(migrated.indexNames("todo_outbox")).containsAtLeast(
            "index_todo_outbox_clientMutationId",
            "index_todo_outbox_todoLocalId",
            "index_todo_outbox_ownerUserId_createdAt"
        )
        testDb.close()
    }

    @Test
    fun migration8To9_createsAssignedTodoCacheTablesAndIndexes() {
        val testDb = createVersion8Database("${TEST_DB}_assigned_cache")

        val migrated = testDb.database.apply { MIGRATION_8_9.migrate(this) }

        assertThat(migrated.columnNames("assigned_todo")).containsAtLeast(
            "id",
            "ownerUserId",
            "cacheKey",
            "bundleId",
            "title",
            "dueDateEpochDay",
            "dueTimeMinutes",
            "priority",
            "status",
            "progressPercent",
            "senderUserId",
            "receiverUserId",
            "reminderAt",
            "receivedCached",
            "sentCached",
            "cacheUpdatedAt"
        )
        assertThat(migrated.columnNames("assigned_todo_checklist_item")).containsAtLeast(
            "ownerUserId",
            "assignedTodoId",
            "assignedTodoCacheKey",
            "id",
            "title",
            "completed",
            "sortOrder"
        )
        assertThat(migrated.indexNames("assigned_todo")).containsAtLeast(
            "index_assigned_todo_ownerUserId_receivedCached_status",
            "index_assigned_todo_ownerUserId_sentCached_status",
            "index_assigned_todo_ownerUserId_senderUserId_status",
            "index_assigned_todo_ownerUserId_receiverUserId_status",
            "index_assigned_todo_cacheKey"
        )
        assertThat(migrated.indexNames("assigned_todo_checklist_item"))
            .containsAtLeast(
                "index_assigned_todo_checklist_item_ownerUserId_assignedTodoId",
                "index_assigned_todo_checklist_item_assignedTodoCacheKey"
            )
        testDb.close()
    }

    @Test
    fun migration8To9_preservesExistingTodoAndOutboxRows() {
        val testDb = createVersion8Database("${TEST_DB}_assigned_cache_preserve")
        testDb.database.apply {
            execSQL(
                """
                INSERT INTO todo (
                    id, title, isDone, dueDateEpochDay, createdAt, updatedAt, categoryId,
                    reminderAtEpochMillis, isReminderEnabled, reminderRepeatType,
                    reminderRepeatDaysMask, dueTimeMinutes, reminderLeadMinutes, priority,
                    serverId, clientId, ownerUserId, syncStatus, serverRevision, deletedAt, lastSyncError
                ) VALUES (
                    1, 'keep me', 0, NULL, 100, 100, NULL,
                    NULL, 0, 'NONE', 0, NULL, NULL, 'MEDIUM',
                    'server-1', 'client-1', 'user-1', 'SYNCED', 'rev-1', NULL, NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO todo_outbox (
                    id, ownerUserId, clientMutationId, todoLocalId, serverId, clientId,
                    type, payloadJson, createdAt, retryCount, lastError
                ) VALUES (
                    1, 'user-1', 'mutation-1', 1, 'server-1', 'client-1',
                    'UPDATE', '{}', 100, 0, NULL
                )
                """.trimIndent()
            )
        }

        val migrated = testDb.database.apply { MIGRATION_8_9.migrate(this) }

        migrated.query("SELECT title, ownerUserId, syncStatus FROM todo WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("keep me")
            assertThat(cursor.getString(1)).isEqualTo("user-1")
            assertThat(cursor.getString(2)).isEqualTo("SYNCED")
        }
        migrated.query("SELECT clientMutationId, ownerUserId FROM todo_outbox WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("mutation-1")
            assertThat(cursor.getString(1)).isEqualTo("user-1")
        }
        testDb.close()
    }

    @Test
    fun migration9To10_addsReceivedTaskHiddenDefaultAndIndex() {
        val testDb = createVersion8Database("${TEST_DB}_received_task_hidden")
        testDb.database.apply {
            MIGRATION_8_9.migrate(this)
            execSQL(
                """
                INSERT INTO assigned_todo (
                    ownerUserId, id, cacheKey, bundleId, title, description, dueDateEpochDay,
                    dueTimeMinutes, priority, category, status, terminalReason, progressPercent,
                    senderUserId, senderNickname, receiverUserId, receiverNickname, reminderAt,
                    reminderEnabled, createdAtEpochMillis, completedAtEpochMillis, receivedCached,
                    sentCached, cacheUpdatedAt
                ) VALUES (
                    'user-1', 'assigned-1', 'cache-1', NULL, 'done shared', NULL, NULL,
                    NULL, 'MEDIUM', NULL, 'DONE', NULL, 100,
                    'friend-1', 'tee', 'user-1', 'neo', NULL,
                    NULL, 100, 200, 1,
                    0, 300
                )
                """.trimIndent()
            )
        }

        val migrated = testDb.database.apply { MIGRATION_9_10.migrate(this) }

        assertThat(migrated.columnNames("assigned_todo")).contains("receivedTaskHidden")
        assertThat(migrated.indexNames("assigned_todo"))
            .contains("index_assigned_todo_ownerUserId_receivedTaskHidden_status")
        migrated.query("SELECT receivedTaskHidden FROM assigned_todo WHERE id = 'assigned-1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        testDb.close()
    }

    @Test
    fun migration10To11_addsAssignmentModeDefault() {
        val testDb = createVersion8Database("${TEST_DB}_assignment_mode")
        testDb.database.apply {
            MIGRATION_8_9.migrate(this)
            MIGRATION_9_10.migrate(this)
            execSQL(
                """
                INSERT INTO assigned_todo (
                    ownerUserId, id, cacheKey, bundleId, title, description, dueDateEpochDay,
                    dueTimeMinutes, priority, category, status, terminalReason, progressPercent,
                    senderUserId, senderNickname, receiverUserId, receiverNickname, reminderAt,
                    reminderEnabled, createdAtEpochMillis, completedAtEpochMillis, receivedCached,
                    sentCached, cacheUpdatedAt, receivedTaskHidden
                ) VALUES (
                    'user-1', 'assigned-1', 'cache-1', NULL, 'shared', NULL, NULL,
                    NULL, 'MEDIUM', NULL, 'ACCEPTED', NULL, 0,
                    'friend-1', 'tee', 'user-1', 'neo', NULL,
                    NULL, 100, NULL, 1,
                    0, 300, 0
                )
                """.trimIndent()
            )
        }

        val migrated = testDb.database.apply { MIGRATION_10_11.migrate(this) }

        assertThat(migrated.columnNames("assigned_todo")).contains("assignmentMode")
        migrated.query("SELECT assignmentMode FROM assigned_todo WHERE id = 'assigned-1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("REQUEST")
        }
        testDb.close()
    }

    @Test
    fun migration11To12_preservesAssignedTodoSchemaAndRows() {
        val testDb = createVersion8Database("${TEST_DB}_version_floor")
        testDb.database.apply {
            MIGRATION_8_9.migrate(this)
            MIGRATION_9_10.migrate(this)
            MIGRATION_10_11.migrate(this)
            execSQL(
                """
                INSERT INTO assigned_todo (
                    ownerUserId, id, cacheKey, bundleId, title, description, dueDateEpochDay,
                    dueTimeMinutes, priority, category, status, terminalReason, progressPercent,
                    senderUserId, senderNickname, receiverUserId, receiverNickname, reminderAt,
                    reminderEnabled, createdAtEpochMillis, completedAtEpochMillis, receivedCached,
                    sentCached, cacheUpdatedAt, receivedTaskHidden, assignmentMode
                ) VALUES (
                    'user-1', 'assigned-1', 'cache-1', NULL, 'direct shared', NULL, NULL,
                    NULL, 'HIGH', NULL, 'ACCEPTED', NULL, 0,
                    'friend-1', 'tee', 'user-1', 'neo', NULL,
                    NULL, 100, NULL, 1,
                    0, 300, 0, 'DIRECT'
                )
                """.trimIndent()
            )
        }

        val migrated = testDb.database.apply { MIGRATION_11_12.migrate(this) }

        assertThat(migrated.columnNames("assigned_todo")).contains("assignmentMode")
        migrated.query("SELECT title, assignmentMode FROM assigned_todo WHERE id = 'assigned-1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("direct shared")
            assertThat(cursor.getString(1)).isEqualTo("DIRECT")
        }
        testDb.close()
    }

    private fun createVersion7Database(name: String): TestDatabase {
        return createDatabase(name, version = 7, includeSyncColumns = false)
    }

    private fun createVersion8Database(name: String): TestDatabase {
        return createDatabase(name, version = 8, includeSyncColumns = true)
    }

    private fun createDatabase(
        name: String,
        version: Int,
        includeSyncColumns: Boolean
    ): TestDatabase {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `todo` (
                                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    `title` TEXT NOT NULL,
                                    `isDone` INTEGER NOT NULL,
                                    `dueDateEpochDay` INTEGER,
                                    `createdAt` INTEGER NOT NULL,
                                    `updatedAt` INTEGER NOT NULL,
                                    `categoryId` INTEGER,
                                    `reminderAtEpochMillis` INTEGER,
                                    `isReminderEnabled` INTEGER NOT NULL,
                                    `reminderRepeatType` TEXT NOT NULL,
                                    `reminderRepeatDaysMask` INTEGER NOT NULL,
                                    `dueTimeMinutes` INTEGER,
                                    `reminderLeadMinutes` INTEGER,
                                    `priority` TEXT NOT NULL
                                    ${if (includeSyncColumns) ", `serverId` TEXT, `clientId` TEXT, `ownerUserId` TEXT, `syncStatus` TEXT NOT NULL DEFAULT 'LOCAL_ONLY', `serverRevision` TEXT, `deletedAt` INTEGER, `lastSyncError` TEXT" else ""}
                                )
                                """.trimIndent()
                            )
                            if (includeSyncColumns) {
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
                            }
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )
        return TestDatabase(helper, helper.writableDatabase)
    }

    private fun SupportSQLiteDatabase.columnNames(tableName: String): List<String> =
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }

    private fun SupportSQLiteDatabase.indexNames(tableName: String): List<String> =
        query("PRAGMA index_list(`$tableName`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }

    private class TestDatabase(
        private val helper: SupportSQLiteOpenHelper,
        val database: SupportSQLiteDatabase
    ) {
        fun close() {
            database.close()
            helper.close()
        }
    }

    private companion object {
        private const val TEST_DB = "app-database-migration-test"
    }
}
