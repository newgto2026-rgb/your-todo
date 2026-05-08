package com.neo.yourtodo.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.database.AppDatabaseMigrations.MIGRATION_7_8
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

    private fun createVersion7Database(name: String): TestDatabase {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(7) {
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
                                )
                                """.trimIndent()
                            )
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
