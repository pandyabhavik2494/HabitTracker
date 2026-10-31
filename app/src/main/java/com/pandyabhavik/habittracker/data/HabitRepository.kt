package com.pandyabhavik.habittracker.data

import android.content.Context
import android.util.Log
import com.pandyabhavik.habittracker.core.Cat
import com.pandyabhavik.habittracker.core.Catalog
import com.pandyabhavik.habittracker.core.Day
import com.pandyabhavik.habittracker.core.Packs
import com.pandyabhavik.habittracker.core.Rarity
import com.pandyabhavik.habittracker.core.Rules
import com.pandyabhavik.habittracker.core.Secrets
import com.pandyabhavik.habittracker.core.Streaks
import com.pandyabhavik.habittracker.health.HealthGateway
import com.pandyabhavik.habittracker.health.HealthStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/** Something the evaluation found that deserves a notification when the app isn't open. */
sealed interface HabitEvent {
    data class PerfectDay(val date: LocalDate, val number: Int, val streak: Int) : HabitEvent
    data class SecretUnlocked(val catId: String) : HabitEvent
    data class Saved(val dates: List<LocalDate>, val swapsLeft: Int) : HabitEvent
    data class Comeback(val record: Int) : HabitEvent
}

data class PackResult(val pack: PackEntity, val cat: Cat, val isNew: Boolean, val swapsEarned: Int)

class HabitRepository(
    private val context: Context,
    val dao: HabitDao,
    val settings: SettingsStore,
    val health: HealthGateway,
    /** Called after every change, to redraw the widgets and the ongoing notification. */
    private val onChanged: suspend () -> Unit = {},
    /** Tests replace the clock and the dice. */
    var clock: () -> LocalDateTime = { LocalDateTime.now() },
    var random: Random = Random.Default,
) {
    private val syncLock = Mutex()
    private val writeLock = Mutex()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    fun zone(): ZoneId = ZoneId.systemDefault()
    fun now(): LocalDateTime = clock()
    fun today(): LocalDate = now().toLocalDate()
    private fun nowMillis(): Long = now().atZone(zone()).toInstant().toEpochMilli()

    /**
     * Reads Health Connect for the days that can still change, then re-checks the day. Runs on pull-down,
     * when the app opens, and every 15 minutes in the background.
     */
    suspend fun sync(): List<HabitEvent> = syncLock.withLock {
        _syncing.value = true
        try {
            val error = try {
                readHealth()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed", e)
                describe(e)
            }
            settings.update { it.copy(lastSync = if (error == null) nowMillis() else it.lastSync, lastSyncError = error) }
            evaluate()
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun readHealth() {
        when (health.status()) {
            HealthStatus.NOT_INSTALLED -> throw SyncProblem("Health Connect isn't installed")
            HealthStatus.NEEDS_UPDATE -> throw SyncProblem("Health Connect needs an update")
            HealthStatus.AVAILABLE -> Unit
        }
        val granted = health.granted()
        if (HealthGateway.CORE.none { it in granted }) throw SyncProblem("Allow Health Connect access in Settings")
        val s = settings.current()
        val today = today()
        // The first sync brings in the last 30 days for the Journey; after that, the last 3 days can
        // still change (late syncs from the watch or the scale).
        val from = if (s.lastSync == null) today.minusDays(30) else today.minusDays(2)
        var d = from
        while (!d.isAfter(today)) {
            val h = health.readDay(d, zone(), granted)
            writeLock.withLock {
                val old = dao.day(d.toString())?.toDay() ?: Day(d)
                val hasData = (h.steps ?: 0) > 0 || h.weightKg != null
                if (old != Day(d) || hasData || d == today) {
                    val oldAt = old.weighedAt
                    val newAt = h.weighedAt
                    val useScale = h.weightKg != null &&
                        (!old.weightManual || oldAt == null || (newAt != null && newAt >= oldAt))
                    val merged = old.copy(
                        steps = h.steps ?: old.steps,
                        hourly = h.hourly ?: old.hourly,
                        // The newer reading wins: a typed-in correction stays until the scale logs a later one.
                        weightKg = if (useScale) h.weightKg else old.weightKg,
                        weighedAt = if (useScale) h.weighedAt else old.weighedAt,
                        weightManual = if (useScale) false else old.weightManual,
                    )
                    dao.upsertDay(merged.toEntity())
                }
            }
            d = d.plusDays(1)
        }
    }

    /**
     * Marks new Perfect Days, hands out their packs (and any secret or comeback cat), then settles the
     * finished days: a missed day after a streak is saved with 3 swaps, or the streak breaks.
     */
    suspend fun evaluate(): List<HabitEvent> = writeLock.withLock {
        val events = ArrayList<HabitEvent>()
        val now = now()
        val today = now.toLocalDate()
        var s = settings.current()
        if (s.firstDay == null) {
            settings.update { it.copy(firstDay = today) }
            s = settings.current()
        }
        val days = dao.days().associate { LocalDate.parse(it.date) to it.toDay() }.toMutableMap()
        if (days[today] == null) {
            days[today] = Day(today)
            dao.upsertDay(Day(today).toEntity())
        }
        val goals = s.goals
        val owned = dao.stickers().map { it.id }.toMutableSet()
        val pending = dao.packs().filter { it.openedAt == null }.mapNotNull { it.fixedCat }.toMutableSet()

        // 1. New Perfect Days (today, or a recent day that late data completed).
        for (back in 3 downTo 0) {
            val date = today.minusDays(back.toLong())
            val day = days[date] ?: continue
            if (day.perfectAt != null || !Rules.perfect(day, goals, now)) continue
            val at = nowMillis()
            if (day.savedWithSwaps) {
                // Late data completed a day that swaps had saved: give the swaps back.
                settings.update { it.copy(swaps = it.swaps + Streaks.SWAPS_PER_SAVE) }
                s = settings.current()
            }
            if (s.comebackFrom == date.plusDays(1)) {
                // Late data completed the day that broke the streak: there is nothing to come back from.
                settings.update { it.copy(comebackFrom = null) }
                s = settings.current()
            }
            val done = day.copy(perfectAt = at, savedWithSwaps = false)
            days[date] = done
            dao.upsertDay(done.toEntity())
            val run = Streaks.runEndingAt(days, date)
            val guarantee = Packs.milestoneGuarantee(run)
            if (dao.packsFor(date.toString(), REASON_DAILY).isEmpty() && dao.packsFor(date.toString(), REASON_MILESTONE).isEmpty()) {
                dao.insertPack(
                    PackEntity(
                        date = date.toString(),
                        createdAt = at,
                        reason = if (guarantee != null) REASON_MILESTONE else REASON_DAILY,
                        minRarity = guarantee?.name,
                    ),
                )
            }
            events += HabitEvent.PerfectDay(date, Streaks.perfectCount(days), run)
            // Time-of-day secrets only count when the day closes live.
            val local = if (date == today) now.toLocalTime() else LocalTime.of(18, 0)
            for (id in Secrets.unlocked(days, date, local, owned + pending)) {
                dao.insertPack(PackEntity(date = date.toString(), createdAt = at, reason = REASON_SECRET, fixedCat = id))
                pending += id
                events += HabitEvent.SecretUnlocked(id)
            }
            val cb = s.comebackFrom
            if (cb != null && !date.isBefore(cb) && run >= 3) {
                dao.insertPack(PackEntity(date = date.toString(), createdAt = at, reason = REASON_COMEBACK, fixedCat = "maneki"))
                settings.update { it.copy(comebackFrom = null) }
                s = settings.current()
                events += HabitEvent.Comeback(s.comebackRecord)
            }
        }

        // 2. Settle finished days. Yesterday waits until 4 AM so a watch's late steps still count.
        val canSettleYesterday = now.hour >= 4
        val settleTo = if (canSettleYesterday) today else today.minusDays(1)
        val from = s.settledUntil ?: s.firstDay ?: today
        if (from.isBefore(settleTo)) {
            val result = Streaks.settle(days, from, settleTo, s.swaps)
            for (d in result.saved) {
                val saved = (days[d] ?: Day(d)).copy(savedWithSwaps = true)
                days[d] = saved
                dao.upsertDay(saved.toEntity())
            }
            if (result.saved.isNotEmpty()) events += HabitEvent.Saved(result.saved, result.swapsLeft)
            val broke = result.brokenOn
            settings.update {
                var n = it.copy(swaps = result.swapsLeft, settledUntil = settleTo)
                if (broke != null && result.runBeforeBreak >= 3) {
                    n = n.copy(comebackFrom = broke.plusDays(1), comebackRecord = maxOf(Streaks.best(days), result.runBeforeBreak))
                } else if (broke != null && it.comebackFrom != null) {
                    // A miss during a comeback starts it again.
                    n = n.copy(comebackFrom = broke.plusDays(1))
                }
                n
            }
        }
        onChanged()
        events
    }

    // ---- Actions -------------------------------------------------------------------------------

    private suspend fun editToday(change: (Day) -> Day) {
        writeLock.withLock {
            val today = today()
            val old = dao.day(today.toString())?.toDay() ?: Day(today)
            dao.upsertDay(change(old).toEntity())
        }
    }

    suspend fun logBath(): List<HabitEvent> {
        editToday { it.copy(bathAt = it.bathAt ?: nowMillis()) }
        return evaluate()
    }

    /** Undo right after a bath was logged by mistake. Takes back the Perfect Day if the pack is still sealed. */
    suspend fun undoBath(): List<HabitEvent> {
        undoClose { it.copy(bathAt = null) }
        return evaluate()
    }

    private suspend fun undoClose(change: (Day) -> Day) {
        writeLock.withLock {
            val today = today()
            val old = dao.day(today.toString())?.toDay() ?: return
            val opened = dao.packs().any { it.date == today.toString() && it.openedAt != null }
            if (old.perfectAt != null && opened) return
            if (old.perfectAt != null) dao.deleteUnopenedDayPacks(today.toString())
            dao.upsertDay(change(old).copy(perfectAt = null).toEntity())
        }
    }

    /** "Yes, I logged my meals today": closes the meals quarter. */
    suspend fun logMeals(): List<HabitEvent> {
        editToday { it.copy(mealsLoggedAt = it.mealsLoggedAt ?: nowMillis()) }
        return evaluate()
    }

    suspend fun unlogMeals(): List<HabitEvent> {
        undoClose { it.copy(mealsLoggedAt = null) }
        return evaluate()
    }

    suspend fun logWeight(kg: Double): List<HabitEvent> {
        editToday { it.copy(weightKg = kg, weighedAt = nowMillis(), weightManual = true) }
        return evaluate()
    }

    suspend fun pledge() {
        editToday { it.copy(pledgedAt = it.pledgedAt ?: nowMillis()) }
        onChanged()
    }

    /** Rips a pack: a random cat by the odds (or the pack's fixed cat). A cat already owned is a swap. */
    suspend fun openPack(id: Long): PackResult? = writeLock.withLock {
        val pack = dao.pack(id) ?: return@withLock null
        if (pack.openedCat != null) {
            val cat = Catalog[pack.openedCat]
            return@withLock PackResult(pack, cat, pack.wasNew, 0)
        }
        val cat = pack.fixedCat?.let { Catalog[it] }
            ?: Packs.roll(random, pack.minRarity?.let { runCatching { Rarity.valueOf(it) }.getOrNull() })
        val at = nowMillis()
        val old = dao.sticker(cat.id)
        dao.upsertSticker(StickerEntity(cat.id, (old?.count ?: 0) + 1, old?.firstAt ?: at, at))
        val isNew = old == null
        if (!isNew) settings.update { it.copy(swaps = it.swaps + 1) }
        val opened = pack.copy(openedCat = cat.id, openedAt = at, wasNew = isNew)
        dao.upsertPack(opened)
        PackResult(opened, cat, isNew, if (isNew) 0 else 1)
    }.also { onChanged() }

    suspend fun setWarnPattern(day: java.time.DayOfWeek?, habit: com.pandyabhavik.habittracker.core.Habit?, key: String) {
        settings.update { it.copy(warnDay = day, warnHabit = habit, dismissedPattern = key) }
    }

    private fun describe(e: Throwable): String = when {
        e is SyncProblem -> e.message ?: "Sync failed"
        e is SecurityException -> "Allow Health Connect access in Settings"
        e.message?.contains("rate limit", ignoreCase = true) == true -> "Health Connect is busy. Trying again soon."
        else -> "Couldn't read Health Connect"
    }

    class SyncProblem(message: String) : Exception(message)

    companion object {
        private const val TAG = "HabitRepository"
        const val REASON_DAILY = "daily"
        const val REASON_MILESTONE = "milestone"
        const val REASON_SECRET = "secret"
        const val REASON_COMEBACK = "comeback"
    }
}
