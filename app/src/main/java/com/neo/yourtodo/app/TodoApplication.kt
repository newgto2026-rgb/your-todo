package com.neo.yourtodo.app

import android.app.Application
import com.neo.yourtodo.app.todo.reminder.TodoReminderNotificationHelper
import com.neo.yourtodo.core.domain.scheduler.TodoReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class TodoApplication : Application() {

    @Inject
    lateinit var todoReminderScheduler: TodoReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        TodoReminderNotificationHelper.ensureChannel(this)
        appScope.launch {
            todoReminderScheduler.rescheduleAll()
        }
    }
}
