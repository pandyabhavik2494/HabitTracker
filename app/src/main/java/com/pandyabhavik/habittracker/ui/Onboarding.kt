package com.pandyabhavik.habittracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.data.HomeState
import com.pandyabhavik.habittracker.health.HealthGateway
import com.pandyabhavik.habittracker.work.Notifier
import kotlinx.coroutines.delay

@Composable
fun Onboarding(vm: MainViewModel, s: HomeState, askHealth: () -> Unit, askNotifications: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag("onboarding"),
        contentAlignment = Alignment.TopCenter,
    ) {
        // At least a screen tall, so short pages sit in the middle; taller ones still scroll.
        val screenHeight = maxHeight
        AnimatedContent(page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding") { p ->
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = screenHeight)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (p) {
                    0 -> Welcome(screenHeight) { page = 1 }
                    1 -> Connect(vm, askHealth) { page = 2 }
                    2 -> Nudges(askNotifications) { page = 3 }
                    else -> FirstPledge(s) { vm.finishOnboarding() }
                }
            }
        }
    }
}

@Composable
private fun Welcome(screenHeight: androidx.compose.ui.unit.Dp, next: () -> Unit) {
    // Short screens (the cover display) shrink the ring and the gaps so "Get started" fits without scrolling.
    val compact = screenHeight < 760.dp
    val ring = minOf(220.dp, screenHeight * 0.26f)
    // Opens on the full, sealed ring (the goal), then shows it filling quarter by quarter.
    var demo by rememberSaveable { mutableIntStateOf(4) }
    LaunchedEffect(Unit) {
        delay(1500)
        demo = 0
        while (true) {
            delay(900)
            demo = (demo + 1) % 6
        }
    }
    val prog = Habit.entries.mapIndexed { i, h -> h to if (i < demo) 1f else 0f }.toMap()
    Spacer(Modifier.height(if (compact) 0.dp else 24.dp))
    Seal(prog, ring) {
        if (demo >= 4) {
            Icon(Icons.Rounded.Check, contentDescription = "Sealed", tint = C.ink, modifier = Modifier.size(if (compact) 48.dp else 56.dp))
        } else {
            Text("$demo/4", style = T.hero.copy(fontSize = if (compact) 40.sp else 48.sp))
        }
    }
    Spacer(Modifier.height(if (compact) 14.dp else 24.dp))
    Text("Close all four.\nEvery day.", style = T.hero.copy(fontSize = if (compact) 32.sp else 40.sp), textAlign = TextAlign.Center)
    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
    Text(
        "Each habit is a quarter of the Seal. Close all four for a Perfect Day, a sticker pack, and one more day on the streak.",
        style = T.body,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
    Habit.entries.forEach { h ->
        Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 3.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
            HabitIcon(h, if (compact) 30.dp else 34.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                when (h) {
                    Habit.STEPS -> "10k steps · from Google Health"
                    Habit.CALORIES -> "Under 1,500 kcal · from MyFitnessPal"
                    Habit.WEIGH -> "Weigh-in · from your VeSync scale"
                    Habit.BATH -> "Bath · a long-press here"
                },
                style = T.label,
            )
        }
    }
    Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
    Primary("Get started", "onboarding-next") { next() }
}

@Composable
private fun Connect(vm: MainViewModel, askHealth: () -> Unit, next: () -> Unit) {
    val granted by vm.granted.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshGranted() }
    Spacer(Modifier.height(24.dp))
    Text("Connect your data", style = T.big, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(
        "Everything comes through Health Connect, on the phone. Nothing leaves it.",
        style = T.body,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(18.dp))
    Panel(Modifier.fillMaxWidth()) {
        Check("Steps", "Google Health (Fit) and your watch already write steps to Health Connect.", HealthGateway.READ_STEPS in granted)
        Check("Food", "In MyFitnessPal: Settings › Sharing & Privacy › Health Connect, turn it on.", HealthGateway.READ_NUTRITION in granted)
        Check("Weight", "In VeSync: Me › Third-party services › Health Connect, turn it on.", HealthGateway.READ_WEIGHT in granted)
        Check("In the background", "So the Seal and widgets update every 15 minutes with the app closed.", HealthGateway.READ_IN_BACKGROUND in granted)
    }
    Spacer(Modifier.height(20.dp))
    val all = HealthGateway.CORE.all { it in granted }
    Primary(if (all) "Continue" else "Allow Health Connect", "onboarding-health") { if (all) next() else askHealth() }
    Spacer(Modifier.height(8.dp))
    if (!all) Secondary("Skip for now", "onboarding-skip") { next() }
}

@Composable
private fun Check(title: String, text: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
            tint = if (ok) C.steps else C.dim, modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = T.label)
            Text(text, style = T.small)
        }
    }
}

@Composable
private fun Nudges(askNotifications: () -> Unit, next: () -> Unit) {
    val context = LocalContext.current
    Spacer(Modifier.height(24.dp))
    Text("Four nudges, at most", style = T.big, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text("Each is timed to when you can still act, and skipped once the day is closed.", style = T.body, textAlign = TextAlign.Center)
    Spacer(Modifier.height(18.dp))
    Panel(Modifier.fillMaxWidth()) {
        listOf(
            "7:00 AM" to "The pledge. Weigh in first: a quarter done before coffee.",
            "12:30 PM" to "Only if you're behind on steps.",
            "9:00 PM" to "At risk: what's left, and the countdown to midnight.",
            "11:00 PM" to "Last call, with a Log bath button right on it.",
        ).forEach { (time, text) ->
            Row(Modifier.padding(vertical = 6.dp)) {
                Text(time, style = T.label.copy(color = C.bath), modifier = Modifier.width(82.dp))
                Text(text, style = T.body.copy(color = C.ink))
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    val allowed = Notifier.allowed(context)
    Primary(if (allowed) "Continue" else "Allow notifications", "onboarding-notifications") {
        if (allowed) next() else {
            askNotifications()
            next()
        }
    }
    Spacer(Modifier.height(8.dp))
    if (!allowed) Secondary("Not now", "onboarding-skip-notifications") { next() }
}

@Composable
private fun FirstPledge(s: HomeState, done: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Text("DAY 1", style = T.caps.copy(color = C.ink))
    Spacer(Modifier.height(10.dp))
    Text("“I close all four today.”", style = T.big, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text("Saying it, even with a thumb, makes you far more likely to follow through.", style = T.body, textAlign = TextAlign.Center)
    Spacer(Modifier.height(28.dp))
    HoldButton("Hold to pledge", haptics = s.settings.haptics) { done() }
}

@Composable
private fun Primary(text: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(CircleShape)
            .background(C.ink)
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.label.copy(color = Color(0xFF0A0B10))) }
}

@Composable
private fun Secondary(text: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.label.copy(color = C.mute)) }
}
