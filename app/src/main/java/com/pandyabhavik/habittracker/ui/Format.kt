package com.pandyabhavik.habittracker.ui

import com.pandyabhavik.habittracker.core.Day
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.core.Rarity
import com.pandyabhavik.habittracker.data.HomeState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object F {
    /** A no-break space: keeps "AM"/"PM" on the line with its time. */
    const val NBSP = '\u00A0'

    private val clock = DateTimeFormatter.ofPattern("h:mm${NBSP}a", Locale.US)
    val weekday: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.US)
    val monthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    val month: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.US)
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

    fun n(v: Int): String = String.format(Locale.US, "%,d", v)

    fun time(epochMillis: Long, zone: ZoneId): String = clock.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun weightValue(kg: Double, pounds: Boolean): Double = if (pounds) kg * 2.2046226 else kg

    fun weight(kg: Double, pounds: Boolean): String =
        String.format(Locale.US, "%.1f %s", weightValue(kg, pounds), if (pounds) "lb" else "kg")

    fun unit(pounds: Boolean) = if (pounds) "lb" else "kg"

    /** "down 0.6 this week" against the last weigh-in at least a week back, or null without one. */
    fun weightTrend(s: HomeState, kg: Double): String? {
        val today = s.now.toLocalDate()
        val ref = s.days.entries
            .filter { it.key.isBefore(today.minusDays(6)) && it.value.weightKg != null }
            .maxByOrNull { it.key }?.value?.weightKg
            ?: return null
        val d = weightValue(kg, s.settings.pounds) - weightValue(ref, s.settings.pounds)
        if (abs(d) < 0.05) return "steady this week"
        return String.format(Locale.US, "%s %.1f%s%s this week", if (d < 0) "down" else "up", abs(d), NBSP, unit(s.settings.pounds))
    }

    fun habitName(h: Habit, s: HomeState): String = when (h) {
        Habit.STEPS -> "${compact(s.goals.steps)} steps"
        Habit.CALORIES -> "Under ${n(s.goals.calories)} kcal"
        Habit.WEIGH -> "Weigh-in"
        Habit.BATH -> "Bath"
    }

    fun habitShort(h: Habit, s: HomeState): String = when (h) {
        Habit.STEPS -> "${compact(s.goals.steps)} steps"
        Habit.CALORIES -> "≤${n(s.goals.calories)} kcal"
        Habit.WEIGH -> "Weigh-in"
        Habit.BATH -> "Bath"
    }

    /** 10000 -> "10k", 12500 -> "12.5k". */
    fun compact(v: Int): String = if (v % 1000 == 0) "${v / 1000}k" else String.format(Locale.US, "%.1fk", v / 1000.0)

    fun rarityColor(r: Rarity) = when (r) {
        Rarity.COMMON -> C.ink
        Rarity.UNCOMMON -> C.bath
        Rarity.RARE -> C.flame
        Rarity.HOLO -> C.weigh
        Rarity.LEGENDARY -> C.swap
        Rarity.SECRET -> C.steps
    }

    fun rarityLine(r: Rarity) = when (r) {
        Rarity.COMMON -> "Common"
        Rarity.UNCOMMON -> "Uncommon"
        Rarity.RARE -> "Rare · 1 in 10"
        Rarity.HOLO -> "Holo · 1 in 20"
        Rarity.LEGENDARY -> "Legendary"
        Rarity.SECRET -> "Secret"
    }

    fun minutesOfDay(m: Int): String {
        val h = m / 60
        val mm = m % 60
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format(Locale.US, "%d:%02d%s%s", h12, mm, NBSP, ampm)
    }

    /** "7:58–8:14 AM", or "11:50 AM–12:10 PM" across noon; minutes after midnight. */
    fun minutesRange(lo: Int, hi: Int): String {
        val a = minutesOfDay(lo)
        val b = minutesOfDay(hi)
        val sameHalf = (lo / 60 < 12) == (hi / 60 < 12)
        return if (sameHalf) a.substringBefore(NBSP) + "–" + b else "$a–$b"
    }

    fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> monthDay.format(d)
    }

    fun ago(millis: Long?, now: Long): String {
        if (millis == null) return "Not synced yet"
        val m = (now - millis) / 60_000
        return when {
            m < 1 -> "Synced just now"
            m < 60 -> "Synced $m min ago"
            m < 24 * 60 -> "Synced ${m / 60} h ago"
            else -> "Synced ${m / (24 * 60)} days ago"
        }
    }

    fun mealLine(d: Day): List<Pair<String, Int>> =
        listOf("Breakfast" to d.breakfast, "Lunch" to d.lunch, "Snacks" to d.snacks, "Dinner" to d.dinner).filter { it.second > 0 }
}
