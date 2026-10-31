package com.pandyabhavik.habittracker

import android.app.Application
import com.pandyabhavik.habittracker.work.Notifier
import com.pandyabhavik.habittracker.work.Schedules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HabitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            if (Graph.repo(this@HabitApp).settings.current().onboarded) Schedules.scheduleAll(this@HabitApp)
        }
    }
}
