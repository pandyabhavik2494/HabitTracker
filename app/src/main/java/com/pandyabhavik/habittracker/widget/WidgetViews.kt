package com.pandyabhavik.habittracker.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pandyabhavik.habittracker.MainActivity
import com.pandyabhavik.habittracker.R
import com.pandyabhavik.habittracker.art.SealArt
import com.pandyabhavik.habittracker.core.CalorieState
import com.pandyabhavik.habittracker.core.Coach
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.core.Rules
import com.pandyabhavik.habittracker.data.HomeState
import com.pandyabhavik.habittracker.ui.C
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// The widget layouts. Each widget is drawn at its real size (SizeMode.Exact) and scaled by [k], the
// ratio of that size to the mock's design size, so text and rings grow with the widget instead of
// floating in a big box. Rings are bitmaps; bars and cards are plain layout boxes.

// ------------------------------------------------------------------------------------------ helpers

private val TRACK = Color(0xFF1D212C)
private val ON_BATH = Color(0xFF04141A)

/** The widget's size relative to its design size, clamped so text stays readable and inside the card. */
@Composable
internal fun scale(designW: Float, designH: Float): Float {
    val dims = LocalSize.current
    if (dims.width.value <= 0f || dims.height.value <= 0f) return 1f
    return minOf(dims.width.value / designW, dims.height.value / designH).coerceIn(0.7f, 1.6f)
}

private fun ts(color: Color, size: Float, weight: FontWeight = FontWeight.Normal, align: TextAlign = TextAlign.Start) =
    TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = weight, textAlign = align)

internal fun Habit.iconRes(): Int = when (this) {
    Habit.STEPS -> R.drawable.ic_w_steps
    Habit.CALORIES -> R.drawable.ic_w_food
    Habit.WEIGH -> R.drawable.ic_w_weight
    Habit.BATH -> R.drawable.ic_w_bath
}

/** Opens the app on Today, like Notifier.openApp(context, "today"). */
internal fun openToday(context: Context): Action = androidx.glance.appwidget.action.actionStartActivity(
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(MainActivity.EXTRA_TAB, "today"),
)

internal fun openBath(): Action = actionStartActivity<BathHoldActivity>()

@Composable
private fun Icon(res: Int, color: Color, dim: Dp) {
    Image(
        provider = ImageProvider(res),
        contentDescription = null,
        modifier = GlanceModifier.size(dim),
        colorFilter = ColorFilter.tint(ColorProvider(color)),
    )
}

/**
 * The dark rounded card: a 1dp border (line colour, or red when the streak is at risk) around the
 * #0D0F14 surface. Corner radius needs Android 12+; older versions show square corners.
 */
@Composable
private fun Card(
    onClick: Action,
    risk: Boolean = false,
    glow: Bitmap? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(if (risk) C.risk.copy(alpha = 0.5f) else C.line)
            .cornerRadius(26.dp)
            .padding(1.dp)
            .clickable(onClick),
    ) {
        Box(modifier = GlanceModifier.fillMaxSize().background(C.s1).cornerRadius(25.dp)) {
            if (glow != null) {
                Image(
                    provider = ImageProvider(glow),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }
            content()
        }
    }
}

/** Flame and streak; after 8 PM with something open, the countdown to midnight in red. */
@Composable
private fun FlameChip(s: HomeState, textSp: Float, countdown: Boolean = true) {
    val color = if (s.atRisk) C.risk else C.flame
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(R.drawable.ic_w_flame, color, (textSp + 1f).dp)
        Spacer(GlanceModifier.width(2.dp))
        Text(
            if (s.atRisk && countdown) countdown(s) else s.streak.toString(),
            style = ts(color, textSp, FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/**
 * Widgets redraw every 15 minutes at best, so a ticking h:mm:ss would look precise and be wrong.
 * Hours and minutes, rounded up, stay honest between redraws.
 */
internal fun countdown(s: HomeState): String {
    val left = Coach.untilMidnight(s.now, s.zone)
    val minutes = ((left.seconds + 59) / 60).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun calorieOver(s: HomeState) = s.calorieState == CalorieState.OVER

/**
 * Bar fill for a habit: its progress. Calories are different: s.progress is a time-of-day fill until the
 * kitchen locks (right for the Seal), but next to "710 left" a bar reads as a kcal meter, so here it shows
 * kcal eaten against the limit; full once the kitchen is closed, and full (red) when over.
 */
private fun barValue(s: HomeState, h: Habit): Float = when {
    h != Habit.CALORIES -> (s.progress[h] ?: 0f).coerceIn(0f, 1f)
    s.calorieState == CalorieState.OVER || s.calorieState == CalorieState.CLOSED -> 1f
    s.goals.calories <= 0 -> 0f
    else -> (s.today.calories.toFloat() / s.goals.calories).coerceIn(0f, 1f)
}

private fun barColor(s: HomeState, h: Habit): Color = if (h == Habit.CALORIES && calorieOver(s)) C.risk else C.of(h)

/**
 * The value at the end of a habit row: what's left while open, "done" once closed, for every habit alike
 * (totals live in the app). Calories over the limit say by how much, in red.
 */
private fun rowValue(s: HomeState, h: Habit): String = when (h) {
    Habit.STEPS -> if (h in s.closed) "done"
    else "${Coach.n((s.goals.steps - s.today.steps).coerceAtLeast(0))} left"
    Habit.CALORIES -> when (s.calorieState) {
        CalorieState.NOT_LOGGED -> "open"
        CalorieState.UNDER -> "${Coach.n((s.goals.calories - s.today.calories).coerceAtLeast(0))} left"
        CalorieState.CLOSED -> "done"
        CalorieState.OVER -> "${Coach.n((s.today.calories - s.goals.calories).coerceAtLeast(0))} over"
    }
    Habit.WEIGH, Habit.BATH -> if (h in s.closed) "done" else "open"
}

/** A rounded bar. [trackDp] is the expected track width, used for the fill; the track itself takes [modifier]. */
@Composable
private fun Bar(modifier: GlanceModifier, trackDp: Float, value: Float, color: Color, thick: Dp) {
    // A nested fixed-width Box doesn't reliably size inside a weighted one in RemoteViews (the fill came out
    // full width), so the bar is a small bitmap stretched to the track.
    Image(
        provider = ImageProvider(Art.bar(color, TRACK, value, trackDp, thick.value)),
        contentDescription = null,
        modifier = modifier.height(thick),
        contentScale = ContentScale.FillBounds,
    )
}

/** Icon, bar and (optionally) a right-aligned value of [valueDp] width. */
@Composable
private fun HabitRow(s: HomeState, h: Habit, k: Float, rowDp: Float, valueDp: Float?) {
    val icon = 16f * k
    val gap = 8f * k
    val track = rowDp - icon - gap - (if (valueDp != null) gap + valueDp else 0f)
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(h.iconRes(), C.of(h), icon.dp)
        Spacer(GlanceModifier.width(gap.dp))
        Bar(GlanceModifier.defaultWeight(), track, barValue(s, h), barColor(s, h), (6f * k).dp)
        if (valueDp != null) {
            Spacer(GlanceModifier.width(gap.dp))
            val closed = h in s.closed
            Text(
                rowValue(s, h),
                modifier = GlanceModifier.width(valueDp.dp),
                style = ts(
                    when {
                        h == Habit.CALORIES && calorieOver(s) -> C.risk
                        closed -> C.ink
                        else -> C.mute
                    },
                    12.5f * k, FontWeight.Medium, TextAlign.End,
                ),
                maxLines = 1,
            )
        }
    }
}

/** The Seal at [sizeDp] with [center] on top. */
@Composable
private fun SealBlock(s: HomeState, sizeDp: Float, modifier: GlanceModifier = GlanceModifier.size(sizeDp.dp), center: @Composable () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(Art.seal(s, sizeDp)),
            contentDescription = if (s.perfect) "Perfect Day, all four closed" else "${s.closedCount} of 4 closed",
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.fillMaxSize(),
        )
        center()
    }
}

@Composable
private fun SealCenter(s: HomeState, k: Float, numberSp: Float, flame: Boolean, caption: String? = null) {
    if (s.perfect) {
        Icon(R.drawable.ic_w_check, C.ink, (numberSp * 1.2f).dp)
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${s.closedCount}/4", style = ts(C.ink, numberSp, FontWeight.Bold, TextAlign.Center), maxLines = 1)
        if (caption != null) {
            Text(caption, style = ts(C.mute, 12f * k, FontWeight.Medium, TextAlign.Center), maxLines = 1)
        }
        if (flame) {
            Spacer(GlanceModifier.height((1f * k).dp))
            // At risk, the countdown alone (no flame icon) in a smaller size, so "2h 42m" sits well inside
            // the ring's hole instead of touching the arcs.
            if (s.atRisk) {
                Text(countdown(s), style = ts(C.risk, 9.5f * k, FontWeight.Bold, TextAlign.Center), maxLines = 1)
            } else {
                FlameChip(s, 11f * k)
            }
        }
    }
}

/** Shown before the first sync, or if the data can't be read. */
@Composable
internal fun EmptyWidget(context: Context) {
    Card(openToday(context)) {
        Box(modifier = GlanceModifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            Text(
                "Open Habit Tracker",
                style = ts(C.mute, 14f, FontWeight.Medium, TextAlign.Center),
                maxLines = 2,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------ 1. Seal (2x2)

@Composable
internal fun SealView(context: Context, s: HomeState) {
    val dims = LocalSize.current
    val k = scale(150f, 150f)
    val ring = (minOf(dims.width.value, dims.height.value) - 10f).coerceAtLeast(60f)
    Card(openToday(context), risk = s.atRisk) {
        Box(modifier = GlanceModifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
            SealBlock(s, ring, GlanceModifier.fillMaxSize()) {
                SealCenter(s, k, 19f * k, flame = true)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ 2. List (2x2)

@Composable
internal fun ListView(context: Context, s: HomeState) {
    val dims = LocalSize.current
    val k = scale(150f, 150f)
    val pad = 14f * k
    val rowDp = dims.width.value - 2f - 2 * pad
    Card(openToday(context), risk = s.atRisk) {
        Column(modifier = GlanceModifier.fillMaxSize().padding(pad.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Today", modifier = GlanceModifier.defaultWeight(), style = ts(C.ink, 15f * k, FontWeight.Bold), maxLines = 1)
                // The streak, not the countdown: "2h 42m" would squeeze "Today" (the red border says at risk).
                FlameChip(s, 13.5f * k, countdown = false)
            }
            for (h in Habit.entries) {
                Spacer(GlanceModifier.defaultWeight())
                HabitRow(s, h, k, rowDp, valueDp = null)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ 3. Streak (2x2)

@Composable
internal fun StreakView(context: Context, s: HomeState) {
    val k = scale(150f, 150f)
    val color = if (s.atRisk) C.risk else C.flame
    val caption = when {
        s.atRisk -> "ends in ${countdown(s)}"
        s.perfect -> "${if (s.streak == 1) "day" else "days"} · best ${s.best}"
        else -> "day streak"
    }
    Card(openToday(context), risk = s.atRisk, glow = Art.glow(color, if (s.atRisk) 0.22f else 0.16f)) {
        Column(modifier = GlanceModifier.fillMaxSize().padding((14f * k).dp)) {
            Icon(R.drawable.ic_w_flame, color, (20f * k).dp)
            Spacer(GlanceModifier.defaultWeight())
            Text(s.streak.toString(), style = ts(color, 40f * k, FontWeight.Bold), maxLines = 1)
            Text(caption, style = ts(if (s.atRisk) C.risk else C.mute, 13f * k, FontWeight.Medium), maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------------------------------ 4. Bath (2x2)

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

internal fun bathTime(s: HomeState): String? =
    s.today.bathAt?.let { Rules.localTime(it, s.zone).format(TIME) }

@Composable
internal fun BathView(s: HomeState) {
    val k = scale(150f, 150f)
    val time = bathTime(s)
    Card(openBath()) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (time != null) {
                // The bitmap leaves room around the 52dp circle for its glow.
                val box = 76f * k
                Box(modifier = GlanceModifier.size(box.dp), contentAlignment = Alignment.Center) {
                    Image(
                        provider = ImageProvider(Art.doneDot(C.bath, box)),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxSize(),
                    )
                    Icon(R.drawable.ic_w_check, ON_BATH, (28f * k).dp)
                }
                Spacer(GlanceModifier.height((4f * k).dp))
                Text("Bath · $time", style = ts(C.mute, 13.5f * k, FontWeight.Medium, TextAlign.Center), maxLines = 1)
            } else {
                val box = 66f * k
                Box(modifier = GlanceModifier.size(box.dp), contentAlignment = Alignment.Center) {
                    Image(
                        // Just the faint track: any arc here read as progress on a habit that's yes/no.
                        provider = ImageProvider(Art.ring(C.bath, 0f, box)),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxSize(),
                    )
                    Icon(R.drawable.ic_w_bath, C.bath, (28f * k).dp)
                }
                Spacer(GlanceModifier.height((10f * k).dp))
                Text("Hold to log bath", style = ts(C.mute, 13.5f * k, FontWeight.Medium, TextAlign.Center), maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ 5. Strip (4x1)

@Composable
internal fun StripView(context: Context, s: HomeState) {
    val k = scale(328f, 72f)
    val dot = 38f * k
    Card(openToday(context), risk = s.atRisk) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = (16f * k).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Habit.entries.forEachIndexed { i, h ->
                if (i > 0) Spacer(GlanceModifier.width((12f * k).dp))
                val closed = h in s.closed
                val color = barColor(s, h)
                Box(modifier = GlanceModifier.size(dot.dp), contentAlignment = Alignment.Center) {
                    Image(
                        provider = ImageProvider(if (closed) Art.doneRing(color, dot) else Art.ring(color, s.progress[h] ?: 0f, dot)),
                        contentDescription = "${h.name.lowercase()} ${if (closed) "closed" else "open"}",
                        modifier = GlanceModifier.fillMaxSize(),
                    )
                    Icon(h.iconRes(), color, (16f * k).dp)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            FlameChip(s, 17f * k)
        }
    }
}

// ------------------------------------------------------------------------------------------ 6. Wide (4x2)

@Composable
internal fun WideView(context: Context, s: HomeState) {
    val dims = LocalSize.current
    val k = scale(328f, 150f)
    val padH = 16f * k
    // Tight vertically: four rows and the footer have to fit in 150dp at the cover screen's density and
    // font scale too (14dp clipped the footer's descenders there).
    val padV = 8f * k
    val seal = minOf(dims.height.value - 2f - 2 * padV, 108f * k).coerceAtLeast(56f)
    val gap = 14f * k
    val rowDp = dims.width.value - 2f - 2 * padH - seal - gap
    val footer = when {
        s.atRisk -> "Streak ends in ${countdown(s)}"
        s.perfect -> "Perfect Day #${s.dayNumber} · ${s.streak} ${if (s.streak == 1) "day" else "days"}"
        s.streak > 0 -> "${s.streak}-day streak"
        else -> "Perfect Day #${s.dayNumber} is up for grabs"
    }
    val footerColor = when {
        s.atRisk -> C.risk
        s.perfect -> C.flame
        else -> C.mute
    }
    Card(openToday(context), risk = s.atRisk) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = padH.dp, vertical = padV.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SealBlock(s, seal) { SealCenter(s, k, 16f * k, flame = false) }
            Spacer(GlanceModifier.width(gap.dp))
            Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Habit.entries.forEachIndexed { i, h ->
                    if (i > 0) Spacer(GlanceModifier.height((4f * k).dp))
                    HabitRow(s, h, k, rowDp, valueDp = 80f * k)
                }
                Spacer(GlanceModifier.height((5f * k).dp))
                Text(footer, style = ts(footerColor, 12.5f * k, FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ 7. Large (4x4)

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

@Composable
private fun Pill(icon: Int, text: String, color: Color, k: Float) {
    Row(
        modifier = GlanceModifier.background(color.copy(alpha = 0.14f)).cornerRadius((14f * k).dp)
            .padding(horizontal = (9f * k).dp, vertical = (4f * k).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, color, (15f * k).dp)
        Spacer(GlanceModifier.width((4f * k).dp))
        Text(text, style = ts(color, 14f * k, FontWeight.Bold), maxLines = 1)
    }
}

/**
 * Breaks [text] into two lines of about equal length when it likely won't fit on one line of [widthDp] at
 * [sp] (scaled by the user's font size), so a wrap never leaves one word (like "2,820.") alone on line two.
 * RemoteViews has no balanced line breaking, so this estimates with an average glyph width.
 */
internal fun balanced(context: Context, text: String, widthDp: Float, sp: Float): String {
    if ('\n' in text) return text
    val glyph = sp * context.resources.configuration.fontScale * 0.55f
    val fits = if (glyph > 0f) (widthDp / glyph).toInt() else Int.MAX_VALUE
    if (text.length <= fits) return text
    val mid = text.length / 2
    val at = text.indices.filter { text[it] == ' ' }.minByOrNull { kotlin.math.abs(it - mid) } ?: return text
    return text.substring(0, at) + "\n" + text.substring(at + 1)
}

@Composable
internal fun LargeView(context: Context, s: HomeState) {
    val dims = LocalSize.current
    val k = scale(328f, 328f)
    val pad = 18f * k
    val inner = dims.width.value - 2f - 2 * pad
    val seal = minOf(112f * k, inner * 0.42f)
    val gap = 14f * k
    val rowDp = inner - seal - gap
    val move = s.move
    val (cardTitle, cardBody) = when {
        s.atRisk -> "STREAK ENDS IN ${countdown(s)}" to (move?.title ?: s.sealLine)
        s.perfect -> "ALL FOUR CLOSED" to "${s.streak}-day streak · best ${s.best}"
        move != null -> "BEST NEXT MOVE" to move.title
        else -> "TODAY" to s.sealLine
    }
    val cardColor = if (s.atRisk) C.risk else C.mute
    Card(openToday(context), risk = s.atRisk) {
        Column(modifier = GlanceModifier.fillMaxSize().padding(pad.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(s.now.format(DATE), style = ts(C.mute, 13f * k, FontWeight.Medium), maxLines = 1)
                    Text("Habit Tracker", style = ts(C.ink, 17f * k, FontWeight.Bold), maxLines = 1)
                }
                Pill(R.drawable.ic_w_flame, s.streak.toString(), if (s.atRisk) C.risk else C.flame, k)
                Spacer(GlanceModifier.width((6f * k).dp))
                Pill(R.drawable.ic_w_spark, s.swaps.toString(), C.swap, k)
            }
            Spacer(GlanceModifier.height((6f * k).dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SealBlock(s, seal) { SealCenter(s, k, 18f * k, flame = false) }
                Spacer(GlanceModifier.width(gap.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Habit.entries.forEachIndexed { i, h ->
                        if (i > 0) Spacer(GlanceModifier.height((7f * k).dp))
                        HabitRow(s, h, k, rowDp, valueDp = 80f * k)
                    }
                }
            }
            Spacer(GlanceModifier.height((6f * k).dp))
            // Shorter than the app's line so it fits one line at the bigger widget text.
            Text(
                s.sealLine.replace(" away from a Perfect Day", " to a Perfect Day"),
                style = ts(if (s.perfect) C.flame else C.ink, 14.5f * k, FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height((6f * k).dp))
            Spacer(GlanceModifier.defaultWeight())
            Column(
                modifier = GlanceModifier.fillMaxWidth()
                    .background(if (s.atRisk) C.risk.copy(alpha = 0.10f) else C.s2)
                    .cornerRadius((16f * k).dp)
                    .padding(horizontal = (12f * k).dp, vertical = (7f * k).dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (s.atRisk) R.drawable.ic_w_flame else R.drawable.ic_w_spark, cardColor, (14f * k).dp)
                    Spacer(GlanceModifier.width((5f * k).dp))
                    Text(cardTitle, style = ts(cardColor, 12f * k, FontWeight.Bold), maxLines = 1)
                }
                Spacer(GlanceModifier.height((3f * k).dp))
                Text(
                    balanced(context, cardBody, inner - 24f * k, 14f * k),
                    style = ts(C.ink, 14f * k, FontWeight.Medium),
                    maxLines = 2,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ bitmaps

/**
 * Bitmaps for the widgets. They travel inside the RemoteViews in one binder transaction (about 1 MB for
 * everything in flight), and SizeMode.Exact sends one layout per size (portrait and landscape). So pixel
 * sizes are rounded to a few buckets and kept in a small cache: both layouts then hold the same Bitmap
 * object, which the RemoteViews bitmap cache sends once.
 */
internal object Art {
    /** About 2.5 px per dp: sharp on the Fold's ~2.6x screens. */
    private const val PX_PER_DP = 2f
    private const val SEAL_MAX = 240

    private val cache = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 32
    }

    @Synchronized
    private fun cached(key: String, make: () -> Bitmap): Bitmap = cache.getOrPut(key, make)

    private fun bucket(dp: Float, step: Int, min: Int, max: Int): Int {
        val raw = (dp * PX_PER_DP).roundToInt()
        return (((raw + step - 1) / step) * step).coerceIn(min, max)
    }

    private fun q(v: Float) = (v.coerceIn(0f, 1f) * 100).roundToInt()

    private fun argb(c: Color, alpha: Float = c.alpha): Int = android.graphics.Color.argb(
        (alpha * 255).roundToInt().coerceIn(0, 255),
        (c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt(),
    )

    /** The Seal, inset so the glow of closed rings (and the Perfect ring) isn't cut off at the edges. */
    fun seal(s: HomeState, dp: Float): Bitmap {
        val px = bucket(dp, 40, 160, SEAL_MAX)
        val progress = Habit.entries.map { s.progress[it] ?: 0f }
        val bad = if (s.calorieState == CalorieState.OVER) Habit.CALORIES.ordinal else null
        val key = "seal:$px:${progress.joinToString(",") { q(it).toString() }}:$bad"
        return cached(key) {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            // Wide enough that the Perfect glow's blur fades out before the bitmap edge (0.08 showed a hard box).
            val inset = px * 0.05f
            c.translate(inset, inset)
            SealArt.draw(c, px - 2 * inset, progress, t = 0.6f, stroke = 7f, bad = bad)
            bmp
        }
    }

    /** A habit ring: a faint track and an arc to [value], glowing when [glowAt] is reached. */
    fun ring(color: Color, value: Float, dp: Float, glowAt: Float = 1f): Bitmap {
        val px = bucket(dp, 16, 64, 128)
        val key = "ring:$px:${argb(color)}:${q(value)}:${q(glowAt)}"
        return cached(key) {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val stroke = px * 0.1f
            val r = px / 2f - stroke / 2 - px * 0.06f
            val oval = RectF(px / 2f - r, px / 2f - r, px / 2f + r, px / 2f + r)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
            p.color = argb(color, 0.18f)
            c.drawOval(oval, p)
            val v = value.coerceIn(0f, 1f)
            if (v > 0f) {
                if (v >= glowAt) {
                    val g = Paint(p).apply {
                        this.color = argb(color, 0.8f)
                        maskFilter = BlurMaskFilter(px * 0.05f, BlurMaskFilter.Blur.NORMAL)
                    }
                    c.drawArc(oval, -90f, 360f * v, false, g)
                }
                p.color = argb(color)
                c.drawArc(oval, -90f, 360f * v, false, p)
            }
            bmp
        }
    }

    /** A rounded progress bar: [track] behind, [color] up to [value]. */
    fun bar(color: Color, track: Color, value: Float, trackDp: Float, thickDp: Float): Bitmap {
        val w = bucket(trackDp, 8, 32, 480)
        val h = (thickDp * PX_PER_DP).roundToInt().coerceAtLeast(4)
        val key = "bar:$w:$h:${argb(color)}:${argb(track)}:${q(value)}"
        return cached(key) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = h / 2f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = argb(track)
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, p)
            val v = value.coerceIn(0f, 1f)
            if (v > 0f) {
                p.color = argb(color)
                c.drawRoundRect(RectF(0f, 0f, (w * v).coerceAtLeast(h.toFloat()), h.toFloat()), r, r, p)
            }
            bmp
        }
    }

    /** A closed habit in the strip: a full glowing ring around a tinted disc. */
    fun doneRing(color: Color, dp: Float): Bitmap {
        val px = bucket(dp, 16, 64, 128)
        val key = "done:$px:${argb(color)}"
        return cached(key) {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val stroke = px * 0.1f
            val r = px / 2f - stroke / 2 - px * 0.06f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = argb(color, 0.16f) }
            c.drawCircle(px / 2f, px / 2f, r, fill)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
            p.color = argb(color, 0.8f)
            p.maskFilter = BlurMaskFilter(px * 0.05f, BlurMaskFilter.Blur.NORMAL)
            c.drawCircle(px / 2f, px / 2f, r, p)
            p.maskFilter = null
            p.color = argb(color)
            c.drawCircle(px / 2f, px / 2f, r, p)
            bmp
        }
    }

    /** A filled circle (52 of [dp]) with a soft glow around it, for the logged bath. */
    fun doneDot(color: Color, dp: Float): Bitmap {
        val px = bucket(dp, 16, 96, 224)
        val key = "dot:$px:${argb(color)}"
        return cached(key) {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = px * 26f / 76f
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = argb(color, 0.6f)
                maskFilter = BlurMaskFilter(px * 0.11f, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawCircle(px / 2f, px / 2f, r, glow)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = argb(color) }
            c.drawCircle(px / 2f, px / 2f, r, p)
            bmp
        }
    }

    /** A soft corner glow for the streak card, stretched to the card. */
    fun glow(color: Color, alpha: Float): Bitmap {
        val px = 64
        val key = "glow:${argb(color)}:${q(alpha)}"
        return cached(key) {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    0f, 0f, px * 1.2f,
                    intArrayOf(argb(color, alpha), argb(color, 0f)),
                    floatArrayOf(0f, 0.6f),
                    Shader.TileMode.CLAMP,
                )
            }
            c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), p)
            bmp
        }
    }
}
