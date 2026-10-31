package com.pandyabhavik.habittracker.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandyabhavik.habittracker.core.Catalog
import com.pandyabhavik.habittracker.core.Packs
import com.pandyabhavik.habittracker.core.Rarity
import com.pandyabhavik.habittracker.core.Streaks
import com.pandyabhavik.habittracker.data.HabitRepository
import com.pandyabhavik.habittracker.data.HomeState
import com.pandyabhavik.habittracker.data.PackEntity
import com.pandyabhavik.habittracker.data.PackResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Perfect Day: the sealed pack, the rip, and the reveal. */
@Composable
fun PackFlow(packId: Long, s: HomeState, onClose: () -> Unit) {
    val a = LocalActions.current
    val pack = s.packs.firstOrNull { it.id == packId }
    var result by remember(packId) { mutableStateOf<PackResult?>(null) }
    // A pack opened earlier (or on another screen) goes straight to its reveal. Checked once on entry:
    // keying on openedCat would re-run when the rip itself writes the DB and cut its animation short.
    LaunchedEffect(packId) {
        if (pack?.openedCat != null && result == null) result = a.vm.openPack(packId)
    }
    BackHandler { onClose() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1B1630), C.bg), radius = 1600f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .testTag("pack-flow"),
    ) {
        val r = result
        if (pack == null && r == null) {
            LaunchedEffect(Unit) { onClose() }
            return@Box
        }
        if (r == null && pack != null) {
            Sealed(s, pack) { result = it }
        } else if (r != null) {
            Reveal(s, r, onClose)
        }
    }
}

@Composable
private fun Sealed(s: HomeState, pack: PackEntity, onOpened: (PackResult) -> Unit) {
    val reason = pack.reason
    val fixedCat = pack.fixedCat
    val minRarity = pack.minRarity
    val a = LocalActions.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val rip = remember { Animatable(0f) }
    var ripping by remember { mutableStateOf(false) }
    // Read only in the layer and draw lambdas below, so the frame clock never recomposes this screen.
    val clock = rememberClock()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        val (kicker, head) = when (reason) {
            HabitRepository.REASON_SECRET -> "Secret unlocked" to (Catalog.find(fixedCat)?.hint ?: "A secret cat")
            HabitRepository.REASON_COMEBACK -> "Comeback complete" to "3 Perfect Days in a row. She's yours."
            HabitRepository.REASON_MILESTONE -> "${s.streak}-day milestone" to (if (minRarity == Rarity.HOLO.name) "Holo guaranteed." else "Rare or better, guaranteed.")
            else -> "Perfect Day #${packNumber(s, pack)}" to "You earned a pack."
        }
        Text(kicker.uppercase(), style = T.caps.copy(color = C.ink, letterSpacing = 3.sp))
        Spacer(Modifier.height(10.dp))
        if (reason == HabitRepository.REASON_DAILY || reason == HabitRepository.REASON_MILESTONE) StreakTicker(s.streak)
        Spacer(Modifier.height(6.dp))
        Text(head, style = T.body.copy(color = C.ink), textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .size(220.dp, 300.dp)
                .graphicsLayer {
                    // Wobbles for half a second every 2.4 seconds until it's torn.
                    val phase = clock.value % 2.4f
                    val shake = if (!ripping && phase > 1.7f) sin((phase - 1.7f) * 40f) * 4f * (1f - (phase - 1.7f) / 0.7f) else 0f
                    rotationZ = shake - 14f * rip.value
                    translationY = -60.dp.toPx() * rip.value
                    scaleX = 1f + 0.15f * rip.value
                    scaleY = 1f + 0.15f * rip.value
                    alpha = 1f - rip.value
                }
                .clip(RoundedCornerShape(22.dp))
                .drawBehind { drawRect(foil(clock.value)) }
                .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .clickable(enabled = !ripping) {
                    ripping = true
                    if (s.settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    scope.launch {
                        val res = a.vm.openPack(pack.id)
                        rip.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
                        if (res != null) onOpened(res)
                    }
                }
                .testTag("pack"),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SERIES 1", style = T.caps.copy(color = Color.White, letterSpacing = 3.sp))
                Text("PURRFECT\nDAYS", style = T.big.copy(color = Color.White, lineHeight = 30.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0A0B10)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (fixedCat != null) CatSticker(fixedCat, 74.dp, silhouette = true)
                    else Text("?", style = T.hero.copy(color = Color.White, fontSize = 60.sp))
                }
                Spacer(Modifier.height(10.dp))
                Text("1 STICKER INSIDE", style = T.caps.copy(color = Color.White))
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(if (ripping) "Ripping…" else "Tap to rip it open", style = T.h2)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                fixedCat != null -> "${Catalog.find(fixedCat)?.rarity?.label ?: ""} · one of a kind"
                minRarity == Rarity.HOLO.name -> "Holo 100%"
                minRarity == Rarity.RARE.name -> "Rare 67% · Holo 33%"
                else -> Packs.ODDS_TEXT
            },
            style = T.small,
            textAlign = TextAlign.Center,
        )
    }
}

private fun foil(t: Float): Brush {
    val shift = (t * 120f) % 600f
    return Brush.linearGradient(
        listOf(Color(0xFFFF8FD0), Color(0xFFA98BFF), Color(0xFF43D3EA), Color(0xFF3BE38F), Color(0xFFFF8FD0)),
        start = Offset(-600f + shift, 0f),
        end = Offset(shift, 600f),
        tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
    )
}

/** The Perfect Day number a pack was earned on: today's number, or the run that ended on the pack's day. */
internal fun packNumber(s: HomeState, pack: PackEntity): Int {
    val date = runCatching { LocalDate.parse(pack.date) }.getOrNull() ?: return s.dayNumber
    if (date == s.now.toLocalDate()) return s.dayNumber
    return Streaks.runEndingAt(s.days, date).coerceAtLeast(1)
}

@Composable
private fun StreakTicker(streak: Int) {
    val flip = remember { Animatable(0f) }
    LaunchedEffect(streak) {
        delay(400)
        flip.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (streak > 1) {
            Text(
                "${streak - 1}",
                style = T.big.copy(color = C.dim, textDecoration = TextDecoration.LineThrough),
                modifier = Modifier.graphicsLayer { alpha = 1f - 0.4f * flip.value },
            )
            Spacer(Modifier.size(10.dp))
        }
        Text(
            "$streak",
            style = T.hero.copy(fontSize = 48.sp),
            modifier = Modifier.graphicsLayer {
                scaleX = 0.6f + 0.4f * flip.value
                scaleY = 0.6f + 0.4f * flip.value
                alpha = flip.value
            },
        )
        Icon(Icons.Rounded.LocalFireDepartment, null, tint = C.flame, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun Reveal(s: HomeState, r: PackResult, onClose: () -> Unit) {
    val a = LocalActions.current
    val context = LocalContext.current
    val view = LocalView.current
    val cat = r.cat
    val color = F.rarityColor(cat.rarity)
    val drop = remember { Animatable(0f) }
    val flash = remember { Animatable(0.9f) }
    // Read only inside draw lambdas, so the frame clock never recomposes the reveal.
    val clock = rememberClock()
    LaunchedEffect(r.pack.id) {
        launch { flash.animateTo(0f, tween(600)) }
        drop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
        if (s.settings.haptics) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            if (cat.rarity.ordinal >= Rarity.RARE.ordinal) buzz(context)
        }
    }
    val owned = s.owned.size
    val left = Catalog.SIZE - owned
    val secretsLeft = Catalog.all.count { it.rarity == Rarity.SECRET && it.id !in s.owned }
    val more = s.unopened.firstOrNull { it.id != r.pack.id }

    Box(Modifier.fillMaxSize()) {
        // Rays in the rarity colour, slowly turning. Built once per size, then only rotated each frame.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val c = Offset(size.width / 2, size.height * 0.3f)
                    val n = 16
                    val w = PI / n * 0.55
                    val len = size.maxDimension
                    val rays = Path()
                    for (i in 0 until n) {
                        val ang = i * 2 * PI / n
                        rays.moveTo(c.x, c.y)
                        rays.lineTo(c.x + (len * cos(ang - w)).toFloat(), c.y + (len * sin(ang - w)).toFloat())
                        rays.lineTo(c.x + (len * cos(ang + w)).toFloat(), c.y + (len * sin(ang + w)).toFloat())
                        rays.close()
                    }
                    val brush = Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), c, len * 0.6f)
                    onDrawBehind {
                        rotate(clock.value * 12f, c) { drawPath(rays, brush) }
                    }
                },
        )
        Confetti(clock)
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            Text(F.rarityLine(cat.rarity).uppercase(), style = T.caps.copy(color = color, letterSpacing = 3.sp), modifier = Modifier.testTag("reveal-rarity"))
            Spacer(Modifier.height(14.dp))
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    Modifier
                        .graphicsLayer {
                            val v = drop.value
                            scaleX = 0.1f + 0.9f * v
                            scaleY = 0.1f + 0.9f * v
                            rotationZ = -40f + 34f * v
                            alpha = v.coerceIn(0f, 1f)
                        },
                ) {
                    HoloGlow(cat.rarity, clock)
                    CatSticker(cat.id, 200.dp, Modifier.testTag("reveal-sticker"))
                }
                val badge = if (r.isNew) "NEW!" else "+1 SWAP"
                Text(
                    badge,
                    style = T.label.copy(color = Color(0xFF2A0B1D), fontFamily = Display),
                    modifier = Modifier
                        .offset(x = 14.dp, y = (-6).dp)
                        .rotate(12f)
                        .graphicsLayer {
                            val v = ((drop.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                            scaleX = 0.6f + 0.4f * v
                            scaleY = 0.6f + 0.4f * v
                            alpha = v
                        }
                        .clip(CircleShape)
                        .background(if (r.isNew) C.swap else C.bath)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("reveal-badge"),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(cat.name, style = T.hero.copy(fontSize = 40.sp), modifier = Modifier.testTag("reveal-name"))
            Text(cat.breed.uppercase(), style = T.caps.copy(letterSpacing = 3.sp))
            Spacer(Modifier.height(4.dp))
            Text("“${cat.line}”", style = T.body.copy(color = C.ink, fontStyle = FontStyle.Italic), textAlign = TextAlign.Center)
            if (!r.isNew) {
                Spacer(Modifier.height(6.dp))
                Text("Duplicate: it becomes a swap. 3 swaps save a missed day. You have ${s.swaps}.", style = T.small, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Purrfect Days · Series 1", style = T.small.copy(color = C.ink), modifier = Modifier.weight(1f))
                    Text("$owned of ${Catalog.SIZE}", style = T.label)
                }
                Spacer(Modifier.height(6.dp))
                val fill = remember { Animatable(((owned - if (r.isNew) 1 else 0).toFloat() / Catalog.SIZE)) }
                LaunchedEffect(owned) { delay(500); fill.animateTo(owned.toFloat() / Catalog.SIZE, tween(900)) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(C.s3),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fill.value)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(C.steps, C.bath, C.weigh, C.swap))),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (left == 0) "Series complete. Every cat is yours." else "$left cats still out there. $secretsLeft ${if (secretsLeft == 1) "is" else "are"} secret.",
                    style = T.small,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(CircleShape)
                        .background(C.whatsapp)
                        .clickable { Share.whatsapp(context, cat, s, packNumber(s, r.pack)) }
                        .testTag("share-whatsapp"),
                    contentAlignment = Alignment.Center,
                ) { Text("Send to WhatsApp", style = T.label.copy(color = Color(0xFF052E14))) }
                Box(
                    Modifier
                        .weight(0.8f)
                        .height(50.dp)
                        .clip(CircleShape)
                        .background(C.s2)
                        .clickable { Share.story(context, cat, s, packNumber(s, r.pack)) }
                        .testTag("share-story"),
                    contentAlignment = Alignment.Center,
                ) { Text("Story", style = T.label) }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (more != null) a.openPack(more.id) else onClose()
                    }
                    .testTag("reveal-done"),
                contentAlignment = Alignment.Center,
            ) { Text(if (more != null) "Rip the next pack" else "Done", style = T.label.copy(color = C.mute)) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = flash.value }
                .background(Color.White),
        )
    }
}

@Composable
private fun HoloGlow(rarity: Rarity, clock: State<Float>) {
    if (rarity.ordinal < Rarity.HOLO.ordinal) return
    Canvas(Modifier.size(200.dp)) {
        val colors = listOf(Color(0xFFFF9AD5), Color(0xFF9BE7FF), Color(0xFFC6A8FF), Color(0xFFFF9AD5))
        val i = ((clock.value / 1.2f) % 3f).toInt()
        val c = colors[i]
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.7f), Color.Transparent)), radius = size.minDimension * 0.62f)
    }
}

@Composable
private fun Confetti(clock: State<Float>) {
    val bits = remember {
        val r = Random(7)
        List(46) {
            floatArrayOf(r.nextFloat(), r.nextFloat() * 1.2f, 0.5f + r.nextFloat(), r.nextFloat() * 360f, r.nextInt(5).toFloat())
        }
    }
    val colors = listOf(C.steps, C.calories, C.weigh, C.bath, C.swap)
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value
        for (b in bits) {
            val life = (t * 0.35f * b[2] + b[1]) % 1.4f
            if (life > 1.2f) continue
            val x = b[0] * size.width + sin((t + b[3]) * 1.5f) * 20f
            val y = -20f + life * size.height
            rotate(b[3] + t * 180f * b[2], Offset(x, y)) {
                drawRect(colors[b[4].toInt()].copy(alpha = (1.2f - life).coerceIn(0f, 1f)), Offset(x - 5f, y - 3f), androidx.compose.ui.geometry.Size(10f, 6f))
            }
        }
    }
}

private fun buzz(context: Context) {
    runCatching {
        val vm = context.getSystemService(VibratorManager::class.java)
        vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 60, 90), intArrayOf(0, 180, 0, 200, 0, 255), -1))
    }
}
