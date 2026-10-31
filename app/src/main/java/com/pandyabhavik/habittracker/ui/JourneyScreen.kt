package com.pandyabhavik.habittracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.core.Streaks
import com.pandyabhavik.habittracker.data.HomeState
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun JourneyScreen(s: HomeState) {
    val a = LocalActions.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (a.wide) 20.dp else 16.dp)
            .testTag("journey"),
    ) {
        if (a.wide) JourneyWide(s) else JourneyCover(s)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun JourneyCover(s: HomeState) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val month = YearMonth.from(s.now).minusMonths(offset.toLong())
    TopBar(s, showSettings = true, title = F.month.format(month), kicker = "Journey")
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat("${s.streak}", "Streak", C.flame)
        Stat("${s.best}", "Best ever", C.ink)
        Stat(s.rate30?.let { "${(it * 100).roundToInt()}%" } ?: "–", "Last 30 days", C.ink)
    }
    MonthCalendar(s, month, onPrev = { offset++ }, onNext = { if (offset > 0) offset-- }, canNext = offset > 0)
    Spacer(Modifier.height(12.dp))
    WeekByHabit(s)
    Spacer(Modifier.height(12.dp))
    HitRates(s)
}

@Composable
private fun JourneyWide(s: HomeState) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val month = YearMonth.from(s.now).minusMonths(offset.toLong())
    val rate = s.rate30
    TopBar(
        s, showSettings = false,
        title = if (rate != null) "You close all four ${(rate * 100).roundToInt()}% of days." else "Your first Perfect Day starts the chain.",
        kicker = "Journey",
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("${s.streak}", "Current streak", C.flame, Modifier.weight(1f))
        StatTile("${s.best}", "Best streak", C.ink, Modifier.weight(1f))
        StatTile("${s.perfectDays}", "Perfect Days", C.ink, Modifier.weight(1f))
        StatTile("${s.swaps}", "Swaps", C.swap, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    HeatGrid(s)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            MonthCalendar(s, month, onPrev = { offset++ }, onNext = { if (offset > 0) offset-- }, canNext = offset > 0)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HitRates(s)
            WeekByHabit(s)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = T.hero.copy(fontSize = 34.sp, color = color))
        Text(label.uppercase(), style = T.caps.copy(fontSize = 10.sp))
    }
}

@Composable
private fun StatTile(value: String, label: String, color: Color, modifier: Modifier) {
    Panel(modifier) {
        Text(value, style = T.big.copy(color = color))
        Text(label, style = T.small)
    }
}

/** The month with every Perfect Day lit, saved days pink, and broken days as a red dashed ring. */
@Composable
private fun MonthCalendar(s: HomeState, month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, canNext: Boolean) {
    val today = s.now.toLocalDate()
    val first = s.settings.firstDay ?: today
    Panel(Modifier.fillMaxWidth().testTag("calendar")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(F.monthYear.format(month), style = T.label, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", tint = C.mute,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onPrev() }.padding(4.dp),
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", tint = if (canNext) C.mute else C.s3,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable(enabled = canNext) { onNext() }.padding(4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, style = T.small.copy(fontSize = 11.sp), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        val lead = month.atDay(1).dayOfWeek.value % 7
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (idx in 1..month.lengthOfMonth()) DayDot(s, month.atDay(idx), today, first)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDot(s: HomeState, date: LocalDate, today: LocalDate, first: LocalDate) {
    val day = s.days[date]
    val perfect = day?.perfectAt != null || (date == today && s.perfect)
    val saved = day?.savedWithSwaps == true
    val missed = !perfect && !saved && date.isBefore(today) && !date.isBefore(first)
    val shape = CircleShape
    val base = Modifier.fillMaxSize().clip(shape)
    val mod = when {
        perfect -> base.background(Brush.linearGradient(listOf(C.bath, C.weigh)))
        saved -> base.background(C.swap)
        date == today -> base.border(2.dp, C.ink, shape)
        missed -> base.border(1.dp, C.risk.copy(alpha = 0.6f), shape)
        else -> base
    }
    Box(mod.testTag("day-$date"), contentAlignment = Alignment.Center) {
        Text(
            "${date.dayOfMonth}",
            style = T.small.copy(
                color = when {
                    perfect || saved -> Color(0xFF0A0B10)
                    missed -> C.risk
                    date.isAfter(today) -> C.dim
                    else -> C.ink
                },
                fontSize = 12.sp,
            ),
        )
    }
}

/** This week, habit by habit: a red cell is exactly what broke a day. */
@Composable
private fun WeekByHabit(s: HomeState) {
    val today = s.now.toLocalDate()
    val start = today.minusDays((today.dayOfWeek.value % 7).toLong())
    Panel(Modifier.fillMaxWidth().testTag("week-by-habit")) {
        Caps("This week by habit")
        Spacer(Modifier.height(8.dp))
        Row {
            Spacer(Modifier.width(92.dp))
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, style = T.small.copy(fontSize = 11.sp), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Habit.entries.forEach { h ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(F.habitShort(h, s), style = T.small.copy(color = C.ink, fontSize = 12.sp), modifier = Modifier.width(92.dp), maxLines = 1)
                for (i in 0..6) {
                    val d = start.plusDays(i.toLong())
                    val closed = s.closedOn(d, h)
                    val color = when {
                        d.isAfter(today) || closed == null -> C.s2
                        closed -> C.of(h)
                        d == today -> C.s3
                        else -> C.risk.copy(alpha = 0.55f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(14.dp)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color),
                    )
                }
            }
        }
    }
}

/** 26 weeks, one square a day: Perfect, saved with swaps, or missed. */
@Composable
private fun HeatGrid(s: HomeState) {
    val today = s.now.toLocalDate()
    val end = today.plusDays((6 - today.dayOfWeek.value % 7).toLong())
    val start = end.minusWeeks(26).plusDays(1)
    val first = s.settings.firstDay ?: today
    Panel(Modifier.fillMaxWidth().testTag("heat-grid")) {
        Caps("Last 26 weeks")
        Spacer(Modifier.height(10.dp))
        for (row in 0..6) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 26) {
                    val d = start.plusDays((col * 7 + row).toLong())
                    val day = s.days[d]
                    val color = when {
                        d.isAfter(today) -> Color.Transparent
                        day?.perfectAt != null || (d == today && s.perfect) -> C.ink
                        day?.savedWithSwaps == true -> C.swap
                        d.isBefore(first) -> C.s2
                        d == today -> C.s3
                        else -> Color(0xFF2A2F3E)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(C.ink, "Perfect Day")
            Legend(C.swap, "Saved with swaps")
            Legend(Color(0xFF2A2F3E), "Missed")
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = T.small.copy(fontSize = 11.sp))
    }
}

@Composable
private fun HitRates(s: HomeState) {
    val today = s.now.toLocalDate()
    val rates = Habit.entries.associateWith { Streaks.hitRate(s.days, it, s.goals, today) }
    Panel(Modifier.fillMaxWidth().testTag("hit-rates")) {
        Caps("Hit rate, last 30 days")
        Spacer(Modifier.height(10.dp))
        Habit.entries.forEach { h ->
            val r = rates[h]
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(F.habitName(h, s), style = T.small.copy(color = C.ink), modifier = Modifier.width(118.dp), maxLines = 1)
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(C.s3),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(r ?: 0f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(C.of(h)),
                    )
                }
                Text(
                    r?.let { "${(it * 100).roundToInt()}%" } ?: "–",
                    style = T.label.copy(fontSize = 13.sp),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp),
                )
            }
        }
        val worst = rates.filterValues { it != null }.minByOrNull { it.value!! }
        if (worst != null && (worst.value ?: 1f) < 1f) {
            Spacer(Modifier.height(8.dp))
            val p = s.pattern
            Text(
                "${F.habitName(worst.key, s).replaceFirstChar { it.uppercase(Locale.US) }} is the habit standing between you and a ${s.best + 1}+ streak." +
                    if (p != null && p.habit == worst.key) " Most misses land on ${p.weekday.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)}s." else "",
                style = T.small,
            )
        }
    }
}
