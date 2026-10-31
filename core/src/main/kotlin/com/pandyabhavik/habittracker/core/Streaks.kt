package com.pandyabhavik.habittracker.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object Streaks {
    const val SWAPS_PER_SAVE = 3

    /** A day keeps the streak alive when it was a Perfect Day or swaps saved it. */
    fun counts(day: Day?): Boolean = day != null && (day.perfectAt != null || day.savedWithSwaps)

    /** Consecutive counting days that end on [date] (0 if [date] itself doesn't count). */
    fun runEndingAt(days: Map<LocalDate, Day>, date: LocalDate): Int {
        var n = 0
        var d = date
        while (counts(days[d])) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** The streak as shown: yesterday's run, plus today once it is a Perfect Day. */
    fun current(days: Map<LocalDate, Day>, today: LocalDate): Int =
        runEndingAt(days, today.minusDays(1)) + if (counts(days[today])) 1 else 0

    fun best(days: Map<LocalDate, Day>): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days.keys.sorted()) {
            run = if (counts(days[d])) (if (prev != null && prev.plusDays(1) == d && run > 0) run + 1 else 1) else 0
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    fun perfectCount(days: Map<LocalDate, Day>): Int = days.values.count { it.perfectAt != null }

    /** Pledges in a row that ended in a kept day, up to yesterday. */
    fun pledgesKept(days: Map<LocalDate, Day>, today: LocalDate): Int {
        var n = 0
        var d = today.minusDays(1)
        while (true) {
            val day = days[d] ?: break
            if (day.pledgedAt == null || !counts(day)) break
            n++
            d = d.minusDays(1)
        }
        return n
    }

    data class Settlement(
        /** Missed days that swaps saved, oldest first. */
        val saved: List<LocalDate>,
        /** The last missed day that nothing saved, if the streak broke. */
        val brokenOn: LocalDate?,
        /** How long the streak was before it broke. */
        val runBeforeBreak: Int,
        val swapsLeft: Int,
    )

    /**
     * Settles the finished days from [from] up to (not including) [today]: a missed day right after a
     * streak is saved when 3 swaps are left; otherwise the streak breaks there.
     */
    fun settle(days: Map<LocalDate, Day>, from: LocalDate, today: LocalDate, swaps: Int): Settlement {
        val work = days.toMutableMap()
        val saved = ArrayList<LocalDate>()
        var left = swaps
        var broken: LocalDate? = null
        var runBefore = 0
        var d = from
        while (d.isBefore(today)) {
            if (!counts(work[d])) {
                val run = runEndingAt(work, d.minusDays(1))
                if (run > 0 && left >= SWAPS_PER_SAVE) {
                    left -= SWAPS_PER_SAVE
                    saved += d
                    work[d] = (work[d] ?: Day(d)).copy(savedWithSwaps = true)
                } else if (run > 0) {
                    broken = d
                    runBefore = run
                }
            }
            d = d.plusDays(1)
        }
        return Settlement(saved, broken, runBefore, left)
    }

    /** Whether [habit] closed on a finished day. */
    fun closedOn(habit: Habit, day: Day, goals: Goals): Boolean =
        Rules.closed(habit, day, goals, LocalDateTime.of(day.date, LocalTime.MAX))

    /** Share of the last [n] finished days where [habit] closed, counting only days the app knew about. */
    fun hitRate(days: Map<LocalDate, Day>, habit: Habit, goals: Goals, today: LocalDate, n: Int = 30): Float? {
        val first = days.keys.minOrNull() ?: return null
        val range = (1..n).map { today.minusDays(it.toLong()) }.filter { !it.isBefore(first) }
        if (range.isEmpty()) return null
        val hits = range.count { d -> days[d]?.let { closedOn(habit, it, goals) } == true }
        return hits.toFloat() / range.size
    }

    /** Share of the last [n] finished days that kept the streak. */
    fun perfectRate(days: Map<LocalDate, Day>, today: LocalDate, n: Int = 30): Float? {
        val first = days.keys.minOrNull() ?: return null
        val range = (1..n).map { today.minusDays(it.toLong()) }.filter { !it.isBefore(first) }
        if (range.isEmpty()) return null
        return range.count { days[it]?.perfectAt != null }.toFloat() / range.size
    }

    data class Pattern(val habit: Habit, val weekday: DayOfWeek, val misses: Int, val of: Int)

    /**
     * The habit that breaks the most days, and the weekday it breaks on most, when that is a real pattern:
     * at least 3 of the last 4 such weekdays missed it.
     */
    fun pattern(days: Map<LocalDate, Day>, goals: Goals, today: LocalDate): Pattern? {
        var best: Pattern? = null
        for (habit in Habit.entries) {
            for (wd in DayOfWeek.entries) {
                val dates = (1..56).map { today.minusDays(it.toLong()) }.filter { it.dayOfWeek == wd }.take(4)
                val known = dates.mapNotNull { days[it] }
                if (known.size < 4) continue
                val misses = known.count { !closedOn(habit, it, goals) }
                if (misses >= 3 && (best == null || misses > best.misses)) best = Pattern(habit, wd, misses, known.size)
            }
        }
        return best
    }

    /** Index 0..3 of where the streak sits between milestones. */
    val MILESTONES = listOf(7, 14, 30, 50, 100, 365)

    fun nextMilestone(streak: Int): Int? = MILESTONES.firstOrNull { it > streak }
}
