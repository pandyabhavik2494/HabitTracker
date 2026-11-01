package com.pandyabhavik.habittracker.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandyabhavik.habittracker.art.CatArt
import com.pandyabhavik.habittracker.art.SealArt
import com.pandyabhavik.habittracker.core.Habit
import kotlinx.coroutines.launch

/** Seconds since this composable appeared, ticking every frame while [running]. */
@Composable
fun rememberClock(running: Boolean = true): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    if (running) {
        LaunchedEffect(Unit) {
            // Infinite-animation frames: the same as withFrameNanos in the app, but Compose UI tests know
            // to stop it (they would otherwise never go idle and time out).
            val start = withInfiniteAnimationFrameNanos { it }
            while (true) {
                withInfiniteAnimationFrameNanos { t.floatValue = (it - start) / 1_000_000_000f }
            }
        }
    }
    return t
}

fun Habit.icon(): ImageVector = when (this) {
    Habit.STEPS -> Icons.AutoMirrored.Rounded.DirectionsWalk
    Habit.CALORIES -> Icons.Rounded.Restaurant
    Habit.WEIGH -> Icons.Rounded.MonitorWeight
    Habit.BATH -> Icons.Rounded.Bathtub
}

/** The four-ring ring. Progress animates in; closed rings glow; a Perfect Day turns iridescent. */
@Composable
fun Seal(
    progress: Map<Habit, Float>,
    size: Dp,
    modifier: Modifier = Modifier,
    stroke: Float = 9f,
    pulse: Habit? = null,
    bad: Habit? = null,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val values = Habit.entries.map { h ->
        val target = progress[h] ?: 0f
        if (animate) animateFloatAsState(target, tween(900), label = "seal-$h").value else target
    }
    val perfect = values.all { it >= 1f } && bad == null
    val t by rememberClock(animate && (perfect || pulse != null))
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // The canvas is half as big again as the ring's box, centred on it, so the glow (which reaches
        // about 20% past the box) is drawn inside the canvas and never cut off by its bounds.
        Canvas(Modifier.requiredSize(size * SEAL_ROOM)) {
            val inset = (this.size.width - this.size.width / SEAL_ROOM) / 2f
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save()
                nc.translate(inset, inset)
                SealArt.draw(nc, this.size.width / SEAL_ROOM, values, t, stroke, pulse?.ordinal, bad?.ordinal)
                nc.restore()
            }
        }
        content()
    }
}

private const val SEAL_ROOM = 1.5f

/** A round cat sticker, animated. */
@Composable
fun CatSticker(
    id: String,
    size: Dp,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    silhouette: Boolean = false,
    phase: Float = 0f,
) {
    val context = LocalContext.current
    val cat = remember(id) { CatArt.get(context, id) }
    val t by rememberClock(animate && !silhouette)
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = if (silhouette) "Locked sticker" else "Sticker: $id" },
    ) {
        if (cat == null) return@Canvas
        drawIntoCanvas { c -> CatArt.draw(c.nativeCanvas, cat, this.size.minDimension, t, phase, silhouette) }
    }
}

@Composable
fun Pill(icon: ImageVector, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = T.label.copy(color = color, fontFamily = Display))
    }
}

@Composable
fun StreakPills(streak: Int, swaps: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(Icons.Rounded.LocalFireDepartment, "$streak", C.flame, Modifier.testTag("streak-pill"))
        Pill(Icons.Rounded.AutoAwesome, "$swaps", C.swap, Modifier.testTag("swaps-pill"))
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    borderColor: Color = C.line,
    background: Brush? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .then(if (background != null) Modifier.background(background) else Modifier.background(C.s1))
            .border(1.dp, borderColor, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Caps(text: String, color: Color = C.mute, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), style = T.caps.copy(color = color))
    }
}

/** The habit's coloured icon tile. */
@Composable
fun HabitIcon(habit: Habit, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(C.of(habit)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(habit.icon(), null, tint = Color(0xFF0A0B0F), modifier = Modifier.size(size * 0.55f))
    }
}

/** A row on Today: icon, name, what's left, the value, and a thin progress bar. */
@Composable
fun HabitRow(
    habit: Habit,
    title: String,
    sub: String,
    value: String?,
    progress: Float,
    closed: Boolean,
    modifier: Modifier = Modifier,
    valueColor: Color = C.ink,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "row")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.s1)
            .border(1.dp, if (closed) C.of(habit).copy(alpha = 0.35f) else C.line, shape)
            .pointerInput(onLongPress) {
                detectTapGestures(onTap = { onClick() }, onLongPress = onLongPress?.let { { _ -> it() } })
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("habit-${habit.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HabitIcon(habit)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = T.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!closed && progress > 0f) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.92f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(C.s3),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(p)
                            .clip(CircleShape)
                            .background(C.of(habit)),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        // A fixed-width slot for the trailing value, so the text column (and its progress bar) is the
        // same width on every row, whatever the number's width.
        Box(Modifier.widthIn(min = 64.dp), contentAlignment = Alignment.CenterEnd) {
            when {
                trailing != null -> trailing()
                closed -> CheckDot(C.of(habit))
                value != null -> Text(value, style = T.num.copy(color = valueColor), textAlign = TextAlign.End, maxLines = 1)
            }
        }
    }
}

@Composable
fun CheckDot(color: Color, size: Dp = 26.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, null, tint = Color(0xFF0A0B0F), modifier = Modifier.size(size * 0.65f))
    }
}

/**
 * Press and hold to confirm: a gradient fills the button under the thumb, with a haptic tick at the
 * start and a confirm when it completes. Letting go early rewinds it.
 */
@Composable
fun HoldButton(
    text: String,
    modifier: Modifier = Modifier,
    millis: Int = 1_000,
    haptics: Boolean = true,
    brush: Brush = Brush.horizontalGradient(listOf(C.bath.copy(alpha = 0.3f), C.weigh.copy(alpha = 0.6f))),
    onDone: () -> Unit,
) {
    val fill = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val shape = RoundedCornerShape(999.dp)
    // The gesture loop below lives as long as the button; read the latest callback and setting.
    val currentOnDone by rememberUpdatedState(onDone)
    val currentHaptics by rememberUpdatedState(haptics)
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(C.s2)
            .border(1.dp, C.line, shape)
            .drawBehind {
                drawRect(brush, size = this.size.copy(width = this.size.width * fill.value))
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (currentHaptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    val job = scope.launch {
                        fill.snapTo(0f)
                        fill.animateTo(1f, tween(millis, easing = LinearEasing))
                        if (currentHaptics) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        currentOnDone()
                    }
                    // Stops filling on release, when the finger drags off the button, or when a parent
                    // (the scrolling screen) takes the gesture over.
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        if (event.changes.any { it.isConsumed || it.isOutOfBounds(size, extendedTouchPadding) }) break
                        // A parent scroll consumes in the Main pass after us; check again on the Final pass.
                        val late = awaitPointerEvent(PointerEventPass.Final)
                        if (late.changes.any { it.isConsumed }) break
                    }
                    if (fill.value < 1f) {
                        job.cancel()
                        scope.launch { fill.animateTo(0f, tween(250)) }
                    }
                }
            }
            .semantics { onClick(label = text) { currentOnDone(); true } }
            .testTag("hold-$text"),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.label)
    }
}
