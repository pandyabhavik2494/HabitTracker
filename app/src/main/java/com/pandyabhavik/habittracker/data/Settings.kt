package com.pandyabhavik.habittracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pandyabhavik.habittracker.core.Goals
import com.pandyabhavik.habittracker.core.Habit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

data class Settings(
    val onboarded: Boolean = false,
    val stepsGoal: Int = 10_000,
    val pounds: Boolean = true,
    val notifyPledge: Boolean = true,
    val notifyMidday: Boolean = true,
    val notifyRisk: Boolean = true,
    val notifyLastCall: Boolean = true,
    val ongoing: Boolean = false,
    val haptics: Boolean = true,
    val swaps: Int = 0,
    /** Every day before this one has been settled (saved with swaps or counted as a break). */
    val settledUntil: LocalDate? = null,
    /** The first day of a running 3-day comeback for Maneki. */
    val comebackFrom: LocalDate? = null,
    /** The record before the streak broke, shown on the comeback card. */
    val comebackRecord: Int = 0,
    val warnDay: DayOfWeek? = null,
    val warnHabit: Habit? = null,
    /** A pattern the person turned down, so its card doesn't come back. */
    val dismissedPattern: String? = null,
    val lastSync: Long? = null,
    val lastSyncError: String? = null,
    val firstDay: LocalDate? = null,
) {
    val goals: Goals get() = Goals(stepsGoal)
}

private val Context.store: DataStore<Preferences> by preferencesDataStore(
    "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsStore(context: Context) {
    private val store = context.store

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val steps = intPreferencesKey("steps_goal")
        val pounds = booleanPreferencesKey("pounds")
        val nPledge = booleanPreferencesKey("n_pledge")
        val nMidday = booleanPreferencesKey("n_midday")
        val nRisk = booleanPreferencesKey("n_risk")
        val nLast = booleanPreferencesKey("n_last")
        val ongoing = booleanPreferencesKey("ongoing")
        val haptics = booleanPreferencesKey("haptics")
        val swaps = intPreferencesKey("swaps")
        val settled = stringPreferencesKey("settled_until")
        val comeback = stringPreferencesKey("comeback_from")
        val comebackRecord = intPreferencesKey("comeback_record")
        val warnDay = intPreferencesKey("warn_day")
        val warnHabit = stringPreferencesKey("warn_habit")
        val dismissed = stringPreferencesKey("dismissed_pattern")
        val lastSync = longPreferencesKey("last_sync")
        val lastError = stringPreferencesKey("last_error")
        val firstDay = stringPreferencesKey("first_day")
    }

    val flow: Flow<Settings> = store.data.map { read(it) }

    suspend fun current(): Settings = flow.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit { p ->
            val old = read(p)
            write(p, change(old))
        }
    }

    private fun read(p: Preferences): Settings = Settings(
        onboarded = p[K.onboarded] ?: false,
        stepsGoal = p[K.steps] ?: 10_000,
        pounds = p[K.pounds] ?: true,
        notifyPledge = p[K.nPledge] ?: true,
        notifyMidday = p[K.nMidday] ?: true,
        notifyRisk = p[K.nRisk] ?: true,
        notifyLastCall = p[K.nLast] ?: true,
        ongoing = p[K.ongoing] ?: false,
        haptics = p[K.haptics] ?: true,
        swaps = p[K.swaps] ?: 0,
        settledUntil = p[K.settled]?.let(LocalDate::parse),
        comebackFrom = p[K.comeback]?.let(LocalDate::parse),
        comebackRecord = p[K.comebackRecord] ?: 0,
        warnDay = p[K.warnDay]?.let(DayOfWeek::of),
        warnHabit = p[K.warnHabit]?.let { runCatching { Habit.valueOf(it) }.getOrNull() },
        dismissedPattern = p[K.dismissed],
        lastSync = p[K.lastSync],
        lastSyncError = p[K.lastError],
        firstDay = p[K.firstDay]?.let(LocalDate::parse),
    )

    private fun write(p: MutablePreferences, s: Settings) {
        p[K.onboarded] = s.onboarded
        p[K.steps] = s.stepsGoal
        p[K.pounds] = s.pounds
        p[K.nPledge] = s.notifyPledge
        p[K.nMidday] = s.notifyMidday
        p[K.nRisk] = s.notifyRisk
        p[K.nLast] = s.notifyLastCall
        p[K.ongoing] = s.ongoing
        p[K.haptics] = s.haptics
        p[K.swaps] = s.swaps
        if (s.settledUntil != null) p[K.settled] = s.settledUntil.toString() else p.remove(K.settled)
        if (s.comebackFrom != null) p[K.comeback] = s.comebackFrom.toString() else p.remove(K.comeback)
        p[K.comebackRecord] = s.comebackRecord
        if (s.warnDay != null) p[K.warnDay] = s.warnDay.value else p.remove(K.warnDay)
        if (s.warnHabit != null) p[K.warnHabit] = s.warnHabit.name else p.remove(K.warnHabit)
        if (s.dismissedPattern != null) p[K.dismissed] = s.dismissedPattern else p.remove(K.dismissed)
        if (s.lastSync != null) p[K.lastSync] = s.lastSync else p.remove(K.lastSync)
        if (s.lastSyncError != null) p[K.lastError] = s.lastSyncError else p.remove(K.lastError)
        if (s.firstDay != null) p[K.firstDay] = s.firstDay.toString() else p.remove(K.firstDay)
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }
}
