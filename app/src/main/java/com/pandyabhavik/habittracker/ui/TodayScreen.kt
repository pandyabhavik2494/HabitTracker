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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.tappableElement
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
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandyabhavik.habittracker.core.Coach
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.core.Rules
import com.pandyabhavik.habittracker.core.Streaks
import com.pandyabhavik.habittracker.data.HomeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    val ptr = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    // The box only hides its disc when isRefreshing flips back to false. Make sure it also goes away
    // when the sync ends (or never started), so an empty disc can't stay parked over the tiles.
    LaunchedEffect(pulling) { if (!pulling) ptr.animateToHidden() }
    PullToRefreshBox(
        isRefreshing = pulling,
        onRefresh = {
            a.vm.pullToRefresh()
            scope.launch {
                delay(400)
                a.vm.pulling.first { !it }
                ptr.animateToHidden()
            }
        },
        state = ptr,
        modifier = Modifier
            .fillMaxSize()
            .testTag("today"),
        indicator = {
            // A lighter disc with a clear cyan arc: it slides down with the pull, fills as the pull
            // nears the trigger point, and spins while the sync runs.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val f = if (pulling) 1f else ptr.distanceFraction.coerceIn(0f, 1.5f)
                        translationY = f * 76.dp.toPx() - 48.dp.toPx()
                        alpha = if (pulling) 1f else ptr.distanceFraction.coerceIn(0f, 1f)
                    }
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PullDisc)
                    .border(1.dp, C.bath.copy(alpha = 0.35f), CircleShape)
                    .testTag("pull-indicator"),
                contentAlignment = Alignment.Center,
            ) {
                if (pulling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = C.bath,
                        strokeWidth = 3.dp,
                        trackColor = C.bath.copy(alpha = 0.2f),
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { (ptr.distanceFraction * 0.85f).coerceIn(0.08f, 0.85f) },
                        modifier = Modifier.size(24.dp),
                        color = C.bath,
                        strokeWidth = 3.dp,
                        trackColor = C.bath.copy(alpha = 0.2f),
                    )
                }
            }
        },
    ) {
        if (a.wide) TodayWide(s) { sheet = it } else TodayCover(s) { sheet = it }
    }
    sheet?.let { HabitSheet(it, s) { sheet = null } }
}

/** The pull-to-refresh disc: lighter than the cards, so it reads against them. */
private val PullDisc = Color(0xFF2A3040)

// ---- Cover screen ---------------------------------------------------------------------------------

@Composable
private fun TodayCover(s: HomeState, onHabit: (Habit) -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
        ) {
            TopBar(s, showSettings = true)
            Box(
                Modifier
                    .fillMaxWidth()
                    // Room below for the Seal's glow (10% of its size past the ring's box), so the card
                    // underneath never cuts it flat.
                    .padding(top = 6.dp, bottom = 26.dp),
                contentAlignment = Alignment.Center,
            ) {
                BigSeal(s, 236.dp)
            }
            Cards(s, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            HabitRows(s, onHabit)
            SyncLine(s)
            Spacer(Modifier.height(bottomRoom()))
        }
        StatusScrim(scroll)
    }
}

/** Space below the end of a scroll: clear of the navigation bar or taskbar, plus 24dp. */
@Composable
private fun bottomRoom(): Dp = maxOf(
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding(),
) + 24.dp

/**
 * Fades the top of the scrolling Today into the background once it has scrolled, so the streak pills
 * slide away softly instead of being cut by the top edge.
 */
@Composable
private fun BoxScope.StatusScrim(scroll: ScrollState) {
    val h = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(h)
            .graphicsLayer { alpha = (scroll.value / 24.dp.toPx()).coerceIn(0f, 1f) }
            .background(Brush.verticalGradient(listOf(C.bg, C.bg.copy(alpha = 0f)))),
    )
}

// ---- Inner screen ---------------------------------------------------------------------------------

@Composable
private fun TodayWide(s: HomeState, onHabit: (Habit) -> Unit) {
    // One rule on wide screens: the Seal and its cards on the left, the tiles, this week and the sync
    // line on the right. Each pane scrolls on its own, so neither is ever cut off by the other.
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        TopBar(s, showSettings = false)
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BoxWithConstraints(
                Modifier
                    .weight(0.47f)
                    .fillMaxHeight(),
            ) {
                // Small enough that the Seal's glow (about 10% of its size past each side) stays inside
                // the pane, which clips because it scrolls.
                val seal = minOf(300.dp, maxWidth * 0.8f)
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(seal * 0.1f))
                    BigSeal(s, seal)
                    // The glow reaches 10% of the Seal's size below its box: keep the card clear of it.
                    Spacer(Modifier.height(seal * 0.12f))
                    Cards(s, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(bottomRoom()))
                }
            }
            Column(
                Modifier
                    .weight(0.53f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                // Tiles in a row share one height (the taller one's), so the rows stay aligned.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepsTile(s, Modifier.weight(1f).fillMaxHeight()) { onHabit(Habit.STEPS) }
                    MealsTile(s, Modifier.weight(1f).fillMaxHeight()) { onHabit(Habit.CALORIES) }
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WeighTile(s, Modifier.weight(1f).fillMaxHeight()) { onHabit(Habit.WEIGH) }
                    BathTile(s, Modifier.weight(1f).fillMaxHeight()) { onHabit(Habit.BATH) }
                }
                WeekStrip(s)
                SyncLine(s)
                Spacer(Modifier.height(bottomRoom() - 12.dp))
            }
        }
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
    Seal(s.progress, size, Modifier.testTag("seal"), pulse = pulse) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size * 0.62f)) {
            if (s.perfect) {
                // The Perfect Day number lives on the card below; the ring just says it's sealed.
                Icon(Icons.Rounded.Check, null, tint = C.ink, modifier = Modifier.size(size * 0.2f))
                Text("4/4", style = T.big)
                Text("SEALED", style = T.caps.copy(color = C.ink))
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
                    sealCaption(s, open),
                    style = T.small.copy(
                        color = if (open.size == 1) C.of(open.first()) else C.mute,
                        fontSize = 12.sp,
                        lineBreak = LineBreak.Heading,
                    ),
                    textAlign = TextAlign.Center,
                    // Always two lines tall, so the numeral above doesn't jump when the caption wraps.
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The line inside the Seal: short, plain English. With one quarter left, "Perfect Day" is held
 * together so "Day" never ends up alone on the second line.
 */
private fun sealCaption(s: HomeState, open: List<Habit>): String {
    val pd = "Perfect${F.NBSP}Day"
    if (open.size != 1) return s.sealLine
    return when (open.first()) {
        Habit.STEPS -> "${F.n((s.goals.steps - s.today.steps).coerceAtLeast(0))} steps to a $pd"
        Habit.CALORIES -> "Log your meals for a $pd"
        Habit.WEIGH -> "A weigh-in away from a $pd"
        Habit.BATH -> "A bath away from a $pd"
    }
}

/** The context cards: the pack, comeback, saved streak, risk, pledge, a pattern, the best next move. */
@Composable
fun Cards(s: HomeState, modifier: Modifier) {
    val a = LocalActions.current
    val hour = s.now.hour
    val showPledge = s.today.pledgedAt == null && hour < 12 && !s.perfect
    val pledged = s.today.pledgedAt != null && hour < 12 && !s.perfect
    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        s.unopened.firstOrNull()?.let { PackWaiting(s, it.id) }
        if (s.perfect) PerfectCard(s)
        if (s.comebackFrom != null && !s.perfect) ComebackCard(s)
        if (s.savedYesterday && hour < 14) SavedCard(s)
        if (s.atRisk) RiskCard(s)
        if (showPledge) PledgeCard(s)
        if (pledged) PledgedNote()
        val p = s.pattern
        if (p != null && hour < 14 && !s.atRisk) PatternCard(s, p.habit, p.weekday, p.misses, p.of)
        val move = s.move
        if (!s.atRisk && !showPledge && move != null) {
            Panel(Modifier.fillMaxWidth().testTag("next-move")) {
                Caps("Best next move", icon = Icons.Rounded.Bolt)
                Spacer(Modifier.height(6.dp))
                Text(move.title.keepLast(), style = T.h2)
                move.detail?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it.keepLast(), style = T.body)
                }
            }
        }
        if (s.settings.lastSync == null && s.settings.lastSyncError != null) {
            Panel(Modifier.fillMaxWidth().clickable { a.askHealth() }.testTag("connect-health"), borderColor = C.bath.copy(alpha = 0.4f)) {
                Caps("Connect Health Connect", color = C.bath, icon = Icons.Rounded.Sync)
                Spacer(Modifier.height(6.dp))
                Text("Steps and weight sync from Health Connect. Tap to allow access.".keepLast(), style = T.body)
            }
        }
    }
}

@Composable
private fun PackWaiting(s: HomeState, id: Long) {
    val a = LocalActions.current
    // Read only inside graphicsLayer, so the frame clock redraws the layer without recomposing.
    val clock = rememberClock()
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
                    .graphicsLayer {
                        val t = clock.value
                        rotationZ = kotlin.math.sin(t * 6f) * if ((t % 2.4f) < 0.6f) 4f else 0f
                    }
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
                Text("All four closed. ${s.streak}-day streak.".keepLast(), style = T.h2)
                Text("Come back tomorrow for #${s.dayNumber + 1}. The Seal stays lit tonight.".keepLast(), style = T.body)
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
    // Read only inside graphicsLayer, so the pulse never recomposes the card.
    val pulse = rememberClock()
    Panel(
        Modifier.fillMaxWidth().testTag("risk-card"),
        borderColor = C.risk.copy(alpha = 0.45f),
        background = Brush.radialGradient(listOf(Color(0x33FF5A6A), C.s1), radius = 900f),
    ) {
        Caps("${s.dayNumber - 1}-day streak at risk", color = C.risk, icon = Icons.Rounded.Timer)
        Text(
            countdown,
            style = T.big.copy(color = C.risk),
            modifier = Modifier
                .graphicsLayer { alpha = 0.65f + 0.35f * (0.5f + 0.5f * kotlin.math.cos(pulse.value * Math.PI.toFloat())) }
                .testTag("countdown"),
        )
        Spacer(Modifier.height(4.dp))
        Text((s.move?.title ?: "${open.size} quarters still open.").keepLast(), style = T.h2)
        Text("Close it for Perfect Day #${s.dayNumber} and a sticker pack.".keepLast(), style = T.body)
        // The gap above the button only when there is one, so the card's top and bottom padding match.
        when (open.firstOrNull()) {
            Habit.BATH -> if (open.size == 1) {
                Spacer(Modifier.height(12.dp))
                HoldButton("Hold to log bath", haptics = s.settings.haptics) { a.vm.logBath() }
            }
            Habit.CALORIES -> if (open.size == 1) {
                Spacer(Modifier.height(12.dp))
                MealsYes(s, Modifier.fillMaxWidth().height(50.dp).testTag("risk-meals-yes"), "Yes, I logged my meals")
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
    Spacer(Modifier.height(12.dp))
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
            (if (s.pledgesKept > 0) "You've kept ${s.pledgesKept} pledge${if (s.pledgesKept == 1) "" else "s"} in a row." else "Say it with your thumb. It makes it far more likely.").keepLast(),
            style = T.body,
        )
        Spacer(Modifier.height(10.dp))
        HoldButton("Hold to pledge", haptics = s.settings.haptics) { a.vm.pledge() }
    }
}

/** What the pledge card turns into once held: a small, quiet confirmation for the morning. */
@Composable
private fun PledgedNote() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.weigh.copy(alpha = 0.12f))
            .border(1.dp, C.weigh.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("pledged-note"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckDot(C.weigh, 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = C.ink)) { append("Pledged.") }
                append(" Close all four today.")
            },
            style = T.small.copy(fontSize = 13.sp),
        )
    }
}

@Composable
private fun ComebackCard(s: HomeState) {
    Panel(
        Modifier.fillMaxWidth().testTag("comeback-card"),
        borderColor = C.risk.copy(alpha = 0.35f),
        background = Brush.radialGradient(listOf(Color(0x26FF5A6A), C.s1), radius = 900f),
    ) {
        Caps("Fresh start", color = C.risk, icon = Icons.Rounded.History)
        Spacer(Modifier.height(6.dp))
        Text("Comeback: 3 Perfect Days in a row", style = T.h2)
        Text("Pull it off and Maneki the Lucky Cat is yours, guaranteed. Comebacks are the only way to get her.".keepLast(), style = T.body)
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
                            if (i < done) Modifier.background(Brush.linearGradient(listOf(C.bath, C.weigh, C.swap)))
                            else Modifier.border(1.5.dp, C.dim, CircleShape),
                        ),
                )
            }
        }
        if (s.comebackRecord > 0) {
            Spacer(Modifier.height(10.dp))
            Text("Your record: ${s.comebackRecord} days. You've done it before.".keepLast(), style = T.small.copy(color = C.ink))
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
            "${Streaks.SWAPS_PER_SAVE} swaps saved your ${s.streak}-day streak.",
            style = T.h2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${s.swaps} swap${if (s.swaps == 1) "" else "s"} left. Every duplicate you pull adds one.".keepLast(),
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
        Habit.CALORIES -> "meals"
        Habit.WEIGH -> "weigh-in"
        Habit.BATH -> "bath"
    }
    Panel(Modifier.fillMaxWidth().testTag("pattern-card")) {
        Caps("Pattern spotted", icon = Icons.Rounded.Insights)
        Spacer(Modifier.height(6.dp))
        Text("${name}s are your $what trap.", style = T.h2)
        val count = if (misses >= of) "All" else "$misses"
        Text("$count of your last $of ${name}s missed it. Want a 5${F.NBSP}PM heads-up on ${name}s?".keepLast(), style = T.body)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(C.s2)
                    .clickable {
                        a.vm.warnMe(day, habit)
                        a.showMessage("We'll nudge you ${name}s at 5${F.NBSP}PM.")
                    }
                    .testTag("warn-me"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Warn me",
                    style = T.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
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
    // Open quarters first: the list always starts with what still needs doing.
    val order = Habit.entries.sortedBy { if (it in s.closed) 1 else 0 }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        order.forEach { h ->
            val closed = h in s.closed
            val d = s.today
            when (h) {
                Habit.STEPS -> HabitRow(
                    h, F.habitName(h, s),
                    if (closed) "Closed · ${F.n(d.steps)} steps" else "${F.n(s.goals.steps - d.steps)} to go · ≈${Rules.walkMinutes(s.goals.steps - d.steps)} min walk".keepLast(),
                    F.n(d.steps), d.steps.toFloat() / s.goals.steps, closed,
                    onClick = { onHabit(h) },
                )
                Habit.CALORIES -> HabitRow(
                    h, F.habitName(h, s),
                    d.mealsLoggedAt?.let { "Logged ${F.time(it, s.zone)}" } ?: "Did you log them today?".keepLast(),
                    null, 0f, closed,
                    trailing = if (closed) null else ({ MealsYes(s, Modifier.height(36.dp).testTag("meals-quick"), "Yes") }),
                    onClick = { onHabit(h) },
                )
                Habit.WEIGH -> HabitRow(
                    h, "Weigh-in",
                    d.weightKg?.let { kg -> listOfNotNull(F.weight(kg, s.settings.pounds), F.weightTrend(s, kg)).joinToString(" · ") }
                        ?: "Waiting for your scale".keepLast(),
                    if (closed) null else "Now", 0f, closed,
                    valueColor = C.weigh,
                    trailing = if (closed) null else ({ Text("Now", style = T.label.copy(color = C.weigh)) }),
                    onClick = { onHabit(h) },
                )
                Habit.BATH -> HabitRow(
                    h, "Bath",
                    d.bathAt?.let { "Logged ${F.time(it, s.zone)}" } ?: "Long-press to log".keepLast(),
                    null, 0f, closed,
                    trailing = if (closed) null else ({ Icon(Icons.Rounded.TouchApp, null, tint = C.bath) }),
                    modifier = Modifier.semantics { onLongClick("Log bath") { logBath(a, s, view); true } },
                    onClick = { onHabit(h) },
                    onLongPress = if (closed) null else ({ logBath(a, s, view) }),
                )
            }
        }
    }
}

private fun logBath(a: AppActions, s: HomeState, view: android.view.View) {
    if (s.today.bathAt != null) return
    if (s.settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    // The bath that closes the last quarter makes the Perfect Day and its pack; that can't be undone.
    val makesPerfect = Habit.entries.all { it == Habit.BATH || it in s.closed }
    a.vm.logBath()
    if (makesPerfect) a.showMessage("Bath logged")
    else a.showMessage("Bath logged", actionLabel = "Undo") { a.vm.undoBath() }
}

@Composable
fun SyncLine(s: HomeState) {
    val a = LocalActions.current
    val syncing by a.vm.syncing.collectAsStateWithLifecycle()
    // The same rule Settings uses (no access, Health Connect missing, or the last sync errored), so the
    // two screens never disagree. "Synced X ago" only shows when the last sync went through.
    val failure by a.vm.syncFailure.collectAsStateWithLifecycle()
    val now = s.now.atZone(s.zone).toInstant().toEpochMilli()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .testTag("sync-line"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val error = failure?.takeIf { !syncing }
        Icon(Icons.Rounded.Sync, null, tint = if (error != null) C.risk else C.dim, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            when {
                syncing -> "Syncing with Health Connect…"
                // "Sync failed: no Health Connect access": short, with the last two words kept together.
                error != null -> error.removePrefix("Last ").replaceFirstChar { it.uppercase() }.keepLast()
                else -> "${F.ago(s.settings.lastSync, now)} · pull down to sync".keepLast()
            },
            style = T.small.copy(color = if (error != null) C.risk else C.dim),
            modifier = Modifier.weight(1f, fill = false),
        )
        if (error != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                "Open settings",
                style = T.small.copy(color = C.ink, fontWeight = androidx.compose.ui.text.font.FontWeight(700)),
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(C.s2)
                    .clickable { a.askHealth() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("sync-fix"),
            )
        }
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
private fun BigValue(value: String, unit: String, color: Color = C.ink) {
    Text(
        buildAnnotatedString {
            append(value)
            withStyle(SpanStyle(color = C.dim, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(600))) { append(" $unit") }
        },
        style = T.big.copy(fontSize = 28.sp, color = color),
        maxLines = 1,
    )
}

/** One grey line under a tile's big value: what an empty value means, or when the value is from. */
@Composable
private fun ValueHint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Every tile's empty state looks the same: a dim "—" where the value goes, and a one-line hint. */
@Composable
private fun EmptyValue(unit: String, hint: String) {
    BigValue("—", unit, color = C.dim)
    ValueHint(hint)
}

@Composable
private fun StepsTile(s: HomeState, modifier: Modifier, onClick: () -> Unit) {
    val d = s.today
    Tile(Habit.STEPS, "Steps", s, modifier, onClick) {
        if (d.steps > 0) BigValue(F.n(d.steps), "/ ${F.compact(s.goals.steps)}")
        else EmptyValue("/ ${F.compact(s.goals.steps)}", "No steps synced yet.")
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

/** The meals quarter is a manual yes/no: "Did you log your meals today?" */
@Composable
private fun MealsTile(s: HomeState, modifier: Modifier, onClick: () -> Unit) {
    val d = s.today
    Tile(Habit.CALORIES, "Meals", s, modifier, onClick) {
        val at = d.mealsLoggedAt
        if (at != null) {
            val parts = F.time(at, s.zone).split(F.NBSP)
            BigValue(parts[0], parts.getOrElse(1) { "" })
            ValueHint("Meals logged today.")
        } else {
            Text("Did you log your meals today?".keepLast(), style = T.h2)
            Spacer(Modifier.height(10.dp))
            MealsYes(s, Modifier.fillMaxWidth().height(44.dp).testTag("tile-meals-yes"), "Yes, all logged")
        }
        Spacer(Modifier.height(8.dp))
        val run = mealsRun(s)
        if (run > 1) Text("$run days in a row.", style = T.small)
    }
}

/** Days in a row, up to today, with meals marked logged. Today counts only once it's logged. */
private fun mealsRun(s: HomeState): Int {
    var n = 0
    var d = s.now.toLocalDate()
    if (s.days[d]?.mealsLoggedAt == null) d = d.minusDays(1)
    while (s.days[d]?.mealsLoggedAt != null) {
        n++
        d = d.minusDays(1)
    }
    return n
}

/** The one-tap "yes, I logged my meals" button, with Undo unless it made the Perfect Day. */
@Composable
private fun MealsYes(s: HomeState, modifier: Modifier, label: String) {
    val a = LocalActions.current
    val view = LocalView.current
    Box(
        modifier
            .clip(CircleShape)
            .background(C.calories)
            .clickable { logMeals(a, s, view) }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = T.label.copy(color = Color(0xFF1E0E02)), maxLines = 1)
    }
}

private fun logMeals(a: AppActions, s: HomeState, view: android.view.View) {
    if (s.today.mealsLoggedAt != null) return
    if (s.settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    // The check-in that closes the last quarter makes the Perfect Day and its pack; that can't be undone.
    val makesPerfect = Habit.entries.all { it == Habit.CALORIES || it in s.closed }
    a.vm.logMeals()
    if (makesPerfect) a.showMessage("Meals logged")
    else a.showMessage("Meals logged", actionLabel = "Undo") { a.vm.unlogMeals() }
}

@Composable
private fun WeighTile(s: HomeState, modifier: Modifier, onClick: () -> Unit) {
    val d = s.today
    val history = s.days.entries.sortedBy { it.key }.mapNotNull { it.value.weightKg }.takeLast(30)
    Tile(Habit.WEIGH, "Weigh-in", s, modifier, onClick) {
        val todayDate = s.now.toLocalDate()
        // Before today's weigh-in, the last one shows dimmed and dated so it doesn't read as today's.
        val last = if (d.weightKg != null) null else s.days.entries
            .filter { it.key.isBefore(todayDate) && it.value.weightKg != null }
            .maxByOrNull { it.key }
        val latest = d.weightKg ?: last?.value?.weightKg
        if (latest != null) {
            val v = String.format(Locale.US, "%.1f", F.weightValue(latest, s.settings.pounds))
            BigValue(v, F.unit(s.settings.pounds), color = if (d.weightKg != null) C.ink else C.dim)
            if (d.weightKg == null && last != null) {
                ValueHint(
                    "Last: ${F.dayLabel(last.key, todayDate).let { if (it == "Yesterday") "yesterday" else it }}",
                    modifier = Modifier.testTag("weigh-last"),
                )
            }
        } else {
            EmptyValue(F.unit(s.settings.pounds), "Step on the scale.")
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
            Text(if (d.weighedAt == null) "Not yet today." else "Weighed ${F.time(d.weighedAt!!, s.zone)}.", style = T.small)
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
    val d = s.today
    Tile(
        Habit.BATH, "Bath", s, modifier, onClick,
        onLongPress = if (d.bathAt == null) ({ logBath(a, s, view) }) else null,
    ) {
        if (d.bathAt != null) {
            val parts = F.time(d.bathAt!!, s.zone).split(F.NBSP)
            BigValue(parts[0], parts.getOrElse(1) { "" })
        } else {
            EmptyValue("", "Not yet today.")
        }
        Spacer(Modifier.height(8.dp))
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
    return "Usually ${F.minutesRange(lo, hi)}."
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
                val day = s.days[date]
                // Saved with swaps: a dashed pink ring, as on Journey (it counts, but it wasn't closed).
                // Days still to come: a faint thin ring, so they never read as misses.
                val saved = day?.savedWithSwaps == true && day?.perfectAt == null && date != today
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        date.isAfter(today) -> WeekRing(C.line, dashed = false, Modifier.testTag("week-future-$date"))
                        saved -> WeekRing(C.swap, dashed = true, Modifier.testTag("week-saved-$date"))
                        else -> {
                            // Today shows its progress so far; past days what closed.
                            val prog = Habit.entries.associateWith { h ->
                                if (s.closedOn(date, h) == true) 1f else if (date == today) (s.progress[h] ?: 0f) else 0f
                            }
                            Seal(prog, 34.dp, stroke = 14f, animate = false)
                        }
                    }
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

/** A plain ring the size of the week strip's Seals: dashed for a day saved with swaps, faint for days to come. */
@Composable
private fun WeekRing(color: Color, dashed: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(34.dp)) {
        val w = if (dashed) 3.dp.toPx() else 1.5.dp.toPx()
        // Same radius as the Seal's ring (41 of 100).
        val r = size.minDimension * 0.41f
        drawCircle(
            color,
            radius = r,
            style = Stroke(
                width = w,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            ),
        )
    }
}
