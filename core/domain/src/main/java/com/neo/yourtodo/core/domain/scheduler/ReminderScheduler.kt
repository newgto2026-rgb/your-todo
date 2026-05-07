package com.neo.yourtodo.core.domain.scheduler

import com.neo.yourtodo.core.model.Reminder

interface ReminderScheduler {
    suspend fun schedule(reminder: Reminder)
    suspend fun cancel(reminderId: Long)
    suspend fun rescheduleAll()
}
