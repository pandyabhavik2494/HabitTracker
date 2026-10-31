package com.pandyabhavik.habittracker

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.pandyabhavik.habittracker.core.Catalog
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.core.Rarity
import com.pandyabhavik.habittracker.ui.F
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.time.YearMonth

/**
 * Walks every screen in realistic states and saves a screenshot of each to /sdcard/Download/habit-screens
 * (pulled into the CI results). CI runs it at the Fold's cover-screen size and at its inner-screen size;
 * the app picks its layout from the width, and so do these tests. Key texts are asserted too, so a broken
 * screen fails the run instead of only looking wrong in a picture.
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {
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

    /**
     * Scrolls the screen tagged [screen] until [to] is in view, and further down by a screenful if that
     * didn't move it (the inner screen shows more at once). True when the screen moved, so a screenshot
     * now differs from the one before.
     */
    private fun scrolledDown(screen: String, to: String): Boolean {
        val top = app.scrollPosition(screen)
        app.scrollTo(to)
        if (app.scrollPosition(screen) == top) app.scrollDown(screen)
        return app.scrollPosition(screen) != top
    }

    @Test
    fun onboarding() {
        app.open(Scene.FRESH)
        app.waitTag("onboarding")
        app.waitTag("onboarding-next")
        app.assertText("Get started")
        shot("01-onboarding-welcome")

        app.click("onboarding-next")
        app.waitText("Connect your data")
        shot("02-onboarding-health")
        // No Health Connect access on the emulator, so the page offers to skip.
        if (app.exists("onboarding-skip")) app.click("onboarding-skip") else app.click("onboarding-health")

        app.waitText("Four nudges, at most")
        shot("03-onboarding-nudges")
        // Notifications are granted by the rule, so this is "Continue" and doesn't open the system dialog.
        if (app.exists("onboarding-skip-notifications")) app.click("onboarding-skip-notifications") else app.click("onboarding-notifications")

        app.waitText("DAY 1")
        app.waitTag("hold-Hold to pledge")
        shot("04-onboarding-pledge")
        app.scrollTo("hold-Hold to pledge").performSemanticsAction(SemanticsActions.OnClick)

        app.waitTag("today", 15_000)
        app.quietSync()
        app.waitFor("onboarding to be saved") { app.settings().onboarded }
        app.waitFor("the first pledge to be saved") { app.today()?.pledgedAt != null }
        app.waitText("0/4")
        shot("05-onboarding-done-today")
    }

    @Test
    fun todayThroughTheDay() {
        app.open(Scene.MORNING)
        app.waitText("0/4")
        app.waitTag("pledge-card")
        app.assertText("Perfect Day #${TestData.STREAK + 1}", substring = true)
        app.assertText("${TestData.STREAK}")
        shot("06-today-morning")
        if (!app.wide) {
            app.scrollTo("sync-line")
            shot("07-today-morning-habits")
        }

        app.open(Scene.MIDDAY)
        app.waitText("2/4")
        app.waitTag("next-move")
        app.node("pledge-card").assertDoesNotExist()
        shot("08-today-midday")
        app.assertText("pull down to sync", substring = true)
        // The cover screen scrolls down to its habit rows and sync line. The inner screen's two panes may
        // fit whole; then there's nothing new to show and the shot is left out.
        if (scrolledDown("today", to = "sync-line")) shot("09-today-midday-scrolled")

        app.open(Scene.RISK)
        app.waitText("3/4")
        app.waitTag("risk-card")
        // The clock is fixed at 9:18:00 PM: 2 h 42 min to midnight.
        app.assertTagText("countdown", "2:42:00")
        app.assertText("${TestData.STREAK}-day streak at risk".uppercase())
        shot("10-today-risk")
    }

    @Test
    fun habitSheets() {
        app.open(Scene.MIDDAY)
        app.waitText("2/4")

        app.openSheet(Habit.STEPS)
        app.assertText("6,140 steps")
        shot("11-sheet-steps")
        app.back()
        app.waitGone("sheet-steps")

        app.openSheet(Habit.CALORIES)
        app.assertText("790 kcal")
        app.waitTag("hold-Hold to close the kitchen")
        shot("12-sheet-calories")
        app.back()
        app.waitGone("sheet-calories")

        app.openSheet(Habit.WEIGH)
        app.waitTag("weight-input")
        app.assertText(F.weight(TestData.TODAY_KG, true))
        shot("13-sheet-weigh")
        app.back()
        app.waitGone("sheet-weigh")

        app.openSheet(Habit.BATH)
        app.assertText("Logged at 8:05 AM.")
        app.waitTag("undo-bath")
        shot("14-sheet-bath")
        app.back()
        app.waitGone("sheet-bath")
    }

    @Test
    fun perfectDayAndPack() {
        val seed = TestData.seedFor { it.rarity == Rarity.RARE && it.id !in TestData.OWNED }
        val cat = TestData.rollOf(seed)
        app.open(Scene.PERFECT)
        // A Perfect Day with a sealed pack opens the pack by itself.
        app.waitTag("pack", 15_000)
        app.assertText("Perfect Day #${TestData.STREAK + 1}".uppercase())
        app.assertText("Tap to rip it open")
        shot("15-pack-sealed")

        app.repo.random = kotlin.random.Random(seed)
        app.click("pack")
        app.waitTag("reveal-name", 15_000)
        app.node("reveal-name").assertTextEquals(cat.name)
        app.node("reveal-rarity").assertTextEquals(F.rarityLine(cat.rarity).uppercase())
        app.node("reveal-badge").assertTextEquals("NEW!")
        app.assertText("${TestData.OWNED.size + 1} of ${Catalog.SIZE}")
        shot("16-pack-reveal")

        app.click("reveal-done")
        app.waitGone("pack-flow")
        app.waitTag("perfect-card")
        app.assertText("Perfect Day #${TestData.STREAK + 1}".uppercase())
        // The Seal's own words on a Perfect Day are copy that changes; the day itself is what matters.
        assertNotNull(app.today()?.perfectAt)
        shot("17-today-perfect")
    }

    @Test
    fun contextCards() {
        app.open(Scene.SAVED)
        app.waitTag("saved-card")
        app.assertText("3 swaps saved ${TestData.STREAK} days")
        // Yesterday spent 3; the card's text and the swap pill both show what's left.
        assertEquals(TestData.SAVED_SWAPS_LEFT, app.settings().swaps)
        assertTrue(app.exists(hasText("${TestData.SAVED_SWAPS_LEFT}") and hasAnyAncestor(hasTestTag("swaps-pill"))))
        shot("18-today-saved-by-swaps")

        app.open(Scene.COMEBACK)
        app.waitTag("comeback-card")
        app.assertText("DAY 1 OF 3")
        app.assertText("Comeback: 3 Perfect Days in a row")
        // The record is the streak yesterday broke, the longest in the history.
        assertEquals(TestData.STREAK, app.settings().comebackRecord)
        app.assertText("${TestData.STREAK} days", substring = true)
        shot("19-today-comeback")

        app.open(Scene.PATTERN)
        app.waitTag("pattern-card")
        app.assertText("Wednesdays are your calorie trap.")
        app.assertText("4 of your last 4 Wednesdays missed it.", substring = true)
        // Those misses broke the streak: it runs from last Wednesday.
        assertTrue(app.exists(hasText("${TestData.PATTERN_STREAK}") and hasAnyAncestor(hasTestTag("streak-pill"))))
        shot("20-today-pattern")
    }

    @Test
    fun journey() {
        app.open(Scene.MIDDAY)
        app.tab("journey")
        app.waitTag("journey")
        app.waitTag("calendar")
        app.node("hit-rates").assertExists()
        app.node("week-by-habit").assertExists()
        if (app.wide) app.node("heat-grid").assertExists()
        val month = YearMonth.from(Scene.MIDDAY.now())
        app.assertText(F.monthYear.format(month))
        shot("21-journey")
        if (scrolledDown("journey", to = "hit-rates")) shot("22-journey-scrolled")

        compose.onNode(hasContentDescription("Previous month"), useUnmergedTree = true).performScrollTo().performClick()
        app.waitText(F.monthYear.format(month.minusMonths(1)))
        shot("23-journey-previous-month")
    }

    @Test
    fun album() {
        app.open(Scene.MIDDAY)
        app.tab("stickers")
        app.waitTag("album")
        app.assertText("${TestData.OWNED.size} of ${Catalog.SIZE} cats", substring = true)
        app.waitTag("milestones")
        // Maneki and the secrets not yet found show as silhouettes.
        assertTrue(app.exists(hasContentDescription("Locked sticker")))
        app.node("slot-maneki").assertExists()
        shot("24-album")
        app.scrollTo("slot-lion")
        shot("25-album-scrolled")

        app.click("slot-midnight")
        app.waitTag("sticker-sheet")
        compose.onNode(hasText("HOLO · 1 IN 20") and hasAnyAncestor(hasTestTag("sticker-sheet")), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("Midnight") and hasAnyAncestor(hasTestTag("sticker-sheet")), useUnmergedTree = true).assertExists()
        shot("26-sticker-detail")
        app.back()
        app.waitGone("sticker-sheet")
    }

    @Test
    fun settings() {
        app.open(Scene.MIDDAY)
        app.openSettings()
        app.node("steps-goal-value").assertTextEquals("10,000")
        app.node("calorie-goal-value").assertTextEquals("1,500 kcal")
        app.assertTagText("kitchen-lock-value", "8:30 PM")
        for (section in listOf("GOALS", "NOTIFICATIONS", "FEEL", "HEALTH CONNECT")) app.assertText(section)
        shot("27-settings")
        if (scrolledDown("settings", to = "n-last")) shot("28-settings-notifications")
        val before = app.scrollPosition("settings")
        app.text("Habit Tracker 1.", substring = true).performScrollTo()
        // Only when the bottom wasn't in the last shot already.
        if (app.scrollPosition("settings") != before) shot("29-settings-bottom")
        val s = runBlocking { app.repo.settings.current() }
        assertEquals(10_000, s.stepsGoal)
        assertNotNull(s.lastSync)
    }
}
