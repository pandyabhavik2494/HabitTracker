package com.pandyabhavik.habittracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandyabhavik.habittracker.core.Cat
import com.pandyabhavik.habittracker.core.Catalog
import com.pandyabhavik.habittracker.core.Packs
import com.pandyabhavik.habittracker.core.Rarity
import com.pandyabhavik.habittracker.core.Streaks
import com.pandyabhavik.habittracker.data.HomeState
import java.time.Instant

@Composable
fun AlbumScreen(s: HomeState) {
    val a = LocalActions.current
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val columns = if (a.wide) 6 else 3
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (a.wide) 20.dp else 16.dp)
            .testTag("album"),
    ) {
        TopBar(
            s, showSettings = !a.wide,
            kicker = "Purrfect Days · Series 1",
            // Swaps live in the pill beside it; the title stays one line.
            title = "${s.owned.size} of ${Catalog.SIZE} cats",
        )
        s.unopened.firstOrNull()?.let { p ->
            Spacer(Modifier.height(6.dp))
            Panel(
                Modifier.fillMaxWidth().clickable { a.openPack(p.id) }.testTag("album-pack"),
                borderColor = C.weigh.copy(alpha = 0.6f),
                background = Brush.linearGradient(listOf(Color(0xFF2A1E4A), Color(0xFF14283A))),
            ) {
                Text(if (s.unopened.size == 1) "1 pack to rip" else "${s.unopened.size} packs to rip", style = T.h2)
                Text("Tap to open it now.", style = T.body)
            }
        }
        Spacer(Modifier.height(10.dp))
        Milestones(s)
        Spacer(Modifier.height(16.dp))
        // A real album: every cat has a fixed slot, so the gaps show what's still out there.
        val order = Catalog.all.sortedBy { orderOf(it.rarity) }
        order.chunked(columns).forEachIndexed { i, row ->
            // Every slot in a row is as tall as the row's tallest (two-line unlock hints), so scrolling any
            // slot into view brings its whole row, never half of a neighbour's hint.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { j, cat -> Slot(cat, s, Modifier.weight(1f).fillMaxHeight(), (i * columns + j) * 0.37f) { open = cat.id } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(14.dp))
        }
        val secrets = Catalog.all.count { it.rarity == Rarity.SECRET }
        val legendary = Catalog.all.count { it.rarity == Rarity.LEGENDARY }
        Text(
            "Odds per pack: ${Packs.ODDS_TEXT}. The ${secrets + legendary} silhouette cats " +
                "($secrets Secret, $legendary Legendary) never come from packs: each shows how to unlock it.",
            style = T.small,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // Clear of the navigation bar or taskbar (on both layouts) and the narrow layout's bottom bar, so the
        // last row is never cut off.
        val navBottom = maxOf(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding(),
        )
        Spacer(Modifier.height(navBottom + (if (a.wide) 0.dp else BOTTOM_BAR_HEIGHT) + 24.dp))
    }
    open?.let { id -> StickerSheet(Catalog[id], s) { open = null } }
}

/** Height of the narrow layout's bottom tab bar (AppRoot's BottomBar). */
private val BOTTOM_BAR_HEIGHT = 64.dp

/** The rarity's one colour, from [Rarity.argb]: used by the album, sticker sheet, reveal and share card. */
internal val Rarity.color: Color get() = Color(argb)

private fun orderOf(r: Rarity) = when (r) {
    Rarity.COMMON -> 0
    Rarity.UNCOMMON -> 1
    Rarity.RARE -> 2
    Rarity.HOLO -> 3
    Rarity.LEGENDARY -> 4
    Rarity.SECRET -> 5
}

@Composable
private fun Slot(cat: Cat, s: HomeState, modifier: Modifier, phase: Float, onOpen: () -> Unit) {
    val owned = s.owned[cat.id]
    val hidden = cat.rarity == Rarity.SECRET || cat.rarity == Rarity.LEGENDARY
    Column(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(enabled = owned != null) { onOpen() }
            .padding(vertical = 4.dp)
            .testTag("slot-${cat.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val size = 92.dp
        when {
            owned != null -> Box(contentAlignment = Alignment.TopEnd) {
                StickerWithGlow(cat, size, phase)
                if (owned.count > 1) {
                    Text(
                        "×${owned.count}",
                        style = T.small.copy(color = Color(0xFF2A0B1D), fontFamily = Display, fontSize = 11.sp),
                        modifier = Modifier.clip(CircleShape).background(C.swap).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            hidden -> CatSticker(cat.id, size, silhouette = true)
            else -> DashedSlot(size)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            // Locked cats never give their name or breed away: "???", then the rarity or how to unlock it.
            if (owned != null) cat.name else "???",
            style = T.label.copy(fontSize = 13.sp, color = if (owned != null) cat.rarity.color else C.ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when {
                // "Ragdoll / Ragdoll" says it twice: show the rarity instead.
                owned != null -> if (cat.breed != cat.name) cat.breed else cat.rarity.label
                hidden -> cat.hint ?: ""
                else -> cat.rarity.label
            },
            style = T.small.copy(fontSize = 11.sp, lineHeight = 14.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StickerWithGlow(cat: Cat, size: Dp, phase: Float) {
    if (cat.rarity == Rarity.HOLO || cat.rarity == Rarity.LEGENDARY) {
        // Read only inside drawBehind, so the frame clock never recomposes the album grid.
        val clock = rememberClock()
        val colors = listOf(Color(0xFFFF9AD5), Color(0xFF9BE7FF), Color(0xFFC6A8FF))
        Box(
            Modifier.drawBehind {
                val c = colors[(((clock.value + phase) / 1.3f).toInt()) % 3]
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent)), radius = this.size.minDimension * 0.62f)
            },
        ) { CatSticker(cat.id, size, phase = phase) }
    } else {
        CatSticker(cat.id, size, phase = phase)
    }
}

@Composable
private fun DashedSlot(size: Dp) {
    Box(
        Modifier
            .size(size)
            .drawBehind {
                drawCircle(
                    Color(0xFF2C3140),
                    radius = this.size.minDimension / 2 - 2f,
                    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            },
        contentAlignment = Alignment.Center,
    ) { Text("?", style = T.big.copy(color = Color(0xFF3A4050))) }
}

@Composable
private fun Milestones(s: HomeState) {
    val next = Streaks.nextMilestone(s.streak)
    // Only real milestones are stops; today's streak is a marker placed proportionally between them.
    val points = Streaks.MILESTONES
    val frac = milestoneFraction(s.streak, points)
    Panel(Modifier.fillMaxWidth().testTag("milestones")) {
        Caps("Milestones", icon = Icons.Rounded.EmojiEvents)
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val dot = 30.dp
            // Stops sit at the centres of their 30dp slots, from dot/2 to width - dot/2.
            val markerX = dot / 2 + (maxWidth - dot) * frac
            val labelW = 64.dp
            val maxX = (maxWidth - labelW).coerceAtLeast(0.dp)
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Day ${s.streak}",
                    style = T.small.copy(color = C.ink, fontSize = 11.sp, fontFamily = Display),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .width(labelW)
                        .offset(x = (markerX - labelW / 2).coerceIn(0.dp, maxX))
                        .testTag("milestone-marker"),
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val y = dot.toPx() / 2
                            val x0 = dot.toPx() / 2
                            val x1 = size.width - dot.toPx() / 2
                            val x = x0 + (x1 - x0) * frac
                            drawLine(C.s3, Offset(x0, y), Offset(x1, y), strokeWidth = 3.dp.toPx())
                            drawLine(C.ink, Offset(x0, y), Offset(x, y), strokeWidth = 3.dp.toPx())
                        }
                        .drawWithContent {
                            drawContent()
                            // The marker draws over the stops, so it shows even when it sits on one.
                            val y = dot.toPx() / 2
                            val x = dot.toPx() / 2 + (size.width - dot.toPx()) * frac
                            drawCircle(C.bg, radius = 9.dp.toPx(), center = Offset(x, y))
                            drawCircle(C.ink, radius = 6.dp.toPx(), center = Offset(x, y))
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    points.forEach { p ->
                        val done = p <= s.streak
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(dot), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (done) C.ink else C.s2)
                                        .border(1.dp, if (done) C.ink else C.line, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (done) Icon(Icons.Rounded.Check, null, tint = Color(0xFF0A0B10), modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("$p", style = T.small.copy(color = if (done) C.ink else C.mute, fontSize = 11.sp, fontFamily = Display))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (next == null) "Past every milestone. Legend." else "${next - s.streak} day${if (next - s.streak == 1) "" else "s"} to $next: ${Packs.milestoneText(next)}.",
            style = T.body.copy(color = C.ink),
        )
    }
}

/**
 * Where [streak] sits on a track whose [stops] are evenly spaced, 0..1: proportional within the gap
 * between the two stops around it. Before the first stop it sits at the start of the track.
 */
private fun milestoneFraction(streak: Int, stops: List<Int>): Float {
    if (stops.size < 2) return 0f
    val gaps = (stops.size - 1).toFloat()
    if (streak <= 0) return 0f
    if (streak >= stops.last()) return 1f
    val i = stops.indexOfLast { it <= streak }
    if (i < 0) return 0f
    val within = (streak - stops[i]).toFloat() / (stops[i + 1] - stops[i])
    return (i + within) / gaps
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerSheet(cat: Cat, s: HomeState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owned = s.owned[cat.id] ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.s1,
        contentColor = C.ink,
        // Dark enough that the album's labels don't read through beside the sheet.
        scrimColor = Color.Black.copy(alpha = 0.6f),
    ) {
        LightSheetBars()
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .testTag("sticker-sheet"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(cat.rarity.line.uppercase(), style = T.caps.copy(color = cat.rarity.color, letterSpacing = 3.sp))
            Spacer(Modifier.height(10.dp))
            StickerWithGlow(cat, 200.dp, 0f)
            Spacer(Modifier.height(12.dp))
            Text(cat.name, style = T.hero.copy(fontSize = 36.sp))
            if (cat.breed != cat.name) Text(cat.breed.uppercase(), style = T.caps.copy(letterSpacing = 3.sp))
            Spacer(Modifier.height(4.dp))
            Text("“${cat.line}”", style = T.body.copy(color = C.ink, fontStyle = FontStyle.Italic), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "First pulled ${F.monthDay.format(Instant.ofEpochMilli(owned.firstAt).atZone(s.zone))}" +
                    if (owned.count > 1) " · ×${owned.count} (${owned.count - 1} became swaps)" else "",
                style = T.small,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(CircleShape)
                        .background(C.whatsapp)
                        .clickable { Share.whatsapp(context, cat, s) },
                    contentAlignment = Alignment.Center,
                ) { Text("Send to WhatsApp", style = T.label.copy(color = Color(0xFF052E14))) }
                Box(
                    Modifier
                        .weight(0.8f)
                        .height(50.dp)
                        .clip(CircleShape)
                        // Outlined, like the Story button on the reveal.
                        .background(C.ink.copy(alpha = 0.10f))
                        .border(1.5.dp, C.ink.copy(alpha = 0.7f), CircleShape)
                        .clickable { Share.story(context, cat, s) },
                    contentAlignment = Alignment.Center,
                ) { Text("Story", style = T.label) }
            }
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.width(1.dp))
        }
    }
}
