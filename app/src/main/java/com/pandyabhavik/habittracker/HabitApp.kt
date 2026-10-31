package com.pandyabhavik.habittracker

import android.app.Application
import com.pandyabhavik.habittracker.art.CatArt
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
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Parse the sticker art off the main thread, so the first CatSticker doesn't stall a frame.
        background.launch { runCatching { CatArt.load(this@HabitApp) } }
        background.launch {
            if (Graph.repo(this@HabitApp).settings.current().onboarded) Schedules.scheduleAll(this@HabitApp)
        }
    }
}
