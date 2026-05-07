package com.neo.yourtodo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neo.yourtodo.core.database.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder ORDER BY triggerAtEpochMillis ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE isEnabled = 1 ORDER BY triggerAtEpochMillis ASC")
    suspend fun getActiveReminders(): List<ReminderEntity>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("SELECT * FROM reminder WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?
}
