package com.pandyabhavik.habittracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pandyabhavik.habittracker.Graph
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.data.HabitEvent
import com.pandyabhavik.habittracker.data.HomeState
import com.pandyabhavik.habittracker.data.PackResult
import com.pandyabhavik.habittracker.data.Settings
import com.pandyabhavik.habittracker.health.HealthGateway
import com.pandyabhavik.habittracker.health.HealthStatus
import com.pandyabhavik.habittracker.work.Schedules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Graph.repo(app)

    /** Re-evaluates the time-based rules (kitchen lock, 8 PM risk, midnight) every 15 seconds. */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(15_000)
        }
    }

    val state: StateFlow<HomeState?> = combine(
        repo.dao.daysFlow(),
        repo.dao.stickersFlow(),
        repo.dao.packsFlow(),
        repo.settings.flow,
        ticker,
    ) { days, stickers, packs, settings, _ ->
        HomeState.build(repo.now(), repo.zone(), days, stickers, packs, settings)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val syncing: StateFlow<Boolean> = repo.syncing

    /** True only while a pull-down sync runs, so the spinner shows for the pull and not for background syncs. */
    private val _pulling = MutableStateFlow(false)
    val pulling: StateFlow<Boolean> = _pulling.asStateFlow()

    private val _health = MutableStateFlow<Set<String>>(emptySet())
    val granted: StateFlow<Set<String>> = _health.asStateFlow()

    fun healthStatus(): HealthStatus = repo.health.status()

    /** False until the first [refreshGranted] has answered, so screens don't show "no access" for a set that isn't read yet. */
    private val _grantedChecked = MutableStateFlow(false)
    val grantedChecked: StateFlow<Boolean> = _grantedChecked.asStateFlow()

    fun refreshGranted() {
        viewModelScope.launch {
            _health.value = runCatching { repo.health.granted() }.getOrDefault(emptySet())
            _grantedChecked.value = true
        }
    }

    fun sync() {
        viewModelScope.launch {
            repo.sync()
            refreshGranted()
        }
    }

    fun pullToRefresh() {
        if (_pulling.value) return
        viewModelScope.launch {
            _pulling.value = true
            try {
                repo.sync()
                refreshGranted()
                // Long enough for the spinner to read as "checked", even when the sync is instant.
                delay(350)
            } finally {
                _pulling.value = false
            }
        }
    }

    private fun act(block: suspend () -> List<HabitEvent>) {
        viewModelScope.launch { block() }
    }

    fun logBath() = act { repo.logBath() }
    fun undoBath() = act { repo.undoBath() }
    fun closeKitchen() = act { repo.closeKitchen() }
    fun reopenKitchen() = act { repo.reopenKitchen() }
    fun logWeight(kg: Double) = act { repo.logWeight(kg) }
    fun pledge() = viewModelScope.launch { repo.pledge() }

    suspend fun openPack(id: Long): PackResult? = repo.openPack(id)

    fun warnMe(day: DayOfWeek, habit: Habit) = viewModelScope.launch {
        repo.setWarnPattern(day, habit, "$habit-$day")
    }

    fun dismissPattern(day: DayOfWeek, habit: Habit) = viewModelScope.launch {
        repo.settings.update { it.copy(dismissedPattern = "$habit-$day") }
    }

    fun updateSettings(change: (Settings) -> Settings) = viewModelScope.launch {
        repo.settings.update(change)
        repo.evaluate()
    }

    fun finishOnboarding() = viewModelScope.launch {
        repo.settings.update { it.copy(onboarded = true) }
        Schedules.scheduleAll(getApplication())
        repo.pledge()
        repo.sync()
    }

    companion object {
        val PERMISSIONS = HealthGateway.ALL
    }
}
