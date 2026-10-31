package com.pandyabhavik.habittracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandyabhavik.habittracker.health.HealthGateway
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandyabhavik.habittracker.core.CalorieState
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.core.Rules
import com.pandyabhavik.habittracker.data.HomeState
import java.util.Locale

/** Detail and actions for one habit: tap a row or tile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitSheet(habit: Habit, s: HomeState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = C.s1,
        contentColor = C.ink,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        sheetMaxWidth = 640.dp,
        modifier = Modifier.testTag("sheet-${habit.name.lowercase()}"),
    ) {
        LightSheetBars()
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitIcon(habit, 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(F.habitName(habit, s), style = T.title, modifier = Modifier.weight(1f))
                if (habit in s.closed) CheckDot(C.of(habit))
            }
            when (habit) {
                Habit.STEPS -> StepsSheet(s)
                Habit.CALORIES -> CaloriesSheet(s, onDismiss)
                Habit.WEIGH -> WeighSheet(s, onDismiss)
                Habit.BATH -> BathSheet(s, onDismiss)
            }
        }
    }
}

/**
 * Call first inside any ModalBottomSheet's content. The sheet lives in its own dialog window, which
 * picks dark bar icons from the system theme; the app is always dark, so keep the clock and icons
 * light. Also stops the sheet window from panning the screen behind it when the keyboard opens:
 * the sheet pads itself for the IME through its content insets.
 */
@Composable
fun LightSheetBars() {
    val view = LocalView.current
    SideEffect {
        val window = (view as? DialogWindowProvider)?.window
            ?: (view.parent as? DialogWindowProvider)?.window
            ?: return@SideEffect
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}

@Composable
private fun StepsSheet(s: HomeState) {
    val d = s.today
    val left = (s.goals.steps - d.steps).coerceAtLeast(0)
    Text("${F.n(d.steps)} steps", style = T.hero.copy(fontSize = 40.sp))
    Text(
        if (left == 0) "Closed. Every step from here is a bonus." else "${F.n(left)} to go. About ${Rules.walkMinutes(left)} minutes of walking.",
        style = T.body,
    )
    HourBars(d.hourly, s.now.hour, Modifier.fillMaxWidth().height(80.dp))
    Text(
        "From Health Connect: Google Health (Fit), your watch and the phone, de-duplicated by Health Connect's own source order.",
        style = T.small,
    )
    val context = LocalContext.current
    fitnessIntent(context)?.let { intent ->
        if (left > 0) SheetButton("Start walk", C.steps, Color(0xFF06140C)) { runCatching { context.startActivity(intent) } }
    }
}

@Composable
private fun CaloriesSheet(s: HomeState, close: () -> Unit) {
    val a = LocalActions.current
    val d = s.today
    Text("${F.n(d.calories)} kcal", style = T.hero.copy(fontSize = 40.sp, color = if (s.calorieState == CalorieState.OVER) C.risk else C.ink))
    Text(
        when (s.calorieState) {
            CalorieState.NOT_LOGGED -> "Nothing logged today. Log in MyFitnessPal; it syncs through Health Connect."
            CalorieState.UNDER -> "${F.n(s.goals.calories - d.calories)} kcal left. Done eating? Close the kitchen to seal the quarter, or it locks itself at ${F.minutesOfDay(s.goals.kitchenLockMinute)}."
            CalorieState.CLOSED -> "Kitchen closed with ${F.n(s.goals.calories - d.calories)} kcal to spare. Food logged later still counts against you."
            CalorieState.OVER -> "${F.n(d.calories - s.goals.calories)} kcal over. This quarter can't close today; swaps can still save the streak tonight."
        },
        style = T.body,
    )
    F.mealLine(d).forEach { (name, kcal) ->
        Row(Modifier.fillMaxWidth()) {
            Text(name, style = T.label, modifier = Modifier.weight(1f))
            Text("${F.n(kcal)} kcal", style = T.label.copy(color = C.mute))
        }
    }
    when {
        s.calorieState == CalorieState.UNDER ->
            HoldButton(
                "Hold to close the kitchen",
                modifier = habitHold(C.calories),
                haptics = s.settings.haptics,
                brush = habitFill(C.calories),
            ) {
                a.vm.closeKitchen()
                close()
            }
        d.kitchenClosedAt != null && !Rules.kitchenLocked(d.copy(kitchenClosedAt = null), s.goals, s.now) &&
            s.packs.none { it.date == d.date.toString() && it.openedAt != null } ->
            SheetButton("Reopen the kitchen", C.s2, C.ink) {
                a.vm.reopenKitchen()
                close()
            }
    }
    val context = LocalContext.current
    context.packageManager.getLaunchIntentForPackage("com.myfitnesspal.android")?.let { intent ->
        SheetButton("Open MyFitnessPal", C.s2, C.ink) { runCatching { context.startActivity(intent) } }
    }
    // The setup hint only when meals aren't arriving: no nutrition access, or nothing logged for a few days.
    val granted by a.vm.granted.collectAsStateWithLifecycle()
    val grantedChecked by a.vm.grantedChecked.collectAsStateWithLifecycle()
    val noAccess = grantedChecked && HealthGateway.READ_NUTRITION !in granted
    val recentMeals = (0L..3L).any { back -> (s.days[d.date.minusDays(back)]?.meals ?: 0) > 0 } || d.meals > 0
    if (noAccess || !recentMeals) {
        Text("In MyFitnessPal, turn on Settings › Sharing & Privacy › Health Connect so meals reach this app.", style = T.small)
    }
}

@Composable
private fun WeighSheet(s: HomeState, close: () -> Unit) {
    val a = LocalActions.current
    val d = s.today
    d.weightKg?.let {
        Text(F.weight(it, s.settings.pounds), style = T.hero.copy(fontSize = 40.sp))
        Text(
            listOfNotNull(
                d.weighedAt?.let { t -> "${if (d.weightManual) "Typed in" else "From your scale"} at ${F.time(t, s.zone)}" },
                F.weightTrend(s, it),
            ).joinToString(" · "),
            style = T.body,
        )
    } ?: Text("Step on your scale. VeSync sends the weigh-in to Health Connect and this quarter closes on its own.", style = T.body)
    var text by remember { mutableStateOf("") }
    val unit = F.unit(s.settings.pounds)
    val logged = d.weightKg
    if (logged != null) {
        // Saving now replaces today's weigh-in, so say what's there.
        Text(
            "Logged ${F.weight(logged, s.settings.pounds)}${d.weighedAt?.let { t -> " at ${F.time(t, s.zone)}" } ?: ""}",
            style = T.label,
            modifier = Modifier.testTag("weight-logged"),
        )
        Caps("Correct it")
    } else {
        Caps("Or type it in")
    }
    OutlinedTextField(
        value = text,
        onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
        label = { Text("Weight ($unit)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.weigh,
            unfocusedBorderColor = C.line,
            focusedLabelColor = C.weigh,
            cursorColor = C.weigh,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weight-input"),
    )
    val value = text.replace(',', '.').toDoubleOrNull()
    val kg = value?.let { if (s.settings.pounds) it / 2.2046226 else it }
    val valid = kg != null && kg in 25.0..350.0
    SheetButton(
        if (valid) String.format(Locale.US, "Save %.1f %s", value, unit) else "Save weigh-in",
        if (valid) C.weigh else C.s2,
        if (valid) Color(0xFF120B24) else C.dim,
        Modifier.testTag("save-weight"),
    ) {
        if (kg != null && valid) {
            a.vm.logWeight(kg)
            close()
        }
    }
}

@Composable
private fun BathSheet(s: HomeState, close: () -> Unit) {
    val a = LocalActions.current
    val d = s.today
    if (d.bathAt == null) {
        Text("Not logged yet today.", style = T.body)
        HoldButton(
            "Hold to log bath",
            modifier = habitHold(C.bath),
            haptics = s.settings.haptics,
            brush = habitFill(C.bath),
        ) {
            a.vm.logBath()
            close()
        }
        Text("Shortcut: long-press the Bath row, or use the Bath widget.", style = T.small)
    } else {
        Text("Logged at ${F.time(d.bathAt!!, s.zone)}.", style = T.h2)
        val opened = s.packs.any { it.date == d.date.toString() && it.openedAt != null }
        if (!(s.perfect && opened)) {
            OutlinedSheetButton("Remove today's bath", C.risk, Modifier.testTag("undo-bath")) {
                a.vm.undoBath()
                close()
            }
        }
    }
}

/** Tints a [HoldButton] with its habit's colour so it reads as the sheet's main action, not a disabled pill. */
private fun habitHold(color: Color): Modifier = Modifier
    .clip(CircleShape)
    .border(1.5.dp, color, CircleShape)
    .drawWithContent {
        drawContent()
        drawRect(color.copy(alpha = 0.22f))
    }

private fun habitFill(color: Color): Brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.9f)))

/** A secondary, destructive-looking action: outlined, coloured text, no fill. */
@Composable
private fun OutlinedSheetButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .border(1.dp, color.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.label.copy(color = color)) }
}

@Composable
fun SheetButton(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.label.copy(color = fg)) }
}
