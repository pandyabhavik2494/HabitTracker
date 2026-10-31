package com.pandyabhavik.habittracker

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.pandyabhavik.habittracker.widget.BathHoldActivity
import com.pandyabhavik.habittracker.widget.Widgets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Puts all seven home-screen widgets on a test "home screen" (this process hosts them, the way a launcher
 * does) at their provider sizes (2x2, 4x1, 4x2, 4x4), and screenshots them at four moments of a day:
 * morning (1/4), midday (2/4), 9:18 PM at risk (3/4) and a Perfect Day. Each widget's text is checked
 * against the moment. Then the Bath widget is tapped: its hold screen opens, a real 1-second hold logs the
 * bath, and the widget redraws as logged.
 */
@RunWith(AndroidJUnit4::class)
class WidgetsTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val repo get() = Graph.repo(context)

    /** For the Bath widget's hold screen, which is Compose. */
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(
            // POST_NOTIFICATIONS only exists from API 33; granting it on 30-32 makes the rule fail.
            if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else RuleChain.emptyRuleChain(),
        )
        .around(compose)

    /** Counts the RemoteViews the widget pushes, so the test knows when it has drawn (and redrawn). */
    private class CountingHostView(context: Context) : AppWidgetHostView(context) {
        val renders = AtomicInteger()
        @Volatile var lastLayoutId = 0

        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            // null is the host's own "loading" placeholder, not something the widget drew.
            if (remoteViews != null) {
                lastLayoutId = remoteViews.layoutId
                renders.incrementAndGet()
            }
        }
    }

    private class CountingHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            CountingHostView(context)
    }

    /** One widget on the test home screen. Sizes are in dp. */
    private class Placed(val name: String, receiver: String, val widthDp: Int, val heightDp: Int, packageName: String) {
        val provider = ComponentName(packageName, "com.pandyabhavik.habittracker.widget.$receiver")
        var id = AppWidgetManager.INVALID_APPWIDGET_ID
        var view: CountingHostView? = null
        val label get() = "$name ${widthDp}x$heightDp"
        fun renders() = view?.renders?.get() ?: 0

        fun options(): Bundle = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            if (Build.VERSION.SDK_INT >= 31) {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
                )
            }
        }
    }

    private var root: FrameLayout? = null
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        TestData.restoreClock(repo)
    }

    @Test
    fun everyWidget() {
        TestData.quietWorkManager(context)
        runBlocking { TestData.seed(repo, Scene.MORNING_WEIGHED) }
        val pkg = context.packageName
        val seal = Placed("Seal", "SealWidgetReceiver", SQUARE, SQUARE, pkg)
        val list = Placed("List", "ListWidgetReceiver", SQUARE, SQUARE, pkg)
        val streak = Placed("Streak", "StreakWidgetReceiver", SQUARE, SQUARE, pkg)
        val bath = Placed("Bath", "BathWidgetReceiver", SQUARE, SQUARE, pkg)
        val strip = Placed("Strip", "StripWidgetReceiver", WIDE_W, STRIP_H, pkg)
        val wide = Placed("Wide", "WideWidgetReceiver", WIDE_W, SQUARE, pkg)
        val large = Placed("Large", "LargeWidgetReceiver", WIDE_W, WIDE_W, pkg)
        val placed = listOf(seal, list, streak, bath, strip, wide, large)

        val manager = AppWidgetManager.getInstance(context)
        val installed = manager.getInstalledProvidersForPackage(pkg, null)
        val missing = placed.map { it.provider }.distinct().filter { p -> installed.none { it.provider == p } }
        if (missing.isNotEmpty()) {
            fail("Widget providers not in the manifest: ${missing.joinToString { it.className }}")
        }

        // Instrumentation runs in the app's process, so the app is the widget host and needs the
        // bind grant a launcher has. It is given per install, and Gradle has just (re)installed the app.
        val grant = Shots.shell("appwidget grantbind --package $pkg --user ${Process.myUid() / 100_000}").trim()
        Log.i(TAG, "appwidget grantbind: '$grant'")

        val host = CountingHost(context, HOST_ID)
        // A run that died half way may have left widgets bound to this host.
        runCatching { host.deleteHost() }
        host.startListening()
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        this.scenario = scenario
        val errors = mutableListOf<String>()
        try {
            scenario.onActivity { activity ->
                val wallpaper = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(WALL_TOP, WALL_BOTTOM))
                activity.window.setBackgroundDrawable(ColorDrawable(WALL_TOP))
                val container = FrameLayout(activity).apply { background = wallpaper }
                root = container
                activity.setContentView(container)
            }
            waitForRootLayout(scenario)

            for (w in placed) {
                w.id = host.allocateAppWidgetId()
                if (!manager.bindAppWidgetIdIfAllowed(w.id, w.provider, w.options())) {
                    fail(
                        "Not allowed to bind the ${w.label} widget (${w.provider.className}): " +
                            "`appwidget grantbind` didn't take effect (it said: '$grant').",
                    )
                }
            }
            scenario.onActivity { activity -> layOut(activity, host, placed, installed) }
            // Binding sends an update already; this makes sure, the way a launcher's first add does.
            for (w in placed) {
                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(w.provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(w.id)),
                )
            }
            awaitRenders(placed, emptyMap(), 25_000)?.let { fail(it) }

            val streakDays = TestData.STREAK
            val next = TestData.STREAK + 1
            val countdown = "2h 42m"
            val moments = listOf(
                Moment(
                    Scene.MORNING_WEIGHED, "30-widgets-morning",
                    mapOf(
                        seal to "1 of 4 closed", list to "$streakDays", streak to "day streak", bath to "Hold to log bath",
                        strip to "weigh closed", wide to "$streakDays-day streak", large to "BEST NEXT MOVE",
                    ),
                ),
                Moment(
                    Scene.MIDDAY, "31-widgets-midday",
                    mapOf(
                        seal to "2 of 4 closed", list to "$streakDays", streak to "day streak", bath to "Bath · 8:05 AM",
                        strip to "bath closed", wide to "$streakDays-day streak", large to "BEST NEXT MOVE",
                    ),
                ),
                Moment(
                    Scene.RISK, "32-widgets-risk",
                    mapOf(
                        seal to "3 of 4 closed", list to "$streakDays", streak to "ends in $countdown", bath to "Bath · 8:05 AM",
                        strip to "steps open", wide to "Streak ends in $countdown", large to "STREAK ENDS IN $countdown",
                    ),
                ),
                Moment(
                    Scene.PERFECT, "33-widgets-perfect",
                    mapOf(
                        seal to "Perfect Day, all four closed", list to "$next", streak to "days · best $next", bath to "Bath · 8:05 AM",
                        strip to "steps closed", wide to "Perfect Day #$next · $next days", large to "ALL FOUR CLOSED",
                    ),
                ),
            )
            for ((i, m) in moments.withIndex()) {
                // The first moment is already seeded and drawn; the others are seeded over it.
                if (i > 0) runBlocking { TestData.seed(repo, m.scene) }
                runBlocking { Widgets.refresh(context) }
                // Still take the screenshot when a widget is wrong: the picture shows which one.
                awaitContent(m.expect, 20_000)?.let { errors += "${m.scene}: $it" }
                settle(placed)
                checkNoErrorLayout(placed, m.scene.name)?.let { errors += it }
                shot(m.shot)
            }

            // The Bath widget: tap, hold for a second on the screen it opens, and the bath is logged.
            runBlocking { TestData.seed(repo, Scene.MORNING_WEIGHED) }
            runBlocking { Widgets.refresh(context) }
            awaitContent(mapOf(bath to "Hold to log bath", seal to "1 of 4 closed"), 20_000)?.let { fail("Before the bath tap: $it") }
            settle(placed)
            tapWidget(bath)
            awaitActivity(BathHoldActivity::class.java, resumed = true, timeoutMs = 10_000)
            val holdTag = "hold-Hold to log bath"
            compose.waitUntil(10_000) { compose.onAllNodesWithTag(holdTag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            shot("34-bath-hold-from-widget")
            val before = bath.renders()
            runCatching {
                compose.onNodeWithTag(holdTag, useUnmergedTree = true).performTouchInput {
                    down(center)
                    advanceEventTime(1_300)
                    up()
                }
            }.onFailure { Log.w(TAG, "Holding the bath button: ${it.message}") }
            val end = SystemClock.uptimeMillis() + 10_000
            while (runBlocking { repo.dao.day(repo.today().toString())?.bathAt } == null) {
                if (SystemClock.uptimeMillis() > end) fail("Holding the button on the Bath widget's screen didn't log the bath")
                Thread.sleep(100)
            }
            awaitActivity(BathHoldActivity::class.java, resumed = false, timeoutMs = 10_000)
            awaitRenders(listOf(bath), mapOf(bath to before), 15_000)?.let { errors += "After the bath: $it" }
            awaitContent(mapOf(bath to "Bath · 7:12 AM", seal to "2 of 4 closed", strip to "bath closed"), 20_000)
                ?.let { errors += "After the bath: $it" }
            settle(placed)
            checkNoErrorLayout(placed, "bath logged")?.let { errors += it }
            shot("35-widgets-bath-logged")

            if (errors.isNotEmpty()) fail(errors.joinToString("\n"))
        } finally {
            for (w in placed) {
                if (w.id != AppWidgetManager.INVALID_APPWIDGET_ID) runCatching { host.deleteAppWidgetId(w.id) }
            }
            runCatching { host.stopListening() }
            runCatching { host.deleteHost() }
            runCatching { scenario.close() }
            this.scenario = null
            root = null
        }
    }

    private class Moment(val scene: Scene, val shot: String, val expect: Map<Placed, String>)

    private fun waitForRootLayout(scenario: ActivityScenario<ComponentActivity>) {
        val end = SystemClock.uptimeMillis() + 5_000
        while (true) {
            var laidOut = false
            scenario.onActivity { laidOut = (root?.width ?: 0) > 0 && (root?.height ?: 0) > 0 }
            if (laidOut) return
            if (SystemClock.uptimeMillis() > end) fail("The widget test's host activity never laid out")
            Thread.sleep(100)
        }
    }

    /**
     * Wraps the widgets into rows that fit the screen's width, each under a small caption. Widgets keep
     * their real size; if the whole grid is taller than the screen (the cover screen), it is scaled down
     * as a picture so every widget is still in the screenshot.
     */
    private fun layOut(activity: ComponentActivity, host: AppWidgetHost, placed: List<Placed>, installed: List<AppWidgetProviderInfo>) {
        val root = checkNotNull(this.root)
        val density = activity.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        val gap = px(GAP)
        val pad = px(PAD)
        val caption = px(CAPTION)

        val maxRowWidth = root.width - 2 * pad
        val rows = mutableListOf<MutableList<Placed>>()
        for (w in placed) {
            val row = rows.lastOrNull()
            val used = row?.sumOf { px(it.widthDp) + gap } ?: 0
            if (row != null && used + px(w.widthDp) <= maxRowWidth) row += w else rows += mutableListOf(w)
        }

        val grid = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
        }
        var total = 2 * pad
        rows.forEachIndexed { r, row ->
            val rowView = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
            }
            row.forEachIndexed { i, w ->
                val info = installed.first { it.provider == w.provider }
                val view = host.createView(activity, w.id, info) as? CountingHostView
                    ?: fail("Couldn't create a view for the ${w.label} widget")
                view.setPadding(0, 0, 0, 0)
                w.view = view
                val cell = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(activity).apply {
                            text = w.label.replace("x", " × ") + " dp"
                            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
                            setTextColor(Color.WHITE)
                            setShadowLayer(3f, 0f, 1f, 0x80000000.toInt())
                            gravity = Gravity.CENTER_VERTICAL
                            maxLines = 1
                        },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, caption),
                    )
                    addView(view, LinearLayout.LayoutParams(px(w.widthDp), px(w.heightDp)))
                }
                rowView.addView(
                    cell,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (i > 0) leftMargin = gap
                    },
                )
            }
            grid.addView(
                rowView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (r > 0) topMargin = gap
                },
            )
            total += caption + row.maxOf { px(it.heightDp) } + if (r > 0) gap else 0
        }

        // An exact height lets the grid be taller than the screen; the scale then fits it back in.
        root.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, total))
        if (total > root.height) {
            val scale = root.height.toFloat() / total
            grid.pivotX = root.width / 2f
            grid.pivotY = 0f
            grid.scaleX = scale
            grid.scaleY = scale
            Log.i(TAG, "Widget grid is ${total}px tall on a ${root.height}px screen; drawn at ${"%.2f".format(scale)}x")
        }
    }

    /** Null once every widget has pushed a new RemoteViews since [before], else what's still missing. */
    private fun awaitRenders(placed: List<Placed>, before: Map<Placed, Int>, timeoutMs: Long): String? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            val waiting = placed.filter { it.renders() <= (before[it] ?: 0) }
            if (waiting.isEmpty()) return null
            if (SystemClock.uptimeMillis() > end) {
                return "No render within ${timeoutMs / 1000} s from: ${waiting.joinToString { it.label }}"
            }
            Thread.sleep(200)
        }
    }

    /** The texts and content descriptions a widget shows right now. */
    private fun shown(w: Placed): List<String> {
        val out = ArrayList<String>()
        fun walk(v: View) {
            if (v is TextView) v.text?.let { out += it.toString() }
            v.contentDescription?.let { out += it.toString() }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        instrumentation.runOnMainSync { w.view?.let { walk(it) } }
        return out
    }

    /**
     * Glance draws asynchronously (and can push a stale frame first), so this waits until every widget
     * shows its expected text (in part, with no-break spaces read as plain ones). Null when they all do, else which ones don't and what they show instead.
     */
    private fun awaitContent(expect: Map<Placed, String>, timeoutMs: Long): String? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            // Times may come with a no-break space before AM/PM (and the countdown between "2h" and "42m").
            val wrong = expect.filter { (w, text) -> shown(w).none { norm(it).contains(norm(text)) } }
            if (wrong.isEmpty()) return null
            if (SystemClock.uptimeMillis() > end) {
                return wrong.entries.joinToString("; ") { (w, text) -> "${w.label} should show '$text' but shows ${shown(w)}" }
            }
            Thread.sleep(200)
        }
    }

    /** Glance can push more than once (the data flow and the refresh); wait until it's quiet for 1.5 s. */
    private fun settle(placed: List<Placed>) {
        val end = SystemClock.uptimeMillis() + 10_000
        var last = placed.sumOf { it.renders() }
        var quietSince = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() < end && SystemClock.uptimeMillis() - quietSince < 1_500) {
            Thread.sleep(100)
            val now = placed.sumOf { it.renders() }
            if (now != last) {
                last = now
                quietSince = SystemClock.uptimeMillis()
            }
        }
    }

    /** Glance shows its error layout when a widget's composition throws. */
    private fun checkNoErrorLayout(placed: List<Placed>, moment: String): String? {
        @Suppress("DiscouragedApi")
        val errorLayout = context.resources.getIdentifier("glance_error_layout", "layout", context.packageName)
        if (errorLayout == 0) return null
        val broken = placed.filter { it.view?.lastLayoutId == errorLayout }
        return if (broken.isEmpty()) null else "$moment: Glance's error layout is showing for ${broken.joinToString { it.label }} (see logcat)"
    }

    /** Taps a widget the way a launcher passes a touch on: the first view in it that handles clicks. */
    private fun tapWidget(w: Placed) {
        fun clickable(v: View): View? {
            if (v.hasOnClickListeners()) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) clickable(v.getChildAt(i))?.let { return it }
            return null
        }
        var clicked = false
        instrumentation.runOnMainSync {
            val hostView = w.view
            if (hostView != null) {
                val target = (0 until hostView.childCount).firstNotNullOfOrNull { clickable(hostView.getChildAt(it)) }
                clicked = target?.performClick() == true
            }
        }
        if (!clicked) fail("The ${w.label} widget has nothing to tap")
    }

    private fun awaitActivity(type: Class<out Activity>, resumed: Boolean, timeoutMs: Long) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            var isResumed = false
            instrumentation.runOnMainSync {
                isResumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { type.isInstance(it) }
            }
            if (isResumed == resumed) return
            if (SystemClock.uptimeMillis() > end) {
                fail(if (resumed) "${type.simpleName} didn't open" else "${type.simpleName} didn't close")
            }
            Thread.sleep(100)
        }
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)

    private fun shot(name: String) {
        instrumentation.waitForIdleSync()
        Shots.save(name)
    }

    companion object {
        private const val TAG = "WidgetsTest"
        private const val HOST_ID = 0x4AB1
        private const val SQUARE = 150
        private const val WIDE_W = 328
        private const val STRIP_H = 72
        private const val GAP = 12
        private const val PAD = 16
        private const val CAPTION = 20
        private val WALL_TOP = 0xFF4E6A7A.toInt()
        private val WALL_BOTTOM = 0xFF9FB0B8.toInt()
    }
}
