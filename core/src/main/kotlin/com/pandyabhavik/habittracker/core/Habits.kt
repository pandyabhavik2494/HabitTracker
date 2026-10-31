package com.pandyabhavik.habittracker.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** CALORIES is the meals quarter: a manual yes/no to "did you log your meals today?". */
enum class Habit { STEPS, CALORIES, WEIGH, BATH }

/** Bhavik's targets. Meals are a manual yes/no, so they have no number. */
data class Goals(
    val steps: Int = 10_000,
)

/** Everything known about one day. Times are epoch milliseconds. */
data class Day(
    val date: LocalDate,
    val steps: Int = 0,
    /** When Bhavik said yes to "Did you log your meals today?". Null until then. */
    val mealsLoggedAt: Long? = null,
    val weightKg: Double? = null,
    val weighedAt: Long? = null,
    /** True when the weigh-in was typed in by hand instead of coming from the scale. */
    val weightManual: Boolean = false,
    val bathAt: Long? = null,
    /** When all four first closed. Stays set once reached. */
    val perfectAt: Long? = null,
    /** A missed day that 3 swaps kept in the streak. */
    val savedWithSwaps: Boolean = false,
    val pledgedAt: Long? = null,
    /** Steps per hour of the day, 24 values. */
    val hourly: List<Int> = emptyList(),
)

object Rules {
    fun closed(habit: Habit, day: Day, goals: Goals, now: LocalDateTime): Boolean = when (habit) {
        Habit.STEPS -> day.steps >= goals.steps
        Habit.CALORIES -> day.mealsLoggedAt != null
        Habit.WEIGH -> day.weighedAt != null
        Habit.BATH -> day.bathAt != null
    }

    fun closedSet(day: Day, goals: Goals, now: LocalDateTime): Set<Habit> =
        Habit.entries.filter { closed(it, day, goals, now) }.toSet()

    fun perfect(day: Day, goals: Goals, now: LocalDateTime): Boolean = closedSet(day, goals, now).size == 4

    /** Progress of each quarter, 0..1, for the Seal. A closed quarter is always 1; only steps fill gradually. */
    fun progress(day: Day, goals: Goals, now: LocalDateTime): Map<Habit, Float> = Habit.entries.associateWith { h ->
        if (closed(h, day, goals, now)) 1f else when (h) {
            Habit.STEPS -> (day.steps.toFloat() / goals.steps).coerceIn(0f, 0.97f)
            else -> 0f
        }
    }

    /** Minutes of easy walking for [steps] (about 100 steps a minute). */
    fun walkMinutes(steps: Int): Int = ((steps + 99) / 100).coerceAtLeast(1)

    fun localTime(epochMillis: Long, zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
}
