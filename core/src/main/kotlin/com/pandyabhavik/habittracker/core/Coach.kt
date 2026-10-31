package com.pandyabhavik.habittracker.core

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.LocalTime
import java.util.Locale

/** The words on screen: what's left, the best next move, and how the day is going. */
object Coach {
    fun n(v: Int): String = String.format(Locale.US, "%,d", v)

    /** The streak is at risk from 8 PM while anything is still open. */
    val RISK_FROM: LocalTime = LocalTime.of(20, 0)

    fun atRisk(closed: Set<Habit>, streak: Int, now: LocalDateTime): Boolean =
        closed.size < 4 && streak > 0 && !now.toLocalTime().isBefore(RISK_FROM)

    /** Real time left today, so the countdown is right on the days the clocks change. */
    fun untilMidnight(now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Duration =
        Duration.between(now.atZone(zone), now.toLocalDate().plusDays(1).atStartOfDay(zone))

    fun clock(d: Duration): String {
        val s = d.seconds.coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /** Steps the day should have by now to reach the goal by 9 PM, starting at 7 AM. */
    fun expectedSteps(goals: Goals, now: LocalDateTime): Int {
        val m = now.hour * 60 + now.minute
        val f = ((m - 7 * 60).toFloat() / (14 * 60)).coerceIn(0f, 1f)
        return (goals.steps * f).toInt()
    }

    /** The line under the Seal. */
    fun sealLine(day: Day, goals: Goals, now: LocalDateTime, closed: Set<Habit>, nextPerfect: Int): String {
        val open = Habit.entries.filter { it !in closed }
        return when {
            open.isEmpty() -> "Perfect Day #$nextPerfect"
            closed.isEmpty() -> "Perfect Day #$nextPerfect is up for grabs"
            open.size == 1 -> "${leftText(open.first(), day, goals, now)} from a Perfect Day"
            Habit.STEPS in open && day.steps >= expectedSteps(goals, now) -> "Ahead of pace"
            Habit.STEPS in open && now.hour >= 9 -> "${n(expectedSteps(goals, now) - day.steps)} steps behind pace"
            else -> "${open.size} to go"
        }
    }

    /** A short description of what closing [habit] still takes. */
    fun leftText(habit: Habit, day: Day, goals: Goals, now: LocalDateTime): String = when (habit) {
        Habit.STEPS -> "${n(goals.steps - day.steps)} steps"
        Habit.CALORIES -> when (Rules.calorieState(day, goals, now)) {
            CalorieState.NOT_LOGGED -> "A logged meal"
            CalorieState.OVER -> "Calories"
            else -> "Closing the kitchen"
        }
        Habit.WEIGH -> "A weigh-in"
        Habit.BATH -> "A bath"
    }

    data class Move(val title: String, val detail: String?)

    /** The single best thing to do next, or null once the day is closed. */
    fun nextMove(day: Day, goals: Goals, now: LocalDateTime, closed: Set<Habit>): Move? {
        val stepsLeft = goals.steps - day.steps
        return when {
            closed.size == 4 -> null
            Habit.WEIGH !in closed && now.hour < 12 -> Move("Weigh in: instant quarter.", "30 seconds on the scale closes it.")
            Habit.STEPS !in closed && stepsLeft <= 2_500 ->
                Move("One ${Rules.walkMinutes(stepsLeft)}-minute walk closes steps.", "${n(stepsLeft)} steps to go.")
            Habit.STEPS !in closed && now.hour < 17 -> {
                val walk = 20
                val by = (day.steps + walk * 100)
                Move("A $walk-minute walk now puts you at ${n(by)}.", "Steps are the quarter that takes longest. Bank them early.")
            }
            Habit.WEIGH !in closed -> Move("Weigh in: instant quarter.", "30 seconds on the scale closes it.")
            Habit.CALORIES !in closed && Rules.calorieState(day, goals, now) == CalorieState.UNDER ->
                Move("Done eating? Close the kitchen.", "${n(goals.calories - day.calories)} kcal under. Lock it in.")
            Habit.BATH !in closed -> Move("Long-press Bath when you're done.", null)
            Habit.STEPS !in closed -> Move("A ${Rules.walkMinutes(stepsLeft)}-minute walk closes steps.", "${n(stepsLeft)} steps to go.")
            Habit.CALORIES !in closed && Rules.calorieState(day, goals, now) == CalorieState.NOT_LOGGED ->
                Move("Log today's meals in MyFitnessPal.", "Calories close once the kitchen does.")
            else -> null
        }
    }
}
