package com.pandyabhavik.habittracker.ui

import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandyabhavik.habittracker.core.CalorieState
import com.pandyabhavik.habittracker.core.Coach
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.core.Rules
import com.pandyabhavik.habittracker.data.HomeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(s: HomeState) {
    val a = LocalActions.current
    val pulling by a.vm.pulling.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<Habit?>(null) }
    PullToRefreshBox(
        isRefreshing = pulling,
        onRefresh = { a.vm.pullToRefresh() },
        modifier = Modifier
            .fillMaxSize()
            .testTag("today"),
    ) {
        if (a.wide) TodayWide(s) { sheet = it } else TodayCover(s) { sheet = it }
    }
    sheet?.let { HabitSheet(it, s) { sheet = null } }
}

// ---- Cover screen ---------------------------------------------------------------------------------

@Composable
private fun TodayCover(s: HomeState, onHabit: (Habit) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        TopBar(s, showSettings = true)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            BigSeal(s, 236.dp)
        }
        Cards(s, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        HabitRows(s, onHabit)
        SyncLine(s)
        Spacer(Modifier.height(24.dp))
    }
}

// ---- Inner screen ---------------------------------------------------------------------------------

@Composable
private fun TodayWide(s: HomeState, onHabit: (Habit) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        TopBar(s, showSettings = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(0.47f), horizontalAlignment = Alignment.CenterHorizontally) {
                BigSeal(s, 300.dp)
                Spacer(Modifier.height(8.dp))
                Cards(s, Modifier.fillMaxWidth())
            }
            Column(Modifier.weight(0.53f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepsTile(s, Modifier.weight(1f)) { onHabit(Habit.STEPS) }
                    CaloriesTile(s, Modifier.weight(1f)) { onHabit(Habit.CALORIES) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WeighTile(s, Modifier.weight(1f)) { onHabit(Habit.WEIGH) }
                    BathTile(s, Modifier.weight(1f)) { onHabit(Habit.BATH) }
                }
                WeekStrip(s)
                SyncLine(s)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---- Pieces ---------------------------------------------------------------------------------------

@Composable
fun TopBar(s: HomeState, showSettings: Boolean, title: String? = null, kicker: String? = null) {
    val a = LocalActions.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(kicker ?: F.weekday.format(s.now), style = T.small.copy(fontSize = 13.sp))
            Text(title ?: F.monthDay.format(s.now), style = T.title)
        }
        StreakPills(s.streak, s.swaps)
        if (showSettings) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { a.openSettings() }
                    .testTag("open-settings"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Settings, "Settings", tint = C.dim, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun BigSeal(s: HomeState, size: Dp) {
    val open = Habit.entries.filter { it !in s.closed }
    val pulse = if (s.atRisk && open.size == 1) open.first() else null
    val bad = if (s.calorieState == CalorieState.OVER) Habit.CALORIES else null
    Seal(s.progress, size, Modifier.testTag("seal"), pulse = pulse, bad = bad) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size * 0.62f)) {
            if (s.perfect) {
                Icon(Icons.Rounded.Check, null, tint = C.ink, modifier = Modifier.size(size * 0.2f))
                Text("PERFECT DAY", style = T.caps.copy(color = C.ink))
                Text("#${s.dayNumber}", style = T.big)
            } else {
                Text(
                    buildAnnotatedString {
                        append("${s.closedCount}")
                        withStyle(SpanStyle(color = C.dim, fontSize = (size.value * 0.1f).sp)) { append("/4") }
                    },
                    style = T.hero.copy(fontSize = (size.value * 0.24f).sp),
                )
                Text(
                    if (s.comebackFrom != null) "DAY ${s.comebackDay} OF 3" else "CLOSED",
                    style = T.caps,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    s.sealLine,
                    style = T.small.copy(color = if (open.size == 1) C.of(open.first()) else C.mute, fontSize = 12.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** The context cards: the pack, comeback, saved streak, risk, pledge, a pattern, the best next move. */
@Composable
fun Cards(s: HomeState, modifier: Modifier) {
    val a = LocalActions.current
    val hour = s.now.hour
    val showPledge = s.today.pledgedAt == null && hour < 12 && !s.perfect
    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        s.unopened.firstOrNull()?.let { PackWaiting(s, it.id) }
        if (s.perfect) PerfectCard(s)
        if (s.comebackFrom != null && !s.perfect) ComebackCard(s)
        if (s.savedYesterday && hour < 14) SavedCard(s)
        if (s.atRisk) RiskCard(s)
        if (showPledge) PledgeCard(s)
        val p = s.pattern
        if (p != null && hour < 14 && !s.atRisk) PatternCard(s, p.habit, p.weekday, p.misses, p.of)
        val move = s.move
        if (!s.atRisk && !showPledge && move != null) {
            Panel(Modifier.fillMaxWidth().testTag("next-move")) {
                Caps("Best next move", icon = Icons.Rounded.Bolt)
                Spacer(Modifier.height(6.dp))
                Text(move.title, style = T.h2)
                move.detail?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = T.body)
                }
            }
        }
        if (s.settings.lastSync == null && s.settings.lastSyncError != null) {
            Panel(Modifier.fillMaxWidth().clickable { a.askHealth() }.testTag("connect-health"), borderColor = C.bath.copy(alpha = 0.4f)) {
                Caps("Connect Health Connect", color = C.bath, icon = Icons.Rounded.Sync)
                Spacer(Modifier.height(6.dp))
                Text("Steps, food and weight sync from Health Connect. Tap to allow access.", style = T.body)
            }
        }
    }
}

@Composable
private fun PackWaiting(s: HomeState, id: Long) {
    val a = LocalActions.current
    val t by rememberClock()
    val wobble = kotlin.math.sin(t * 6f) * if ((t % 2.4f) < 0.6f) 4f else 0f
    Panel(
        Modifier
            .fillMaxWidth()
            .clickable { a.openPack(id) }
            .testTag("pack-waiting"),
        borderColor = C.weigh.copy(alpha = 0.6f),
        background = Brush.linearGradient(listOf(Color(0xFF2A1E4A), Color(0xFF14283A))),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(54.dp, 70.dp)
                    .graphicsLayer { rotationZ = wobble }
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(C.swap, C.weigh, C.bath))),
                contentAlignment = Alignment.Center,
            ) { Text("?", style = T.big.copy(color = Color.White)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (s.unopened.size > 1) "${s.unopened.size} packs to rip" else "You earned a pack", style = T.h2)
                Text("Tap to rip it open", style = T.body)
            }
        }
    }
}

@Composable
private fun PerfectCard(s: HomeState) {
    val todays = s.packs.filter { it.date == s.now.toLocalDate().toString() && it.openedCat != null }
    Panel(Modifier.fillMaxWidth().testTag("perfect-card"), borderColor = C.weigh.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            todays.lastOrNull()?.openedCat?.let {
                CatSticker(it, 64.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Caps("Perfect Day #${s.dayNumber}", color = C.weigh, icon = Icons.Rounded.EmojiEvents)
                Spacer(Modifier.height(4.dp))
                Text("All four closed. ${s.streak}-day streak.", style = T.h2)
                Text("Come back tomorrow for #${s.dayNumber + 1}. The Seal stays lit tonight.", style = T.body)
            }
        }
    }
}

@Composable
private fun RiskCard(s: HomeState) {
    val a = LocalActions.current
    val open = Habit.entries.filter { it !in s.closed }
    val countdown by produceState(Coach.clock(Coach.untilMidnight(a.vm.repo.now()))) {
        while (true) {
            value = Coach.clock(Coach.untilMidnight(a.vm.repo.now()))
            delay(1000)
        }
    }
    val pulse by rememberClock()
    val alpha = 0.65f + 0.35f * (0.5f + 0.5f * kotlin.math.cos(pulse * Math.PI.toFloat()))
    Panel(
        Modifier.fillMaxWidth().testTag("risk-card"),
        borderColor = C.risk.copy(alpha = 0.45f),
        background = Brush.radialGradient(listOf(Color(0x33FF5A6A), C.s1), radius = 900f),
    ) {
        Caps("${s.dayNumber - 1}-day streak at risk", color = C.risk, icon = Icons.Rounded.Timer)
        Text(countdown, style = T.big.copy(color = C.risk.copy(alpha = alpha)), modifier = Modifier.testTag("countdown"))
        Spacer(Modifier.height(4.dp))
        Text(s.move?.title ?: "${open.size} quarters still open.", style = T.h2)
        Text("Close it and Perfect Day #${s.dayNumber} earns you a sticker pack.", style = T.body)
        Spacer(Modifier.height(10.dp))
        when (open.firstOrNull()) {
            Habit.BATH -> if (open.size == 1) HoldButton("Hold to log bath", haptics = s.settings.haptics) { a.vm.logBath() }
            Habit.CALORIES -> if (open.size == 1 && s.calorieState == CalorieState.UNDER) {
                HoldButton("Hold to close the kitchen", haptics = s.settings.haptics) { a.vm.closeKitchen() }
            }
            Habit.STEPS -> StartWalk()
            else -> Unit
        }
    }
}

@Composable
private fun StartWalk() {
    val context = LocalContext.current
    val intent = remember { fitnessIntent(context) } ?: return
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(C.steps)
            .clickable { runCatching { context.startActivity(intent) } }
            .testTag("start-walk"),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, tint = Color(0xFF06140C))
            Spacer(Modifier.width(6.dp))
            Text("Start walk", style = T.label.copy(color = Color(0xFF06140C)))
        }
    }
}

/** Google Fit or Samsung Health, to start a tracked walk. */
fun fitnessIntent(context: Context): Intent? =
    listOf("com.google.android.apps.fitness", "com.sec.android.app.shealth").firstNotNullOfOrNull {
        context.packageManager.getLaunchIntentForPackage(it)
    }

@Composable
private fun PledgeCard(s: HomeState) {
    val a = LocalActions.current
    Panel(
        Modifier.fillMaxWidth().testTag("pledge-card"),
        borderColor = C.weigh.copy(alpha = 0.35f),
    ) {
        Caps("Today's pledge", icon = Icons.Rounded.WbSunny, color = C.ink)
        Spacer(Modifier.height(6.dp))
        Text("“I close all four today.”", style = T.title)
        Spacer(Modifier.height(4.dp))
        Text(
            if (s.pledgesKept > 0) "You've kept ${s.pledgesKept} pledge${if (s.pledgesKept == 1) "" else "s"} in a row." else "Say it with your thumb. It makes it far more likely.",
            style = T.body,
        )
        Spacer(Modifier.height(10.dp))
        HoldButton("Hold to pledge", haptics = s.settings.haptics) { a.vm.pledge() }
    }
}

@Composable
private fun ComebackCard(s: HomeState) {
    Panel(
        Modifier.fillMaxWidth().testTag("comeback-card"),
        borderColor = C.risk.copy(alpha = 0.35f),
        background = Brush.radialGradient(listOf(Color(0x26FF5A6A), C.s1), radius = 900f),
    ) {
        Caps("Fresh start · comeback", color = C.risk, icon = Icons.Rounded.History)
        Spacer(Modifier.height(6.dp))
        Text("Comeback: 3 Perfect Days in a row", style = T.h2)
        Text("Pull it off and Maneki the Lucky Cat is yours, guaranteed. Comebacks are the only way to get her.", style = T.body)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatSticker("maneki", 56.dp, silhouette = "maneki" !in s.owned)
            val done = (s.comebackDay - 1).coerceAtLeast(0) + if (s.perfect) 1 else 0
            repeat(3) { i ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .then(
                            if (i < done) Modifier.background(Brush.linearGradient(C.iridescent))
                            else Modifier.border(1.5.dp, C.dim, CircleShape),
                        ),
                )
            }
        }
        if (s.comebackRecord > 0) {
            Spacer(Modifier.height(10.dp))
            Text("Your record: ${s.comebackRecord} days. You've done it before.", style = T.small.copy(color = C.ink))
        }
    }
}

@Composable
private fun SavedCard(s: HomeState) {
    val recent = s.owned.values.filter { it.count > 1 }.sortedByDescending { it.lastAt }.take(3).map { it.id }
    Panel(Modifier.fillMaxWidth().testTag("saved-card"), borderColor = C.swap.copy(alpha = 0.35f)) {
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            recent.forEach { CatSticker(it, 48.dp, animate = false) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "3 swaps saved ${s.dayNumber - 1} days",
            style = T.h2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Yesterday slipped, so you traded three duplicates to keep the streak. ${s.swaps} swap${if (s.swaps == 1) "" else "s"} left. Every duplicate you pull adds one.",
            style = T.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PatternCard(s: HomeState, habit: Habit, day: DayOfWeek, misses: Int, of: Int) {
    val a = LocalActions.current
    val name = day.getDisplayName(TextStyle.FULL, Locale.US)
    val what = when (habit) {
        Habit.STEPS -> "steps"
        Habit.CALORIES -> "calorie"
        Habit.WEIGH -> "weigh-in"
        Habit.BATH -> "bath"
    }
    Panel(Modifier.fillMaxWidth().testTag("pattern-card")) {
        Caps("Pattern spotted", icon = Icons.Rounded.Insights)
        Spacer(Modifier.height(6.dp))
        Text("${name}s are your $what trap.", style = T.h2)
        Text("$misses of your last $of ${name}s missed it. Want a 5 PM heads-up on ${name}s?", style = T.body)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(C.s2)
                    .clickable { a.vm.warnMe(day, habit) }
                    .testTag("warn-me"),
                contentAlignment = Alignment.Center,
            ) { Text("Yes, warn me ${name}s", style = T.label) }
            Box(
                Modifier
                    .height(46.dp)
                    .clip(CircleShape)
                    .clickable { a.vm.dismissPattern(day, habit) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("No thanks", style = T.label.copy(color = C.mute)) }
        }
    }
}

// ---- Habit rows (cover) ----------------------------------------------------------------------------

@Composable
private fun HabitRows(s: HomeState, onHabit: (Habit) -> Unit) {
    val a = LocalActions.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Open quarters first: the list always starts with what still needs doing.
    val order = Habit.entries.sortedBy { if (it in s.closed) 1 else 0 }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        order.forEach { h ->
            val closed = h in s.closed
            val d = s.today
            when (h) {
                Habit.STEPS -> HabitRow(
                    h, F.habitName(h, s),
                    if (closed) "Closed · ${F.n(d.steps)} steps" else "${F.n(s.goals.steps - d.steps)} to go · about ${Rules.walkMinutes(s.goals.steps - d.steps)} min of walking",
                    F.n(d.steps), d.steps.toFloat() / s.goals.steps, closed,
                    onClick = { onHabit(h) },
                )
                Habit.CALORIES -> HabitRow(
                    h, F.habitName(h, s),
                    when (s.calorieState) {
                        CalorieState.NOT_LOGGED -> "Nothing logged yet · ${F.n(s.goals.calories)} kcal budget"
                        CalorieState.UNDER -> "${F.n(s.goals.calories - d.calories)} kcal left · kitchen locks ${F.minutesOfDay(s.goals.kitchenLockMinute)}"
                        CalorieState.CLOSED -> "Kitchen closed · ${F.n(d.calories)} kcal sealed"
                        CalorieState.OVER -> "${F.n(d.calories - s.goals.calories)} kcal over today"
                    },
                    F.n(d.calories), if (s.calorieState == CalorieState.UNDER) d.calories.toFloat() / s.goals.calories else 0f, closed,
                    valueColor = if (s.calorieState == CalorieState.OVER) C.risk else C.ink,
                    onClick = { onHabit(h) },
                )
                Habit.WEIGH -> HabitRow(
                    h, "Weigh-in",
                    d.weightKg?.let { kg -> listOfNotNull(F.weight(kg, s.settings.pounds), F.weightTrend(s, kg)).joinToString(" · ") }
                        ?: "Waiting for your scale",
                    if (closed) null else "Now", 0f, closed,
                    valueColor = C.weigh,
                    trailing = if (closed) null else ({ Text("Now", style = T.label.copy(color = C.weigh)) }),
                    onClick = { onHabit(h) },
                )
                Habit.BATH -> HabitRow(
                    h, "Bath",
                    d.bathAt?.let { "Logged ${F.time(it, s.zone)}" } ?: "Long-press to log",
                    null, 0f, closed,
                    trailing = if (closed) null else ({ Icon(Icons.Rounded.TouchApp, null, tint = C.bath) }),
                    modifier = Modifier.semantics { onLongClick("Log bath") { logBath(a, s, view, scope); true } },
                    onClick = { onHabit(h) },
                    onLongPress = if (closed) null else ({ logBath(a, s, view, scope) }),
                )
            }
        }
    }
}

private fun logBath(a: AppActions, s: HomeState, view: android.view.View, scope: kotlinx.coroutines.CoroutineScope) {
    if (s.today.bathAt != null) return
    if (s.settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    a.vm.logBath()
    scope.launch {
        val r = a.snackbar.showSnackbar("Bath logged", actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) a.vm.undoBath()
    }
}

@Composable
fun SyncLine(s: HomeState) {
    val a = LocalActions.current
    val syncing by a.vm.syncing.collectAsStateWithLifecycle()
    val now = s.now.atZone(s.zone).toInstant().toEpochMilli()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .testTag("sync-line"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Sync, null, tint = C.dim, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            when {
                syncing -> "Syncing with Health Connect…"
                s.settings.lastSyncError != null -> s.settings.lastSyncError
                else -> "${F.ago(s.settings.lastSync, now)} · pull down to sync"
            },
            style = T.small.copy(color = if (s.settings.lastSyncError != null && !syncing) C.risk else C.dim),
            modifier = if (s.settings.lastSyncError != null) Modifier.clickable { a.askHealth() } else Modifier,
        )
    }
}

// ---- Tiles (inner screen) -------------------------------------------------------------------------

@Composable
private fun Tile(
    habit: Habit,
    title: String,
    s: HomeState,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val closed = habit in s.closed
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .heightIn(min = 210.dp)
            .clip(shape)
            .background(C.s1)
            .border(1.dp, if (closed) C.of(habit).copy(alpha = 0.5f) else C.line, shape)
            .pointerInput(onLongPress) { detectTapGestures(onTap = { onClick() }, onLongPress = onLongPress?.let { { _ -> it() } }) }
            .padding(14.dp)
            .testTag("tile-${habit.name.lowercase()}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HabitIcon(habit, 32.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = T.label, modifier = Modifier.weight(1f))
            when {
                badge != null -> badge()
                closed -> CheckDot(C.of(habit), 22.dp)
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun BigValue(value: String, unit: String) {
    Text(
        buildAnnotatedString {
            append(value)
            withStyle(SpanStyle(color = C.dim, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(600))) { append(" $unit") }
        },
        style = T.big.copy(fontSize = 28.sp),
        maxLines = 1,
    )
}

@Composable
private fun StepsTile(s: HomeState, modifier: Modifier, onClick: () -> Unit) {
    val d = s.today
    Tile(Habit.STEPS, "Steps", s, modifier, onClick) {
        BigValue(F.n(d.steps), "/ ${F.compact(s.goals.steps)}")
        Spacer(Modifier.height(8.dp))
        HourBars(d.hourly, s.now.hour, Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(8.dp))
        val best = d.hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }
        val left = s.goals.steps - d.steps
        Text(
            listOfNotNull(
                best?.let { "Your best hour was ${F.minutesOfDay(it.index * 60).replace(":00", "")}." },
                if (left > 0) "${F.n(left)} to go." else "Closed.",
            ).joinToString(" "),
            style = T.small,
        )
    }
}

@Composable
fun HourBars(hourly: List<Int>, nowHour: Int, modifier: Modifier) {
    val max = (hourly.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier) {
        val w = size.width / 24f
        for (h in 0 until 24) {
            val v = hourly.getOrElse(h) { 0 }
            val bh = (v.toFloat() / max) * size.height
            val col = if (h <= nowHour) C.steps else C.s3
            drawRoundRect(
                col.copy(alpha = if (v == 0) 0.25f else 1f),
                topLeft = Offset(h * w + w * 0.18f, size.height - bh.coerceAtLeast(2f)),
                size = Size(w * 0.64f, bh.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(w * 0.3f),
            )
        }
    }
}

@Composable
private fun CaloriesTile(s: HomeState, modifier: Modifier, onClick: () -> Unit) {
    val d = s.today
    val locked = Rules.kitchenLocked(d, s.goals, s.now)
    Tile(
        Habit.CALORIES, "Calories", s, modifier, onClick,
        badge = if (locked && Habit.CALORIES !in s.closed) ({ Icon(Icons.Rounded.Lock, null, tint = C.calories) }) else null,
    ) {
        BigValue(F.n(d.calories), "/ ${F.n(s.goals.calories)}")
        Spacer(Modifier.height(8.dp))
        val meals = F.mealLine(d)
        if (meals.isEmpty()) Text(if (d.meals == 0) "Nothing logged in MyFitnessPal yet." else "${d.meals} entries logged.", style = T.small)
        meals.forEach { (name, kcal) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(name, style = T.small.copy(color = C.ink), modifier = Modifier.weight(1f))
                Text(F.n(kcal), style = T.small.copy(color = C.ink))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (s.calorieState) {
                CalorieState.CLOSED -> d.kitchenClosedAt?.let { "Kitchen closed at ${F.time(it, s.zone)} · ${F.n(s.goals.calories - d.calories)} under" }
                    ?: "Kitchen locked at ${F.minutesOfDay(s.goals.kitchenLockMinute)} · ${F.n(s.goals.calories - d.calories)} under"
                CalorieState.OVER -> "${F.n(d.calories - s.goals.calories)} kcal over. Tomorrow's a new day."
                CalorieState.UNDER -> "${F.n(s.goals.calories - d.calories)} left. Kitchen locks at ${F.minutesOfDay(s.goals.kitchenLockMinute)}."
                CalorieState.NOT_LOGGED -> "Log meals in MyFitnessPal; they sync here."
            },
            style = T.small.copy(color = if (s.calorieState == CalorieState.OVER) C.risk else C.mute),
        )
    }
}

@Composable
private fun WeighTile(s: HomeState, modifier: Modifier, onClick: () -> Unit) {
    val d = s.today
    val history = s.days.entries.sortedBy { it.key }.mapNotNull { it.value.weightKg }.takeLast(30)
    Tile(Habit.WEIGH, "Weigh-in", s, modifier, onClick) {
        val latest = d.weightKg ?: history.lastOrNull()
        if (latest != null) {
            val v = String.format(Locale.US, "%.1f", F.weightValue(latest, s.settings.pounds))
            BigValue(v, F.unit(s.settings.pounds))
        } else {
            Text("Step on the scale", style = T.h2)
        }
        Spacer(Modifier.height(8.dp))
        if (history.size >= 2) Sparkline(history.map { F.weightValue(it, s.settings.pounds).toFloat() }, Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(8.dp))
        if (history.size >= 2) {
            val delta = F.weightValue(history.last(), s.settings.pounds) - F.weightValue(history.first(), s.settings.pounds)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.TrendingDown, null, tint = C.weigh, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    String.format(Locale.US, "%.1f %s %s over %d weigh-ins", kotlin.math.abs(delta), F.unit(s.settings.pounds), if (delta <= 0) "down" else "up", history.size),
                    style = T.small,
                )
            }
        } else {
            Text(if (d.weighedAt == null) "Instant quarter: 30 seconds closes it." else "Weighed ${F.time(d.weighedAt, s.zone)}.", style = T.small)
        }
    }
}

@Composable
fun Sparkline(values: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).coerceAtLeast(0.5f)
        val step = size.width / (values.size - 1)
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v - min) / span * size.height * 0.9f - size.height * 0.05f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(p)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(C.weigh.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(p, C.weigh, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun BathTile(s: HomeState, modifier: Modifier, onClick: () -> Unit) {
    val a = LocalActions.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val d = s.today
    Tile(
        Habit.BATH, "Bath", s, modifier, onClick,
        onLongPress = if (d.bathAt == null) ({ logBath(a, s, view, scope) }) else null,
    ) {
        if (d.bathAt != null) {
            val parts = F.time(d.bathAt, s.zone).split(" ")
            BigValue(parts[0], parts.getOrElse(1) { "" })
        } else {
            Text("Not yet", style = T.big.copy(fontSize = 28.sp, color = C.dim))
        }
        Spacer(Modifier.height(10.dp))
        val run = bathRun(s)
        val usual = usualBath(s)
        Text(
            listOfNotNull(
                if (run > 1) "$run days in a row." else null,
                usual,
                if (d.bathAt == null) "Long-press to log." else null,
            ).joinToString(" "),
            style = T.small,
        )
    }
}

private fun bathRun(s: HomeState): Int {
    var n = 0
    var d = s.now.toLocalDate()
    if (s.today.bathAt == null) d = d.minusDays(1)
    while (s.days[d]?.bathAt != null) {
        n++
        d = d.minusDays(1)
    }
    return n
}

private fun usualBath(s: HomeState): String? {
    val minutes = s.days.values.mapNotNull { it.bathAt }.takeLast(30).map {
        val t = java.time.Instant.ofEpochMilli(it).atZone(s.zone)
        t.hour * 60 + t.minute
    }.sorted()
    if (minutes.size < 5) return null
    val lo = minutes[minutes.size / 4]
    val hi = minutes[minutes.size * 3 / 4]
    return "Usually between ${F.minutesOfDay(lo)} and ${F.minutesOfDay(hi)}."
}

@Composable
private fun WeekStrip(s: HomeState) {
    val today = s.now.toLocalDate()
    val start = today.minusDays((today.dayOfWeek.value % 7).toLong())
    Panel(Modifier.fillMaxWidth().testTag("week-strip")) {
        Caps("This week")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0..6) {
                val date = start.plusDays(i.toLong())
                val prog = if (date.isAfter(today)) emptyMap() else Habit.entries.associateWith { h ->
                    if (s.closedOn(date, h) == true) 1f else if (date == today) (s.progress[h] ?: 0f) else 0f
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Seal(prog, 34.dp, stroke = 14f, animate = false)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US),
                        style = T.small.copy(color = if (date == today) C.ink else C.mute, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}
