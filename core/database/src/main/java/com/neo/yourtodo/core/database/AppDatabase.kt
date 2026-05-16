package com.neo.yourtodo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.neo.yourtodo.core.database.dao.CategoryDao
import com.neo.yourtodo.core.database.dao.AssignedTodoDao
import com.neo.yourtodo.core.database.dao.ReminderDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.dao.TodoOutboxDao
import com.neo.yourtodo.core.database.entity.AssignedTodoChecklistItemEntity
import com.neo.yourtodo.core.database.entity.AssignedTodoEntity
import com.neo.yourtodo.core.database.entity.CategoryEntity
import com.neo.yourtodo.core.database.entity.ReminderEntity
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.database.entity.TodoOutboxEntity

@Database(
    entities = [
        TodoEntity::class,
        CategoryEntity::class,
        ReminderEntity::class,
        TodoOutboxEntity::class,
        AssignedTodoEntity::class,
        AssignedTodoChecklistItemEntity::class
    ],
    version = 12,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun todoOutboxDao(): TodoOutboxDao
    abstract fun categoryDao(): CategoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun assignedTodoDao(): AssignedTodoDao
}
