package com.neo.yourtodo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.neo.yourtodo.core.database.dao.CategoryDao
import com.neo.yourtodo.core.database.dao.ReminderDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.entity.CategoryEntity
import com.neo.yourtodo.core.database.entity.ReminderEntity
import com.neo.yourtodo.core.database.entity.TodoEntity

@Database(
    entities = [TodoEntity::class, CategoryEntity::class, ReminderEntity::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun categoryDao(): CategoryDao
    abstract fun reminderDao(): ReminderDao
}
