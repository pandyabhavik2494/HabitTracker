package com.pandyabhavik.habittracker

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.pandyabhavik.habittracker.core.Catalog
import com.pandyabhavik.habittracker.core.Day
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.core.Rarity
import com.pandyabhavik.habittracker.data.HabitRepository
import com.pandyabhavik.habittracker.data.HomeState
import com.pandyabhavik.habittracker.data.Settings
import com.pandyabhavik.habittracker.data.toEntity
import com.pandyabhavik.habittracker.work.Notifier
import com.pandyabhavik.habittracker.work.Slot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.random.Random

/**
 * Does everything a person can do in the app, end to end, and checks each action reached the database
 * (not only the screen). Screenshots of the important moments go next to the screen tests' ones.
 */
@RunWith(AndroidJUnit4::class)
class ActionsTest {
    private val compose = createEmptyComposeRule()
    private val app = AppDriver(compose)

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(
            // POST_NOTIFICATIONS only exists from API 33; granting it on 30-32 makes the rule fail.
            if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else RuleChain.emptyRuleChain(),
        )
        .around(compose)

    @Before
    fun setUp() = app.setUp()

    @After
    fun tearDown() = app.tearDown()

    private fun shot(name: String) = app.shot(name)

    private fun packsToday() = runBlocking { app.repo.dao.packs().filter { it.date == app.repo.today().toString() } }

    private fun sticker(id: String) = runBlocking { app.repo.dao.sticker(id) }

    /** Scrolls the Weigh-in row (cover screen) or tile (inner screen) fully into view, so a shot shows it whole. */
    private fun showWeighIn() {
        app.scrollTo(if (app.wide) "tile-weigh" else "habit-weigh")
        app.idle()
    }

    /** Long-presses Bath: the row's long-press on the cover screen, the tile's on the inner screen. */
    private fun longPressBath() {
        if (app.wide) {
            app.scrollTo("tile-bath").performTouchInput { longClick() }
        } else {
            app.scrollTo("habit-bath").performSemanticsAction(SemanticsActions.OnLongClick)
        }
        app.idle()
    }

    // ---- Bath ---------------------------------------------------------------------------------------

    @Test
    fun longPressLogsBathAndUndoRemovesIt() {
        app.open(Scene.ACTION)
        app.waitText("2/4")
        assertNull(app.today()!!.bathAt)

        longPressBath()
        app.waitFor("the bath to be saved") { app.today()?.bathAt != null }
        assertEquals(TestData.millis(Scene.ACTION.now()), app.today()!!.bathAt)
        app.waitText("Bath logged")
        app.waitText("3/4")
        shot("40-bath-long-pressed-undo-offered")

        app.text("Undo").performClick()
        app.waitFor("Undo to remove the bath") { app.today()?.bathAt == null }
        app.waitText("2/4")
        shot("41-bath-undone")
    }

    @Test
    fun bathSheetHoldLogsBathAndRemoveTakesItBack() {
        app.open(Scene.ACTION)
        app.waitText("2/4")
        app.openSheet(Habit.BATH)
        app.assertText("Not logged yet today.")
        shot("42-bath-sheet")

        app.hold("hold-Hold to log bath")
        app.waitFor("holding the button to log the bath") { app.today()?.bathAt != null }
        app.waitGone("sheet-bath")
        app.waitText("3/4")

        app.openSheet(Habit.BATH)
        app.assertText("Logged at 6:30 PM.")
        shot("43-bath-sheet-logged")
        app.click("undo-bath")
        app.waitFor("Remove today's bath") { app.today()?.bathAt == null }
        app.waitGone("sheet-bath")
        app.waitText("2/4")
    }

    // ---- Weigh-in and kitchen ------------------------------------------------------------------------

    @Test
    fun typedWeightInPoundsIsStoredInKilos() {
        app.open(Scene.WEIGH_OPEN)
        app.waitText("2/4")
        assertTrue(app.settings().pounds)
        app.openSheet(Habit.WEIGH)
        app.scrollTo("weight-input").performTextInput("195.4")
        app.waitText("Save 195.4 lb")
        shot("44-weigh-sheet-typed")

        app.click("save-weight")
        app.waitFor("the typed weigh-in to be saved") { app.today()?.weighedAt != null }
        val day = app.today()!!
        val kg = day.weightKg!!
        assertTrue("195.4 lb should be stored as ${195.4 / 2.2046226} kg, was $kg", abs(kg - 195.4 / 2.2046226) < 0.01)
        assertTrue(day.weightManual)
        app.waitGone("sheet-weigh")
        app.waitText("3/4")
        app.assertText("195.4", substring = true)
        shot("45-weight-saved")
    }

    @Test
    fun kitchenClosesFromTheSheetAndReopens() {
        app.open(Scene.ACTION)
        app.waitText("2/4")
        app.openSheet(Habit.CALORIES)
        app.assertText("1,180 kcal")
        // A HoldButton's accessibility click completes the hold at once (the bath tests do a real hold).
        app.scrollTo("hold-Hold to close the kitchen").performSemanticsAction(SemanticsActions.OnClick)
        app.waitFor("the kitchen to close") { app.today()?.kitchenClosedAt != null }
        app.waitGone("sheet-calories")
        app.waitText("3/4")
        if (!app.wide) app.assertText("Kitchen closed · 1,180 kcal sealed")
        showWeighIn()
        shot("46-kitchen-closed")

        app.openSheet(Habit.CALORIES)
        app.assertText("Kitchen closed with 320 kcal to spare.", substring = true)
        app.text("Reopen the kitchen").performScrollTo().performClick()
        app.waitFor("the kitchen to reopen") { app.today()?.kitchenClosedAt == null }
        app.waitGone("sheet-calories")
        app.waitText("2/4")
        shot("47-kitchen-reopened")
    }

    // ---- Pledge -------------------------------------------------------------------------------------

    @Test
    fun holdingThePledgeSavesIt() {
        app.open(Scene.MORNING)
        app.waitTag("pledge-card")
        assertNull(app.today()!!.pledgedAt)
        app.hold("hold-Hold to pledge")
        app.waitFor("the pledge to be saved") { app.today()?.pledgedAt != null }
        app.waitGone("pledge-card")
        app.waitTag("next-move")
        shot("48-pledged")
    }

    // ---- Packs --------------------------------------------------------------------------------------

    @Test
    fun closingTheFourthHabitOpensAPackWithANewSticker() {
        val seed = TestData.seedFor { it.rarity == Rarity.UNCOMMON && it.id !in TestData.OWNED }
        val cat = TestData.rollOf(seed)
        app.open(Scene.FOURTH)
        app.waitText("3/4")
        app.repo.random = Random(seed)
        assertTrue(packsToday().isEmpty())

        longPressBath()
        app.waitFor("the Perfect Day to be saved") { app.today()?.perfectAt != null }
        app.waitTag("pack", 15_000)
        val packs = packsToday()
        assertEquals(1, packs.size)
        assertEquals(HabitRepository.REASON_DAILY, packs[0].reason)
        assertNull(packs[0].openedAt)
        shot("50-pack-after-fourth-habit")

        app.click("pack")
        app.waitTag("reveal-name", 15_000)
        app.node("reveal-name").assertTextEquals(cat.name)
        app.node("reveal-badge").assertTextEquals("NEW!")
        assertEquals(1, sticker(cat.id)?.count)
        assertEquals(TestData.SWAPS, app.settings().swaps)
        val opened = packsToday().single()
        assertEquals(cat.id, opened.openedCat)
        assertTrue(opened.wasNew)
        shot("51-reveal-new-sticker")

        app.click("reveal-done")
        app.waitGone("pack-flow")
        app.waitTag("perfect-card")
        app.assertText("Perfect Day #${TestData.STREAK + 1}".uppercase())
    }

    @Test
    fun aDuplicateBecomesASwap() {
        val seed = TestData.seedFor { it.id in TestData.OWNED }
        val cat = TestData.rollOf(seed)
        val before = TestData.OWNED.getValue(cat.id)
        app.open(Scene.PERFECT)
        app.waitTag("pack", 15_000)
        app.repo.random = Random(seed)

        app.click("pack")
        app.waitTag("reveal-name", 15_000)
        app.node("reveal-name").assertTextEquals(cat.name)
        app.node("reveal-badge").assertTextEquals("+1 SWAP")
        app.waitFor("the duplicate to count") { sticker(cat.id)?.count == before + 1 }
        assertEquals(TestData.SWAPS + 1, app.settings().swaps)
        // "4 swaps · trade 3 to save a missed day." (non-breaking space inside "4 swaps").
        app.waitText("${TestData.SWAPS + 1}\u00A0swaps · trade 3", substring = true)
        shot("52-reveal-duplicate-swap")

        app.click("reveal-done")
        app.waitGone("pack-flow")
        app.waitTag("perfect-card")
        app.waitFor("the swap pill to show ${TestData.SWAPS + 1}") {
            app.exists(hasText("${TestData.SWAPS + 1}") and hasAnyAncestor(hasTestTag("swaps-pill")))
        }
    }

    @Test
    fun twoPacksRipOneAfterTheOther() {
        val seed = TestData.seedFor { it.rarity == Rarity.COMMON }
        val first = TestData.rollOf(seed)
        app.open(Scene.TWO_PACKS)
        app.waitTag("pack", 15_000)
        assertEquals(2, packsToday().count { it.openedAt == null })
        app.repo.random = Random(seed)

        app.click("pack")
        app.waitTag("reveal-name", 15_000)
        app.node("reveal-name").assertTextEquals(first.name)
        app.assertText("Rip the next pack · 1 more")
        shot("53-reveal-first-of-two")

        app.click("reveal-done")
        app.waitGone("reveal-name")
        app.waitTag("pack")
        app.assertText("SECRET UNLOCKED")
        shot("54-second-pack-secret")

        app.click("pack")
        app.waitTag("reveal-name", 15_000)
        app.node("reveal-name").assertTextEquals(Catalog["ragdoll"].name)
        app.assertText("Done")
        shot("55-reveal-second")
        app.click("reveal-done")
        app.waitGone("pack-flow")
        app.waitTag("perfect-card")

        val packs = packsToday()
        assertEquals(2, packs.size)
        assertTrue(packs.all { it.openedAt != null })
        assertEquals(1, sticker("ragdoll")?.count)
        assertNotNull(sticker(first.id))
    }

    // ---- Settings -----------------------------------------------------------------------------------

    @Test
    fun goalSteppersChangeGoalsAndToday() {
        app.open(Scene.MIDDAY)
        app.waitText("2/4")
        app.openSettings()

        app.click("calorie-goal-plus")
        app.waitFor("the calorie limit to be saved") { app.settings().calorieGoal == 1_550 }
        app.node("calorie-goal-value").assertTextEquals("1,550 kcal")
        app.click("steps-goal-plus")
        app.waitFor("the steps goal to be saved") { app.settings().stepsGoal == 10_500 }
        app.node("steps-goal-value").assertTextEquals("10,500")
        app.click("steps-goal-minus")
        app.click("steps-goal-minus")
        app.waitFor("the steps goal to go down") { app.settings().stepsGoal == 9_500 }
        app.node("steps-goal-value").assertTextEquals("9,500")
        app.click("kitchen-lock-minus")
        app.waitFor("the kitchen lock to be saved") { app.settings().kitchenLockMinute == 20 * 60 + 15 }
        app.assertTagText("kitchen-lock-value", "8:15 PM")
        shot("56-settings-goals-changed")

        app.click(if (app.wide) "tab-today" else "settings-back")
        app.waitTag("today")
        // Today follows the new goals.
        app.waitText("1,550", substring = true)
        if (!app.wide) {
            app.assertText("Under 1,550 kcal")
            app.assertText("9.5k steps")
            app.assertText("kitchen locks 8:15 PM", substring = true)
        }
        showWeighIn()
        shot("57-today-new-goals")
    }

    @Test
    fun unitToggleSwitchesTheWeightDisplay() {
        app.open(Scene.ACTION)
        app.waitText("2/4")
        app.assertText(" lb", substring = true)
        app.openSettings()
        app.click("unit-kg")
        app.waitFor("kilos to be saved") { !app.settings().pounds }
        app.click(if (app.wide) "tab-today" else "settings-back")
        app.waitTag("today")
        app.waitText(" kg", substring = true)
        assertFalse(app.exists(app.hasShownText(" lb", substring = true)))
        showWeighIn()
        shot("58-today-in-kg")

        app.openSheet(Habit.WEIGH)
        app.assertText(String.format(java.util.Locale.US, "%.1f kg", TestData.TODAY_KG))
        app.back()
        app.waitGone("sheet-weigh")

        app.openSettings()
        app.click("unit-lb")
        app.waitFor("pounds to be saved") { app.settings().pounds }
    }

    @Test
    fun notificationTogglesPersist() {
        app.open(Scene.MIDDAY)
        app.waitText("2/4")
        app.openSettings()
        val toggles = listOf("n-pledge", "n-midday", "n-risk", "n-last", "haptics")
        for (t in toggles) app.scrollTo(t).assertIsOn()
        for (t in toggles) app.click(t)
        app.waitFor("the toggles to be saved") {
            val s = app.settings()
            !s.notifyPledge && !s.notifyMidday && !s.notifyRisk && !s.notifyLastCall && !s.haptics
        }
        shot("59-settings-toggles-off")

        // Still off after the app is closed and opened again.
        app.relaunch()
        app.openSettings()
        for (t in toggles) app.scrollTo(t).assertIsOff()
        app.click("n-midday")
        app.waitFor("Midday to be back on") { app.settings().notifyMidday }
        app.scrollTo("n-midday").assertIsOn()
    }

    @Test
    fun warnMeSavesThePattern() {
        app.open(Scene.PATTERN)
        app.waitTag("pattern-card")
        app.click("warn-me")
        app.waitFor("the warning to be saved") { app.settings().warnDay == DayOfWeek.WEDNESDAY }
        val s = app.settings()
        assertEquals(Habit.CALORIES, s.warnHabit)
        assertEquals("CALORIES-WEDNESDAY", s.dismissedPattern)
        app.waitGone("pattern-card")
        shot("60-pattern-warned")

        app.openSettings()
        app.scrollTo("n-warn").assertIsOn()
        app.assertText("Wednesday heads-up · 5 PM")
        shot("61-settings-warn-toggle")
    }

    // ---- Navigation and sync ------------------------------------------------------------------------

    @Test
    fun tabsAndBackNavigation() {
        app.open(Scene.MIDDAY)
        app.waitTag("today")

        app.tab("journey")
        app.waitTag("journey")
        app.back()
        app.waitTag("today")
        app.waitGone("journey")

        app.tab("stickers")
        app.waitTag("album")
        app.back()
        app.waitTag("today")
        app.waitGone("album")

        app.openSettings()
        app.click(if (app.wide) "tab-today" else "settings-back")
        app.waitTag("today")
        app.waitGone("settings")

        app.openSettings()
        app.back()
        app.waitTag("today")
        app.waitGone("settings")

        app.tab("journey")
        app.waitTag("journey")
        app.tab("stickers")
        app.waitTag("album")
        app.waitGone("journey")
        app.tab("today")
        app.waitTag("today")
        app.waitGone("album")

        // Back on Today leaves the app alone until here; the activity is still up.
        assertEquals(Lifecycle.State.RESUMED, app.scenario!!.state)
    }

    @Test
    fun pullDownSyncs() {
        app.open(Scene.MIDDAY)
        app.waitTag("today")
        assertNull(app.settings().lastSyncError)
        // Pull from inside the content (the top bar itself doesn't scroll, so it starts no pull).
        app.node("today").performTouchInput {
            swipeDown(startY = height * 0.3f, endY = height * 0.95f, durationMillis = 400)
        }
        // There's no Health Connect access on the emulator, so the sync it starts ends in an error.
        app.waitFor("a pull-down to sync", 20_000) { app.settings().lastSyncError != null }
        app.waitFor("the sync to finish", 20_000) { !app.repo.syncing.value }
        shot("62-pull-to-refresh")
        app.quietSync()
    }

    // ---- Notification texts -------------------------------------------------------------------------

    private val date: LocalDate = LocalDate.of(2026, 10, 28)

    private fun state(time: LocalTime, today: Day, history: Boolean = true, settings: (Settings) -> Settings = { it }): HomeState {
        val base = Settings(onboarded = true, swaps = 3, settledUntil = date, firstDay = date.minusDays(60))
        val days = (if (history) TestData.history(date) else emptyList()) + today.toEntity()
        return HomeState.build(LocalDateTime.of(date, time), ZoneId.systemDefault(), days, emptyList(), emptyList(), settings(base))
    }

    private fun at(h: Int, m: Int = 0) = TestData.millis(date, h, m)

    private fun sensible(slot: Slot, text: Pair<String, String>?): Pair<String, String> {
        assertNotNull("$slot should notify", text)
        val (title, body) = text!!
        for (part in listOf(title, body)) {
            assertTrue("$slot: blank text", part.isNotBlank())
            assertFalse("$slot: '$part'", part.contains("null"))
            assertFalse("$slot: negative number in '$part'", Regex("-\\d").containsMatchIn(part))
        }
        assertTrue("$slot title should be a sentence: '$title'", title.endsWith("."))
        return title to body
    }

    @Test
    fun nudgeTextsFitTheDay() {
        val nothing = Day(date)
        val weighed = Day(date, weightKg = 88.4, weighedAt = at(6, 50))

        // 7 AM pledge.
        val pledge = sensible(Slot.PLEDGE, Notifier.nudgeText(Slot.PLEDGE, state(LocalTime.of(7, 0), nothing)))
        assertEquals("Day 24 starts now.", pledge.first)
        assertTrue(pledge.second, pledge.second.startsWith("Weigh in first"))
        val pledgeWeighed = sensible(Slot.PLEDGE, Notifier.nudgeText(Slot.PLEDGE, state(LocalTime.of(7, 0), weighed))).second
        assertTrue(pledgeWeighed, pledgeWeighed.startsWith("Hold to pledge"))
        assertNull(Notifier.nudgeText(Slot.PLEDGE, state(LocalTime.of(7, 0), nothing.copy(pledgedAt = at(6, 58)))))
        assertNull(Notifier.nudgeText(Slot.PLEDGE, state(LocalTime.of(7, 0), nothing) { it.copy(notifyPledge = false) }))

        // 12:30 PM, only when behind on steps.
        val behind = Day(date, steps = 2_000, weightKg = 88.4, weighedAt = at(6, 50))
        val midday = sensible(Slot.MIDDAY, Notifier.nudgeText(Slot.MIDDAY, state(LocalTime.of(12, 30), behind)))
        assertEquals("2,000 steps.", midday.first)
        assertTrue(midday.second, midday.second.contains("3,928"))
        assertNull(Notifier.nudgeText(Slot.MIDDAY, state(LocalTime.of(12, 30), behind.copy(steps = 5_000))))
        assertNull(Notifier.nudgeText(Slot.MIDDAY, state(LocalTime.of(12, 30), behind) { it.copy(notifyMidday = false) }))

        // 5 PM warning, on the weekday the person asked for.
        val lunch = Day(date, steps = 6_000, calories = 700, meals = 2, lunch = 700)
        val warn = sensible(
            Slot.WARN,
            Notifier.nudgeText(Slot.WARN, state(LocalTime.of(17, 0), lunch) { it.copy(warnDay = date.dayOfWeek, warnHabit = Habit.CALORIES) }),
        )
        assertEquals("It's Wednesday.", warn.first)
        assertTrue(warn.second, warn.second.contains("800 kcal left"))
        assertNull(Notifier.nudgeText(Slot.WARN, state(LocalTime.of(17, 0), lunch) { it.copy(warnDay = DayOfWeek.MONDAY, warnHabit = Habit.CALORIES) }))
        assertNull(Notifier.nudgeText(Slot.WARN, state(LocalTime.of(17, 0), lunch)))

        // 9 PM at risk.
        val stepsOpen = Day(
            date, steps = 7_600, calories = 1_200, meals = 4, breakfast = 300, lunch = 500, dinner = 400,
            weightKg = 88.4, weighedAt = at(6, 50), bathAt = at(8, 5),
        )
        val risk = sensible(Slot.RISK, Notifier.nudgeText(Slot.RISK, state(LocalTime.of(21, 0), stepsOpen)))
        assertEquals("2,400 steps between you and Perfect Day #24.", risk.first)
        assertEquals("Your 23-day streak ends at midnight if this quarter stays open.", risk.second)
        val twoOpen = sensible(Slot.RISK, Notifier.nudgeText(Slot.RISK, state(LocalTime.of(21, 0), stepsOpen.copy(bathAt = null))))
        assertEquals("2,400 steps and 1 more between you and Perfect Day #24.", twoOpen.first)
        assertTrue(twoOpen.second, twoOpen.second.endsWith("if these quarters stay open."))
        val noStreak = sensible(Slot.RISK, Notifier.nudgeText(Slot.RISK, state(LocalTime.of(21, 0), stepsOpen.copy(bathAt = null), history = false)))
        assertEquals("2 quarters still open.", noStreak.first)
        assertTrue(noStreak.second, noStreak.second.startsWith("Close them before midnight"))

        // 11 PM last call.
        val bathOpen = stepsOpen.copy(steps = 10_300, bathAt = null)
        val last = sensible(Slot.LAST_CALL, Notifier.nudgeText(Slot.LAST_CALL, state(LocalTime.of(23, 0), bathOpen)))
        assertEquals("Bath is the last quarter.", last.first)
        assertEquals("60 minutes left. Already had one? Tap to log it.", last.second)
        val lastTwo = sensible(Slot.LAST_CALL, Notifier.nudgeText(Slot.LAST_CALL, state(LocalTime.of(23, 0), stepsOpen.copy(bathAt = null))))
        assertEquals("2 quarters open, 60 minutes left.", lastTwo.first)

        // A Perfect Day is never nagged about.
        val perfect = stepsOpen.copy(steps = 10_300, kitchenClosedAt = at(19, 0))
        for (slot in Slot.entries) {
            val time = LocalTime.of(slot.minute / 60, slot.minute % 60)
            assertNull("$slot after a Perfect Day", Notifier.nudgeText(slot, state(time, perfect) { it.copy(warnDay = date.dayOfWeek, warnHabit = Habit.BATH) }))
        }
    }
}
