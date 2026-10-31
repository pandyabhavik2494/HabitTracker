package com.pandyabhavik.habittracker

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.pandyabhavik.habittracker.core.Cat
import com.pandyabhavik.habittracker.core.Day
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.core.Packs
import com.pandyabhavik.habittracker.core.Streaks
import com.pandyabhavik.habittracker.data.DayEntity
import com.pandyabhavik.habittracker.data.HabitRepository
import com.pandyabhavik.habittracker.data.PackEntity
import com.pandyabhavik.habittracker.data.Settings
import com.pandyabhavik.habittracker.data.StickerEntity
import com.pandyabhavik.habittracker.data.toEntity
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/**
 * The moments the device tests put the app in. Every scene has 60 days of history behind it (mostly
 * Perfect Days, two saved with swaps, a few misses, weight trending down, baths around 8 AM), 10 stickers
 * and 3 swaps, and a fixed clock (the app reads the time through [HabitRepository.clock]). The saved,
 * comeback and pattern scenes change that history (and the swaps) to match their story.
 */
enum class Scene(val hour: Int, val minute: Int) {
    /** A fresh install: onboarding. */
    FRESH(8, 0),

    /** 7:12 AM, nothing closed yet: the pledge card, 0/4. */
    MORNING(7, 12),

    /** 7:12 AM, weighed in: 1/4. */
    MORNING_WEIGHED(7, 12),

    /** 1:40 PM: weigh-in and bath closed, steps and calories open, 2/4 with a next move. */
    MIDDAY(13, 40),

    /** 9:18 PM: steps still open, 3/4, the streak at risk. */
    RISK(21, 18),

    /** 7:45 PM, all four closed and the day's pack still sealed. */
    PERFECT(19, 45),

    /** As [PERFECT], with a secret pack (Ragdoll) waiting too. */
    TWO_PACKS(19, 45),

    /** 6:30 PM: steps and weigh-in closed, calories under with the kitchen open, bath open: 2/4. */
    ACTION(18, 30),

    /** 6:30 PM: like [ACTION] with the kitchen closed, so the bath is the 4th habit: 3/4. */
    FOURTH(18, 30),

    /** 6:30 PM: steps and bath closed, calories under, the weigh-in open: 2/4. */
    WEIGH_OPEN(18, 30),

    /** 8:05 AM, the morning after 3 swaps saved yesterday: 1 swap left. */
    SAVED(8, 5),

    /**
     * 10:20 AM, day 1 of a comeback: yesterday's missed bath broke the 23-day streak (only 2 swaps, not
     * enough to save it). The record shown is that streak.
     */
    COMEBACK(10, 20),

    /**
     * 9:05 AM on a Wednesday, after 4 Wednesdays in a row over the calorie limit. Swaps saved the oldest;
     * the last three broke the streak, so it is 6 days (since last Wednesday).
     */
    PATTERN(9, 5),
    ;

    fun date(): LocalDate {
        val today = LocalDate.now()
        return if (this == PATTERN) today.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY)) else today
    }

    fun now(): LocalDateTime = LocalDateTime.of(date(), LocalTime.of(hour, minute))
}

object TestData {
    const val TAG = "HabitTests"
    const val HISTORY_DAYS = 60

    /** Days in a row (Perfect or saved) up to yesterday in the base history. Today would be Perfect Day #24. */
    const val STREAK = 23
    const val SWAPS = 3

    /** Today's weigh-in; above the history's lowest, so it doesn't unlock the "new lowest weight" secret. */
    const val TODAY_KG = 88.45

    /** The album: 10 cats, three of them doubles. One Holo and two secrets; Maneki and 4 secrets still locked. */
    val OWNED: Map<String, Int> = linkedMapOf(
        "mango" to 2, "oreo" to 1, "patches" to 2, "pickles" to 1, "miso" to 1,
        "blue" to 2, "thor" to 1, "midnight" to 1, "british" to 1, "savannah" to 1,
    )

    /** A day in the history that wasn't Perfect: which habit slipped, and whether 3 swaps saved it. */
    data class Gap(val missed: Habit, val saved: Boolean)

    /** Days ago -> gap. Saved days at 4 and 15 keep the 23-day streak; 24 broke the one before. */
    val BASE_GAPS: Map<Int, Gap> = mapOf(
        4 to Gap(Habit.CALORIES, saved = true),
        15 to Gap(Habit.BATH, saved = true),
        24 to Gap(Habit.STEPS, saved = false),
        38 to Gap(Habit.WEIGH, saved = false),
        51 to Gap(Habit.CALORIES, saved = false),
    )

    /**
     * Four Wednesdays in a row over the calorie limit, so the pattern card shows. Swaps saved the oldest; with
     * fewer than 3 left, the other three broke the streak, so it is 6 days now. The bath slipped 15 days ago, as in the base
     * history, so the bath run (14 days) matches the other scenes.
     */
    val PATTERN_GAPS: Map<Int, Gap> = mapOf(
        7 to Gap(Habit.CALORIES, saved = false),
        14 to Gap(Habit.CALORIES, saved = false),
        15 to Gap(Habit.BATH, saved = false),
        21 to Gap(Habit.CALORIES, saved = false),
        28 to Gap(Habit.CALORIES, saved = true),
        40 to Gap(Habit.STEPS, saved = false),
    )

    /** The pattern scene's streak: the days since last Wednesday's miss. */
    const val PATTERN_STREAK = 6

    /**
     * The comeback's history: the base one a day further back (so the 23-day streak ran up to the day
     * before yesterday), then yesterday's bath missed with no swaps to save it.
     */
    val COMEBACK_GAPS: Map<Int, Gap> =
        BASE_GAPS.mapKeys { it.key + 1 } + (1 to Gap(Habit.BATH, saved = false))

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun millis(date: LocalDate, hour: Int, minute: Int, plusMinutes: Int = 0): Long =
        date.atTime(hour, minute).plusMinutes(plusMinutes.toLong()).atZone(zone).toInstant().toEpochMilli()

    fun millis(at: LocalDateTime): Long = at.atZone(zone).toInstant().toEpochMilli()

    // ---- WorkManager ------------------------------------------------------------------------------

    private class NoopWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result = Result.success()
    }

    @Volatile private var workManagerReady = false

    /**
     * Swaps WorkManager for the test one, with workers that do nothing: a background sync or a nudge
     * firing mid-test would change the data under the screens and post notifications.
     */
    fun quietWorkManager(context: Context) {
        if (workManagerReady) return
        synchronized(this) {
            if (workManagerReady) return
            val config = Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setMinimumLoggingLevel(Log.INFO)
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker? =
                            // Only the app's own sync, nudge and tick jobs are silenced. Glance runs every
                            // widget session as a WorkManager job, so those must run for real.
                            if (workerClassName.startsWith("com.pandyabhavik.habittracker.")) {
                                NoopWorker(appContext, workerParameters)
                            } else {
                                null
                            }
                    },
                )
                .build()
            WorkManagerTestInitHelper.initializeTestWorkManager(context.applicationContext, config)
            workManagerReady = true
        }
    }

    // ---- History ----------------------------------------------------------------------------------

    private val SHAPE = intArrayOf(0, 0, 0, 0, 0, 0, 2, 9, 6, 3, 3, 4, 8, 5, 3, 3, 4, 7, 9, 6, 4, 2, 1, 0)

    /** [total] steps spread over the hours up to [upToHour] the way a real day goes: a morning walk, lunch, the evening. */
    fun hourly(total: Int, upToHour: Int): List<Int> {
        val weights = (0..23).map { if (it <= upToHour) SHAPE[it] else 0 }
        val sum = weights.sum()
        if (sum == 0 || total == 0) return List(24) { 0 }
        val out = weights.map { total * it / sum }.toMutableList()
        val last = (0..23).last { weights[it] > 0 }
        out[last] += total - out.sum()
        return out
    }

    /** The weigh-in [daysAgo]: about 4 kg down over two months, with day-to-day noise. */
    fun weightOn(daysAgo: Int): Double =
        88.35 + daysAgo * 0.062 + listOf(0.0, 0.15, -0.1, 0.05, -0.05, 0.2, -0.15)[daysAgo % 7]

    fun pastDay(today: LocalDate, daysAgo: Int, gap: Gap?): Day {
        val i = daysAgo
        val date = today.minusDays(i.toLong())
        val steps = if (gap?.missed == Habit.STEPS) 6_200 + (i * 137) % 1_500 else 10_150 + (i * 379) % 3_900
        val breakfast = 280 + (i * 37) % 80
        val lunch = 400 + (i * 53) % 80
        // An evening out: dinner alone goes over the limit.
        val dinner = 380 + (i * 29) % 80 + if (gap?.missed == Habit.CALORIES) 520 else 0
        val snacks = if (i % 3 == 0) 0 else 60 + (i * 17) % 90
        val calories = breakfast + lunch + dinner + snacks
        val meals = listOf(breakfast, lunch, dinner, snacks).count { it > 0 } + i % 3
        val kitchen = if (i % 2 == 0) millis(date, 19, 30, i % 25) else null
        val weighed = gap?.missed != Habit.WEIGH
        val bathed = gap?.missed != Habit.BATH
        return Day(
            date = date,
            steps = steps,
            calories = calories,
            meals = meals,
            breakfast = breakfast,
            lunch = lunch,
            dinner = dinner,
            snacks = snacks,
            kitchenClosedAt = kitchen,
            weightKg = if (weighed) weightOn(i) else null,
            weighedAt = if (weighed) millis(date, 6, 40, i % 20) else null,
            bathAt = if (bathed) millis(date, 7, 50, i % 35) else null,
            perfectAt = if (gap == null) kitchen ?: millis(date, 20, 30) else null,
            savedWithSwaps = gap?.saved == true,
            pledgedAt = if (i % 5 != 3) millis(date, 7, 10) else null,
            hourly = hourly(steps, 22),
        )
    }

    /** The finished days before [today], 1 to [HISTORY_DAYS] days back. */
    fun history(today: LocalDate, gaps: Map<Int, Gap> = BASE_GAPS): List<DayEntity> =
        (1..HISTORY_DAYS).map { pastDay(today, it, gaps[it]).toEntity() }

    /** Today, as far as it has got at the scene's time. */
    fun todayOf(scene: Scene): Day? {
        val d = scene.date()
        val weighed = { day: Day -> day.copy(weightKg = TODAY_KG, weighedAt = millis(d, 6, 52)) }
        val bathed = { day: Day -> day.copy(bathAt = millis(d, 8, 5)) }
        val pledged = { day: Day -> day.copy(pledgedAt = millis(d, 7, 15)) }
        fun food(day: Day, breakfast: Int, lunch: Int, dinner: Int, snacks: Int, entries: Int) = day.copy(
            breakfast = breakfast, lunch = lunch, dinner = dinner, snacks = snacks,
            calories = breakfast + lunch + dinner + snacks, meals = entries,
        )
        fun walked(steps: Int) = Day(d, steps = steps, hourly = hourly(steps, scene.hour))
        return when (scene) {
            Scene.FRESH -> null
            Scene.MORNING -> walked(820)
            Scene.MORNING_WEIGHED, Scene.SAVED, Scene.PATTERN -> weighed(walked(if (scene == Scene.PATTERN) 1_460 else 820))
            Scene.COMEBACK -> bathed(weighed(food(walked(2_350), 330, 0, 0, 0, 2)))
            Scene.MIDDAY -> pledged(bathed(weighed(food(walked(6_140), 330, 460, 0, 0, 3))))
            Scene.RISK -> pledged(bathed(weighed(food(walked(7_640), 330, 460, 450, 0, 5))))
            Scene.PERFECT, Scene.TWO_PACKS ->
                pledged(bathed(weighed(food(walked(10_860), 330, 460, 520, 0, 4)))).copy(kitchenClosedAt = millis(d, 19, 20))
            Scene.ACTION -> pledged(weighed(food(walked(10_420), 330, 460, 390, 0, 4)))
            Scene.FOURTH -> pledged(weighed(food(walked(10_420), 330, 460, 390, 0, 4))).copy(kitchenClosedAt = millis(d, 18, 10))
            Scene.WEIGH_OPEN -> pledged(bathed(food(walked(10_420), 330, 460, 390, 0, 4)))
        }
    }

    fun gapsOf(scene: Scene): Map<Int, Gap> = when (scene) {
        Scene.SAVED -> BASE_GAPS + (1 to Gap(Habit.CALORIES, saved = true))
        Scene.COMEBACK -> COMEBACK_GAPS
        Scene.PATTERN -> PATTERN_GAPS
        else -> BASE_GAPS
    }

    /** The settings a seeded scene starts with (Health Connect last synced 4 minutes before the scene). */
    fun settingsOf(scene: Scene): Settings {
        val d = scene.date()
        val base = Settings(
            onboarded = true,
            swaps = SWAPS,
            // Everything before today is settled, so opening the app doesn't re-judge the seeded history.
            settledUntil = d,
            firstDay = d.minusDays(HISTORY_DAYS.toLong()),
            lastSync = millis(scene.now()) - 4 * 60_000,
            lastSyncError = null,
        )
        return when (scene) {
            // Spent 3 of 4 on yesterday; the saved card and the swap pill both say 1 left.
            Scene.SAVED -> base.copy(swaps = SAVED_SWAPS_LEFT)
            // Fewer than 3 swaps is why yesterday broke the streak. The record is the best run in the
            // history, the way the app works it out when a streak breaks.
            Scene.COMEBACK -> base.copy(swaps = 2, comebackFrom = d, comebackRecord = bestRun(d, gapsOf(scene)))
            // Fewer than 3 swaps, so last Wednesday's miss broke the streak instead of being saved.
            Scene.PATTERN -> base.copy(swaps = 2)
            else -> base
        }
    }

    const val SAVED_SWAPS_LEFT = 1

    /** The longest run of Perfect or saved days in the seeded history. */
    fun bestRun(today: LocalDate, gaps: Map<Int, Gap>): Int =
        Streaks.best((1..HISTORY_DAYS).associate { today.minusDays(it.toLong()) to pastDay(today, it, gaps[it]) })

    /** Clears everything and puts the app in [scene]. Returns the scene's (fixed) time. */
    suspend fun seed(repo: HabitRepository, scene: Scene): LocalDateTime {
        val now = scene.now()
        val d = now.toLocalDate()
        repo.clock = { now }
        repo.random = Random(42)
        repo.dao.clearAll()
        repo.settings.clear()
        if (scene == Scene.FRESH) return now

        repo.dao.upsertDays(history(d, gapsOf(scene)))
        todayOf(scene)?.let { repo.dao.upsertDay(it.toEntity()) }
        OWNED.entries.forEachIndexed { i, (id, count) ->
            val first = millis(d.minusDays(58L - i * 5), 20, 40)
            val last = if (count > 1) millis(d.minusDays(6L + i), 20, 40) else first
            repo.dao.upsertSticker(StickerEntity(id, count, first, last))
        }
        val settings = settingsOf(scene)
        repo.settings.update { settings }
        if (scene == Scene.PERFECT || scene == Scene.TWO_PACKS) {
            // Marks today Perfect and puts its pack on the table, as a sync would.
            repo.evaluate()
            if (scene == Scene.TWO_PACKS) {
                repo.dao.insertPack(
                    PackEntity(date = d.toString(), createdAt = millis(now), reason = HabitRepository.REASON_SECRET, fixedCat = "ragdoll"),
                )
            }
        }
        return now
    }

    fun restoreClock(repo: HabitRepository) {
        repo.clock = { LocalDateTime.now() }
        repo.random = Random.Default
    }

    /** A dice seed whose first daily pack rolls a cat matching [want] (the roll is the same on every device). */
    fun seedFor(want: (Cat) -> Boolean): Int =
        (1..20_000).firstOrNull { want(Packs.roll(Random(it), null)) }
            ?: throw AssertionError("No dice seed rolls the wanted cat")

    fun rollOf(seed: Int): Cat = Packs.roll(Random(seed), null)
}

/** A no-break (U+00A0) or narrow no-break (U+202F) space as a plain one, for comparing times like "8:05 AM". */
fun norm(s: String): String = s.replace('\u00A0', ' ').replace('\u202F', ' ')

/** Screenshots, saved where CI pulls them from. */
object Shots {
    const val DIR = "/sdcard/Download/habit-screens"

    fun shell(command: String): String {
        val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().use { it.readText() }
    }

    fun save(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(800)
        shell("mkdir -p $DIR")
        shell("screencap -p $DIR/$name.png")
    }
}

/**
 * Drives MainActivity for the screen and action tests: seeds a [Scene], launches the app, waits for it,
 * and keeps the Health Connect error (there's no Health Connect access on the emulator) out of the way.
 */
class AppDriver(val compose: ComposeTestRule) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context get() = instrumentation.targetContext
    val repo: HabitRepository get() = Graph.repo(context)
    val device: UiDevice by lazy { UiDevice.getInstance(instrumentation) }
    var scenario: ActivityScenario<MainActivity>? = null
        private set

    /** The inner screen's layout (a rail and tiles) rather than the cover screen's (bottom bar and rows). */
    var wide: Boolean = false
        private set

    fun setUp() {
        TestData.quietWorkManager(context)
    }

    fun tearDown() {
        runCatching { scenario?.close() }
        scenario = null
        TestData.restoreClock(repo)
    }

    fun open(scene: Scene) {
        runCatching { scenario?.close() }
        scenario = null
        runBlocking { TestData.seed(repo, scene) }
        launch()
    }

    /** Opens the app again over the data as it is now. */
    fun relaunch() {
        runCatching { scenario?.close() }
        runBlocking { repo.settings.update { it.copy(lastSyncError = null) } }
        launch()
    }

    private fun launch() {
        val s = ActivityScenario.launch(MainActivity::class.java)
        scenario = s
        s.onActivity { wide = it.resources.configuration.screenWidthDp >= 600 }
        waitFor("the app to show its first screen", 20_000) {
            exists("today") || exists("onboarding") || exists("pack-flow")
        }
        quietSync()
    }

    /**
     * The app syncs Health Connect whenever it starts, and on the emulator that ends in an error (no access),
     * shown in a snackbar and the sync line. Waits for that sync to finish, clears the error, and lets the
     * snackbar time out, so it doesn't cover the screenshots.
     */
    fun quietSync() {
        val end = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < end) {
            val s = runBlocking { repo.settings.current() }
            if (s.lastSyncError != null && !repo.syncing.value) break
            Thread.sleep(100)
        }
        val error = runBlocking { repo.settings.current().lastSyncError }
        Log.i(TestData.TAG, "Sync on open ended with: $error")
        runBlocking { repo.settings.update { it.copy(lastSyncError = null) } }
        idle()
        if (error != null) {
            // Snackbar timeouts run on the test's clock, which only moves when told to.
            var tries = 0
            do {
                compose.mainClock.advanceTimeBy(6_000)
                idle()
                tries++
            } while (exists(hasText(error)) && tries < 3)
        }
    }

    fun idle() {
        runCatching { compose.waitForIdle() }
        instrumentation.waitForIdleSync()
    }

    fun shot(name: String) {
        idle()
        Shots.save(name)
    }

    // ---- Finding things -----------------------------------------------------------------------------

    /** The texts a node shows, as [hasText] reads them. */
    private fun shown(node: SemanticsNode): List<String> {
        val c = node.config
        val texts = if (SemanticsProperties.Text in c) c[SemanticsProperties.Text].map { it.text } else emptyList()
        val edit = if (SemanticsProperties.EditableText in c) listOf(c[SemanticsProperties.EditableText].text) else emptyList()
        return texts + edit
    }

    /**
     * Like [hasText], but a no-break or narrow no-break space counts as a plain one, so "8:05 AM" matches
     * however the app spaces its times.
     */
    fun hasShownText(text: String, substring: Boolean = false): SemanticsMatcher {
        val want = norm(text)
        return SemanticsMatcher("shows '$text'${if (substring) " (in part)" else ""}") { n ->
            shown(n).any { val got = norm(it); if (substring) got.contains(want) else got == want }
        }
    }

    /** Asserts the node tagged [tag] shows exactly [expected] (spaces normalised as in [hasShownText]). */
    fun assertTagText(tag: String, expected: String) {
        val got = shown(node(tag).fetchSemanticsNode())
        if (got.map(::norm) != listOf(norm(expected))) throw AssertionError("'$tag' should show '$expected' but shows $got")
    }

    // ---- Scrolling ----------------------------------------------------------------------------------

    private fun scrollables(within: String) = compose.onAllNodes(
        hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            (hasTestTag(within) or hasAnyAncestor(hasTestTag(within))),
        useUnmergedTree = true,
    )

    /** The column that scrolls the screen tagged [within]: the vertical scroller with the most to scroll. */
    private fun scroller(within: String): Int? {
        val nodes = runCatching { scrollables(within).fetchSemanticsNodes() }.getOrDefault(emptyList())
        return nodes.indices.maxByOrNull { nodes[it].config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }
    }

    /** How far the screen tagged [within] is scrolled, in px; null when nothing in it scrolls. */
    fun scrollPosition(within: String): Float? {
        idle()
        val i = scroller(within) ?: return null
        return runCatching { scrollables(within)[i].fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() }.getOrNull()
    }

    /**
     * Scrolls the screen tagged [within] down by most of its height, through the scroller's own scroll
     * action (so it works on the inner screen's panes as well as the cover screen's column). Returns false
     * when nothing moved: it all fits, as it may on the inner screen.
     */
    fun scrollDown(within: String, fraction: Float = 0.8f): Boolean {
        val before = scrollPosition(within) ?: return false
        val i = scroller(within) ?: return false
        val n = scrollables(within)[i]
        val node = n.fetchSemanticsNode()
        if (node.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() <= 0f) return false
        val dy = node.size.height * fraction
        runCatching { n.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, dy) } }
            .onFailure { Log.w(TestData.TAG, "Scrolling $within: ${it.message}") }
        return scrollPosition(within) != before
    }

    fun exists(tag: String): Boolean = exists(hasTestTag(tag))

    fun exists(matcher: SemanticsMatcher): Boolean =
        runCatching { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    /** Unmerged: the pack flow and the bath dialog are clickable surfaces that would otherwise swallow their children. */
    fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    fun text(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        compose.onNodeWithText(text, substring = substring, useUnmergedTree = true)

    /** At least one node shows [text] (several may: the pack flow sits on top of Today, for one). */
    fun assertText(text: String, substring: Boolean = false) {
        if (!exists(hasShownText(text, substring = substring))) {
            throw AssertionError("Expected the text '$text'${if (substring) " (in part)" else ""} on screen")
        }
    }

    fun waitFor(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (!runCatching(condition).getOrDefault(false)) {
            if (SystemClock.uptimeMillis() > end) throw AssertionError("Timed out after ${timeoutMs / 1000} s waiting for $what")
            runCatching { compose.waitForIdle() }
            Thread.sleep(50)
        }
        idle()
    }

    fun waitTag(tag: String, timeoutMs: Long = 10_000) = waitFor("'$tag' to show", timeoutMs) { exists(tag) }

    fun waitGone(tag: String, timeoutMs: Long = 10_000) = waitFor("'$tag' to go away", timeoutMs) { !exists(tag) }

    fun waitText(text: String, substring: Boolean = false, timeoutMs: Long = 10_000) =
        waitFor("the text '$text'", timeoutMs) { exists(hasShownText(text, substring = substring)) }

    /** Scrolls [tag] into view when it sits in a scrolling column; does nothing when it doesn't. */
    fun scrollTo(tag: String): SemanticsNodeInteraction {
        val n = node(tag)
        runCatching { n.performScrollTo() }
        return n
    }

    fun click(tag: String) {
        scrollTo(tag).performClick()
        idle()
    }

    // ---- App navigation -----------------------------------------------------------------------------

    fun tab(key: String) {
        click("tab-$key")
    }

    fun openSettings() {
        if (wide) click("tab-settings") else click("open-settings")
        waitTag("settings")
    }

    /** Opens a habit's sheet from its row (cover screen) or tile (inner screen). */
    fun openSheet(habit: Habit) {
        val name = habit.name.lowercase()
        click(if (wide) "tile-$name" else "habit-$name")
        waitTag("sheet-$name")
    }

    fun back() {
        idle()
        device.pressBack()
        idle()
    }

    /**
     * Presses a HoldButton and keeps the finger down for 1.3 s (the hold takes 1 s), then lets go. Whatever
     * the hold does (log, close the sheet) happens while the finger is still down.
     */
    fun hold(tag: String) {
        val n = scrollTo(tag)
        runCatching {
            n.performTouchInput {
                down(center)
                advanceEventTime(1_300)
                up()
            }
        }.onFailure { Log.w(TestData.TAG, "Holding $tag: ${it.message}") }
        idle()
    }

    fun today(): DayEntity? = runBlocking { repo.dao.day(repo.today().toString()) }

    fun settings(): Settings = runBlocking { repo.settings.current() }
}
