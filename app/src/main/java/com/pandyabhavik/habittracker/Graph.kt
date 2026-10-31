package com.pandyabhavik.habittracker

import android.content.Context
import com.pandyabhavik.habittracker.data.HabitDatabase
import com.pandyabhavik.habittracker.data.HabitRepository
import com.pandyabhavik.habittracker.data.HomeState
import com.pandyabhavik.habittracker.data.SettingsStore
import com.pandyabhavik.habittracker.health.HealthGateway
import com.pandyabhavik.habittracker.widget.Widgets
import com.pandyabhavik.habittracker.work.Ongoing

/** The app's single repository, shared by the screens, the workers and the widgets. */
object Graph {
    @Volatile private var repository: HabitRepository? = null

    fun repo(context: Context): HabitRepository = repository ?: synchronized(this) {
        repository ?: run {
            val app = context.applicationContext
            lateinit var r: HabitRepository
            r = HabitRepository(
                context = app,
                dao = HabitDatabase.create(app).dao(),
                settings = SettingsStore(app),
                health = HealthGateway(app),
                onChanged = {
                    Widgets.refresh(app)
                    Ongoing.update(app, snapshot(app))
                },
            )
            repository = r
            r
        }
    }

    /** The state right now, for widgets, notifications and workers. */
    suspend fun snapshot(context: Context): HomeState {
        val r = repo(context)
        return HomeState.build(
            now = r.now(),
            zone = r.zone(),
            dayList = r.dao.days(),
            stickers = r.dao.stickers(),
            packs = r.dao.packs(),
            settings = r.settings.current(),
        )
    }
}
