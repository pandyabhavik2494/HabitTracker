package com.pandyabhavik.habittracker.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class Habit { STEPS, CALORIES, WEIGH, BATH }

/** Bhavik's four targets. The kitchen locks itself at [kitchenLockMinute] (minutes after midnight). */
data class Goals(
    val steps: Int = 10_000,
    val calories: Int = 1_500,
    val kitchenLockMinute: Int = 20 * 60 + 30,
)

/** Everything known about one day. Times are epoch milliseconds. */
data class Day(
    val date: LocalDate,
    val steps: Int = 0,
    val calories: Int = 0,
    /** Food entries from MyFitnessPal (through Health Connect). Zero means nothing was logged. */
    val meals: Int = 0,
    val breakfast: Int = 0,
    val lunch: Int = 0,
    val dinner: Int = 0,
    val snacks: Int = 0,
    val kitchenClosedAt: Long? = null,
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

enum class CalorieState {
    /** Nothing logged yet. */
    NOT_LOGGED,

    /** Under the limit, kitchen still open. */
    UNDER,

    /** Kitchen closed (tapped or locked at the set time) at or under the limit. */
    CLOSED,

    /** Over the limit: this ring can't close today. */
    OVER,
}

object Rules {
    fun kitchenLocked(day: Day, goals: Goals, now: LocalDateTime): Boolean {
        if (day.kitchenClosedAt != null) return true
        if (day.date.isBefore(now.toLocalDate())) return true
        if (day.date.isAfter(now.toLocalDate())) return false
        return now.hour * 60 + now.minute >= goals.kitchenLockMinute
    }

    fun calorieState(day: Day, goals: Goals, now: LocalDateTime): CalorieState = when {
        day.calories > goals.calories -> CalorieState.OVER
        day.meals == 0 && day.calories == 0 -> CalorieState.NOT_LOGGED
        kitchenLocked(day, goals, now) -> CalorieState.CLOSED
        else -> CalorieState.UNDER
    }

    fun closed(habit: Habit, day: Day, goals: Goals, now: LocalDateTime): Boolean = when (habit) {
        Habit.STEPS -> day.steps >= goals.steps
        Habit.CALORIES -> calorieState(day, goals, now) == CalorieState.CLOSED
        Habit.WEIGH -> day.weighedAt != null
        Habit.BATH -> day.bathAt != null
    }

    fun closedSet(day: Day, goals: Goals, now: LocalDateTime): Set<Habit> =
        Habit.entries.filter { closed(it, day, goals, now) }.toSet()

    fun perfect(day: Day, goals: Goals, now: LocalDateTime): Boolean = closedSet(day, goals, now).size == 4

    /** Progress of each ring, 0..1, for the Seal. A closed ring is always 1. */
    fun progress(day: Day, goals: Goals, now: LocalDateTime): Map<Habit, Float> = Habit.entries.associateWith { h ->
        if (closed(h, day, goals, now)) 1f else when (h) {
            Habit.STEPS -> (day.steps.toFloat() / goals.steps).coerceIn(0f, 0.97f)
            Habit.CALORIES -> when (calorieState(day, goals, now)) {
                // Under budget so far: the ring fills as the day goes on, and closes when the kitchen does.
                CalorieState.UNDER -> {
                    val minute = now.hour * 60 + now.minute
                    (minute.toFloat() / goals.kitchenLockMinute).coerceIn(0.05f, 0.95f)
                }
                else -> 0f
            }
            Habit.WEIGH, Habit.BATH -> 0f
        }
    }

    /** Minutes of easy walking for [steps] (about 100 steps a minute). */
    fun walkMinutes(steps: Int): Int = ((steps + 99) / 100).coerceAtLeast(1)

    fun localTime(epochMillis: Long, zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
}
