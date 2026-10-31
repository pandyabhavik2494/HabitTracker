package com.pandyabhavik.habittracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

class RulesTest {
    private val goals = Goals()
    private val today = LocalDate.of(2026, 10, 31)
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(today, LocalTime.of(h, m))

    private fun perfect(d: LocalDate) = Day(d, steps = 10_500, calories = 1_200, meals = 3, weighedAt = 1, bathAt = 1, perfectAt = 1)

    @Test fun stepsCloseAtGoal() {
        assertFalse(Rules.closed(Habit.STEPS, Day(today, steps = 9_999), goals, at(12)))
        assertTrue(Rules.closed(Habit.STEPS, Day(today, steps = 10_000), goals, at(12)))
    }

    @Test fun caloriesNeedALoggedMealAndALockedKitchen() {
        assertEquals(CalorieState.NOT_LOGGED, Rules.calorieState(Day(today), goals, at(21)))
        assertEquals(CalorieState.UNDER, Rules.calorieState(Day(today, calories = 900, meals = 2), goals, at(14)))
        assertEquals(CalorieState.CLOSED, Rules.calorieState(Day(today, calories = 900, meals = 2), goals, at(20, 30)))
        assertEquals(CalorieState.CLOSED, Rules.calorieState(Day(today, calories = 900, meals = 2, kitchenClosedAt = 5), goals, at(13)))
        assertEquals(CalorieState.OVER, Rules.calorieState(Day(today, calories = 1_501, meals = 4, kitchenClosedAt = 5), goals, at(21)))
        assertEquals(CalorieState.CLOSED, Rules.calorieState(Day(today, calories = 1_500, meals = 4), goals, at(21)))
    }

    @Test fun pastDaysCountTheKitchenAsLocked() {
        val y = Day(today.minusDays(1), calories = 1_000, meals = 3)
        assertEquals(CalorieState.CLOSED, Rules.calorieState(y, goals, at(8)))
    }

    @Test fun perfectNeedsAllFour() {
        val d = Day(today, steps = 10_000, calories = 1_000, meals = 3, kitchenClosedAt = 1, weighedAt = 1, bathAt = null)
        assertFalse(Rules.perfect(d, goals, at(15)))
        assertTrue(Rules.perfect(d.copy(bathAt = 2), goals, at(15)))
    }

    @Test fun progressCapsOpenQuarters() {
        val p = Rules.progress(Day(today, steps = 9_990), goals, at(12))
        assertTrue(p.getValue(Habit.STEPS) < 1f)
        assertEquals(0f, p.getValue(Habit.WEIGH))
    }

    @Test fun streakCountsTodayOnlyOncePerfect() {
        val days = (1..5).associate { today.minusDays(it.toLong()) to perfect(today.minusDays(it.toLong())) }
        assertEquals(5, Streaks.current(days, today))
        assertEquals(6, Streaks.current(days + (today to perfect(today)), today))
    }

    @Test fun bestStreakFindsTheLongestRun() {
        val days = HashMap<LocalDate, Day>()
        (1..3).forEach { days[today.minusDays(it.toLong())] = perfect(today.minusDays(it.toLong())) }
        days[today.minusDays(4)] = Day(today.minusDays(4))
        (5..11).forEach { days[today.minusDays(it.toLong())] = perfect(today.minusDays(it.toLong())) }
        assertEquals(7, Streaks.best(days))
    }

    @Test fun swapsSaveAMissedDayAfterAStreak() {
        val days = HashMap<LocalDate, Day>()
        (2..5).forEach { days[today.minusDays(it.toLong())] = perfect(today.minusDays(it.toLong())) }
        days[today.minusDays(1)] = Day(today.minusDays(1), calories = 1_612, meals = 4)
        val s = Streaks.settle(days, today.minusDays(1), today, swaps = 4)
        assertEquals(listOf(today.minusDays(1)), s.saved)
        assertEquals(1, s.swapsLeft)
        assertNull(s.brokenOn)
    }

    @Test fun withoutSwapsTheStreakBreaks() {
        val days = HashMap<LocalDate, Day>()
        (2..5).forEach { days[today.minusDays(it.toLong())] = perfect(today.minusDays(it.toLong())) }
        days[today.minusDays(1)] = Day(today.minusDays(1))
        val s = Streaks.settle(days, today.minusDays(1), today, swaps = 2)
        assertEquals(today.minusDays(1), s.brokenOn)
        assertEquals(4, s.runBeforeBreak)
        assertEquals(2, s.swapsLeft)
    }

    @Test fun missesWithNoStreakDoNotSpendSwaps() {
        val s = Streaks.settle(mapOf(today.minusDays(1) to Day(today.minusDays(1))), today.minusDays(1), today, swaps = 9)
        assertEquals(9, s.swapsLeft)
        assertNull(s.brokenOn)
    }

    @Test fun packOddsFollowTheTable() {
        val r = Random(42)
        val counts = HashMap<Rarity, Int>()
        repeat(100_000) { counts.merge(Packs.rollRarity(r), 1, Int::plus) }
        assertEquals(60_000.0, counts.getValue(Rarity.COMMON).toDouble(), 1_500.0)
        assertEquals(25_000.0, counts.getValue(Rarity.UNCOMMON).toDouble(), 1_000.0)
        assertEquals(10_000.0, counts.getValue(Rarity.RARE).toDouble(), 700.0)
        assertEquals(5_000.0, counts.getValue(Rarity.HOLO).toDouble(), 500.0)
    }

    @Test fun guaranteesNeverRollBelowTheFloor() {
        val r = Random(1)
        repeat(2_000) { assertTrue(Packs.roll(r, Rarity.HOLO).rarity == Rarity.HOLO) }
        repeat(2_000) { assertTrue(Packs.roll(r, Rarity.RARE).rarity.ordinal >= Rarity.RARE.ordinal) }
        assertEquals(Rarity.RARE, Packs.milestoneGuarantee(7))
        assertEquals(Rarity.HOLO, Packs.milestoneGuarantee(30))
        assertNull(Packs.milestoneGuarantee(8))
    }

    @Test fun packsNeverGiveSecretOrLegendaryCats() {
        val r = Random(3)
        repeat(5_000) { assertTrue(Packs.roll(r).rarity in setOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.HOLO)) }
    }

    @Test fun catalogHas24UniqueCats() {
        assertEquals(24, Catalog.all.size)
        assertEquals(24, Catalog.all.map { it.id }.toSet().size)
        assertEquals(17, Catalog.packCats.size)
        assertEquals(6, Catalog.all.count { it.rarity == Rarity.SECRET })
    }

    @Test fun secretsUnlockOnTheirConditions() {
        val days = HashMap<LocalDate, Day>()
        days[today] = perfect(today).copy(steps = 21_000)
        val got = Secrets.unlocked(days, today, LocalTime.of(23, 10), emptySet())
        assertTrue("ragdoll" in got)
        assertTrue("norwegian" in got)
        assertFalse("persian" in got)
        assertTrue(Secrets.unlocked(days, today, LocalTime.of(11, 0), emptySet()).contains("persian"))
        assertFalse(Secrets.unlocked(days, today, LocalTime.of(23, 10), setOf("ragdoll")).contains("ragdoll"))
    }

    @Test fun newLowestWeightNeedsHistory() {
        val days = HashMap<LocalDate, Day>()
        (1..3).forEach { days[today.minusDays(it.toLong())] = perfect(today.minusDays(it.toLong())).copy(weightKg = 80.0 + it) }
        days[today] = perfect(today).copy(weightKg = 80.5)
        assertTrue("savannah" in Secrets.unlocked(days, today, LocalTime.of(15, 0), emptySet()))
        days[today] = perfect(today).copy(weightKg = 81.5)
        assertFalse("savannah" in Secrets.unlocked(days, today, LocalTime.of(15, 0), emptySet()))
    }

    @Test fun aPerfectWeekWithoutSwapsUnlocksBritish() {
        val days = (0..6).associate { today.minusDays(it.toLong()) to perfect(today.minusDays(it.toLong())) }.toMutableMap()
        assertTrue("british" in Secrets.unlocked(days, today, LocalTime.of(15, 0), emptySet()))
        days[today.minusDays(3)] = Day(today.minusDays(3), savedWithSwaps = true)
        assertFalse("british" in Secrets.unlocked(days, today, LocalTime.of(15, 0), emptySet()))
    }

    @Test fun aFullPerfectMonthUnlocksTheLion() {
        val end = LocalDate.of(2026, 11, 30)
        val days = (1..30).associate { LocalDate.of(2026, 11, it) to perfect(LocalDate.of(2026, 11, it)) }
        assertTrue("lion" in Secrets.unlocked(days, end, LocalTime.of(15, 0), emptySet()))
        assertFalse("lion" in Secrets.unlocked(days, end.minusDays(1), LocalTime.of(15, 0), emptySet()))
    }

    @Test fun riskStartsAt8pmWithAStreak() {
        assertFalse(Coach.atRisk(setOf(Habit.STEPS), 5, at(19, 59)))
        assertTrue(Coach.atRisk(setOf(Habit.STEPS), 5, at(20, 0)))
        assertFalse(Coach.atRisk(Habit.entries.toSet(), 5, at(21)))
        assertFalse(Coach.atRisk(setOf(Habit.STEPS), 0, at(21)))
        assertEquals("2:41:55", Coach.clock(Coach.untilMidnight(LocalDateTime.of(today, LocalTime.of(21, 18, 5)))))
    }

    @Test fun sealLineNamesTheLastQuarter() {
        val d = Day(today, steps = 8_760, calories = 1_380, meals = 4, kitchenClosedAt = 1, weighedAt = 1, bathAt = 1)
        val closed = Rules.closedSet(d, goals, at(21, 18))
        assertEquals("1,240 steps away from a Perfect Day", Coach.sealLine(d, goals, at(21, 18), closed, 24))
        assertEquals("Perfect Day #24 is up for grabs", Coach.sealLine(Day(today), goals, at(7), emptySet(), 24))
    }

    @Test fun nextMovePrefersTheInstantQuarterInTheMorning() {
        val m = Coach.nextMove(Day(today, steps = 400), goals, at(7), emptySet())
        assertEquals("Weigh in: instant quarter.", m?.title)
        val evening = Day(today, steps = 8_760, calories = 1_380, meals = 4, weighedAt = 1, bathAt = 1, kitchenClosedAt = 1)
        assertEquals("One 13-minute walk closes steps.", Coach.nextMove(evening, goals, at(21), Rules.closedSet(evening, goals, at(21)))?.title)
    }
}
