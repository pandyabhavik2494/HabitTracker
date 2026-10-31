package com.pandyabhavik.habittracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pandyabhavik.habittracker.R
import com.pandyabhavik.habittracker.core.Habit

/** The mock's palette: near-black, with one colour per habit. */
object C {
    val bg = Color(0xFF050608)
    val s1 = Color(0xFF0D0F14)
    val s2 = Color(0xFF151820)
    val s3 = Color(0xFF1D212C)
    val line = Color(0xFF252A37)
    val ink = Color(0xFFF3F4F8)
    val mute = Color(0xFF8D93A6)
    val dim = Color(0xFF5A6072)
    val steps = Color(0xFF3BE38F)
    val calories = Color(0xFFFF8A3D)
    val weigh = Color(0xFFA98BFF)
    val bath = Color(0xFF43D3EA)
    val flame = Color(0xFFFF6B57)
    val risk = Color(0xFFFF5A6A)
    val swap = Color(0xFFFF8FD0)
    val whatsapp = Color(0xFF25D366)

    fun of(h: Habit): Color = when (h) {
        Habit.STEPS -> steps
        Habit.CALORIES -> calories
        Habit.WEIGH -> weigh
        Habit.BATH -> bath
    }

    // Orange never meets green directly: that blend passes through yellow.
    val iridescent = listOf(steps, bath, weigh, calories, weigh, bath, steps)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun display(weight: Int) = Font(
    R.font.bricolage,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun body(weight: Int) = Font(
    R.font.figtree,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(display(400), display(600), display(700), display(800))
val Body = FontFamily(body(400), body(500), body(600), body(700), body(800))

object T {
    val hero = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 54.sp, letterSpacing = (-1.6).sp, color = C.ink)
    val big = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 30.sp, letterSpacing = (-0.6).sp, color = C.ink)
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 22.sp, letterSpacing = (-0.2).sp, color = C.ink)
    val h2 = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 18.sp, color = C.ink)
    val label = TextStyle(fontFamily = Body, fontWeight = FontWeight(700), fontSize = 15.sp, color = C.ink)
    val body = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 14.sp, color = C.mute, lineHeight = 20.sp)
    val small = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 12.sp, color = C.mute)
    val caps = TextStyle(fontFamily = Body, fontWeight = FontWeight(700), fontSize = 11.sp, letterSpacing = 1.4.sp, color = C.mute)
    val num = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 20.sp, color = C.ink)
}

@Composable
fun HabitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = C.bg,
            surface = C.s1,
            surfaceVariant = C.s2,
            primary = C.bath,
            onPrimary = Color(0xFF04141A),
            secondary = C.weigh,
            onBackground = C.ink,
            onSurface = C.ink,
            onSurfaceVariant = C.mute,
            outline = C.line,
            error = C.risk,
        ),
        content = content,
    )
}
