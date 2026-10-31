package com.pandyabhavik.habittracker.data

import com.pandyabhavik.habittracker.core.Catalog
import com.pandyabhavik.habittracker.core.Coach
import com.pandyabhavik.habittracker.core.Day
import com.pandyabhavik.habittracker.core.Goals
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.core.Rules
import com.pandyabhavik.habittracker.core.Streaks
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Everything the screens and widgets show, worked out from the stored days at one moment. */
data class HomeState(
    val now: LocalDateTime,
    val today: Day,
    val goals: Goals,
    val settings: Settings,
    val days: Map<LocalDate, Day>,
    val closed: Set<Habit>,
    val progress: Map<Habit, Float>,
    val perfect: Boolean,
    val streak: Int,
    val best: Int,
    val perfectDays: Int,
    /** The number this day gets if it closes: "Perfect Day #24". */
    val dayNumber: Int,
    val swaps: Int,
    val atRisk: Boolean,
    val sealLine: String,
    val move: Coach.Move?,
    val pledgesKept: Int,
    val unopened: List<PackEntity>,
    val packs: List<PackEntity>,
    val owned: Map<String, StickerEntity>,
    val comebackFrom: LocalDate?,
    val comebackDay: Int,
    val comebackRecord: Int,
    val savedYesterday: Boolean,
    val pattern: Streaks.Pattern?,
    val rate30: Float?,
    val zone: ZoneId,
) {
    val closedCount: Int get() = closed.size
    val collected: Int get() = owned.size
    val cardTotal: Int get() = Catalog.SIZE

    fun closedOn(date: LocalDate, habit: Habit): Boolean? {
        val d = days[date] ?: return null
        return if (date == now.toLocalDate()) habit in closed else Streaks.closedOn(habit, d, goals)
    }

    companion object {
        fun build(
            now: LocalDateTime,
            zone: ZoneId,
            dayList: List<DayEntity>,
            stickers: List<StickerEntity>,
            packs: List<PackEntity>,
            settings: Settings,
        ): HomeState {
            val todayDate = now.toLocalDate()
            val days = dayList.associate { LocalDate.parse(it.date) to it.toDay() }
            val today = days[todayDate] ?: Day(todayDate)
            val goals = settings.goals
            val closed = Rules.closedSet(today, goals, now)
            val all = if (days.containsKey(todayDate)) days else days + (todayDate to today)
            val streak = Streaks.current(all, todayDate)
            val perfect = closed.size == 4
            val run = Streaks.runEndingAt(all, todayDate.minusDays(1))
            val comebackFrom = settings.comebackFrom
            val comebackDay = if (comebackFrom == null) 0 else {
                // Day 1 of 3 on the first day; each Perfect Day since the break moves it on.
                val done = Streaks.current(all, todayDate)
                (done + if (perfect) 0 else 1).coerceIn(1, 3)
            }
            val pattern = Streaks.pattern(all, goals, todayDate)?.takeIf {
                val key = "${it.habit}-${it.weekday}"
                key != settings.dismissedPattern && settings.warnDay == null
            }
            return HomeState(
                now = now,
                today = today,
                goals = goals,
                settings = settings,
                days = all,
                closed = closed,
                progress = Rules.progress(today, goals, now),
                perfect = perfect,
                streak = streak,
                best = maxOf(Streaks.best(all), streak),
                perfectDays = Streaks.perfectCount(all),
                dayNumber = run + 1,
                swaps = settings.swaps,
                atRisk = Coach.atRisk(closed, run, now),
                sealLine = Coach.sealLine(today, goals, now, closed, run + 1),
                move = Coach.nextMove(today, goals, now, closed),
                pledgesKept = Streaks.pledgesKept(all, todayDate),
                unopened = packs.filter { it.openedAt == null },
                packs = packs,
                owned = stickers.associateBy { it.id },
                comebackFrom = comebackFrom,
                comebackDay = comebackDay,
                comebackRecord = settings.comebackRecord,
                savedYesterday = all[todayDate.minusDays(1)]?.savedWithSwaps == true,
                pattern = pattern,
                rate30 = Streaks.perfectRate(all, todayDate),
                zone = zone,
            )
        }
    }
}
