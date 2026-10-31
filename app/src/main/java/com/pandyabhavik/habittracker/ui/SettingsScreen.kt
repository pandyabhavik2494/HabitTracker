package com.pandyabhavik.habittracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandyabhavik.habittracker.BuildConfig
import com.pandyabhavik.habittracker.data.HomeState
import com.pandyabhavik.habittracker.health.HealthGateway
import com.pandyabhavik.habittracker.health.HealthStatus
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(s: HomeState, onBack: () -> Unit) {
    val a = LocalActions.current
    val vm = a.vm
    val st = s.settings
    val granted by vm.granted.collectAsStateWithLifecycle()
    val grantedChecked by vm.grantedChecked.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshGranted() }
    // On wide screens Settings is a rail destination like the others: same side margins, no back arrow.
    val wide = a.wide
    val side = if (wide) 20.dp else 16.dp
    val maxWidth = if (wide) Dp.Unspecified else 720.dp
    Column(
        Modifier
            .fillMaxSize()
            .testTag("settings"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Stays put while the panels scroll underneath.
        Row(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(horizontal = side)
                .padding(top = 12.dp, bottom = 8.dp)
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!wide) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { onBack() }.testTag("settings-back"),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.ink) }
                Spacer(Modifier.width(6.dp))
            }
            Text("Settings", style = T.title)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("settings-scroll"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Column(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(horizontal = side),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel(Modifier.fillMaxWidth()) {
                Caps("Goals")
                Stepper("Daily steps", F.n(st.stepsGoal), "steps-goal",
                    { vm.updateSettings { it.copy(stepsGoal = (it.stepsGoal - 500).coerceAtLeast(1_000)) } },
                    { vm.updateSettings { it.copy(stepsGoal = (it.stepsGoal + 500).coerceAtMost(50_000)) } })
                Stepper("Calorie limit", "${F.n(st.calorieGoal)} kcal", "calorie-goal",
                    { vm.updateSettings { it.copy(calorieGoal = (it.calorieGoal - 50).coerceAtLeast(800)) } },
                    { vm.updateSettings { it.copy(calorieGoal = (it.calorieGoal + 50).coerceAtMost(5_000)) } })
                Stepper("Kitchen locks at", F.minutesOfDay(st.kitchenLockMinute), "kitchen-lock",
                    { vm.updateSettings { it.copy(kitchenLockMinute = (it.kitchenLockMinute - 15).coerceAtLeast(17 * 60)) } },
                    { vm.updateSettings { it.copy(kitchenLockMinute = (it.kitchenLockMinute + 15).coerceAtMost(23 * 60 + 45)) } })
                Text("Hold to close the kitchen whenever you're done eating; if you forget, it locks itself at this time. Food logged after that still counts.", style = T.small)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Weight unit", style = T.label, modifier = Modifier.weight(1f))
                    Segmented(listOf("lb", "kg"), if (st.pounds) 0 else 1) { i -> vm.updateSettings { it.copy(pounds = i == 0) } }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Caps("Notifications")
                Text("Each one is skipped once the day is closed.", style = T.small)
                Toggle("Morning pledge · 7:00${F.NBSP}AM", st.notifyPledge, "n-pledge") { v -> vm.updateSettings { it.copy(notifyPledge = v) } }
                Toggle("Midday pace if behind · 12:30${F.NBSP}PM", st.notifyMidday, "n-midday") { v -> vm.updateSettings { it.copy(notifyMidday = v) } }
                Toggle("At risk · 9:00${F.NBSP}PM", st.notifyRisk, "n-risk") { v -> vm.updateSettings { it.copy(notifyRisk = v) } }
                Toggle("Last call · 11:00${F.NBSP}PM", st.notifyLastCall, "n-last") { v -> vm.updateSettings { it.copy(notifyLastCall = v) } }
                Toggle("Show progress in status bar", st.ongoing, "n-ongoing") { v ->
                    if (v) a.askNotifications()
                    vm.updateSettings { it.copy(ongoing = v) }
                }
                val wd = st.warnDay
                if (wd != null) {
                    Toggle("${wd.getDisplayName(TextStyle.FULL, Locale.US)} heads-up · 5${F.NBSP}PM", true, "n-warn") { v ->
                        if (!v) vm.updateSettings { it.copy(warnDay = null, warnHabit = null) }
                    }
                }
                if (!com.pandyabhavik.habittracker.work.Notifier.allowed(context)) {
                    Spacer(Modifier.height(6.dp))
                    Pill2("Allow notifications") { a.askNotifications() }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Caps("Feel")
                Spacer(Modifier.height(4.dp))
                Toggle("Haptics on closes and pack rips", st.haptics, "haptics") { v -> vm.updateSettings { it.copy(haptics = v) } }
            }
            Panel(Modifier.fillMaxWidth().testTag("health-panel")) {
                Caps("Health Connect")
                Spacer(Modifier.height(6.dp))
                val status = vm.healthStatus()
                when (status) {
                    HealthStatus.NOT_INSTALLED -> Text("Health Connect isn't installed.", style = T.body)
                    HealthStatus.NEEDS_UPDATE -> Text("Health Connect needs an update.", style = T.body)
                    HealthStatus.AVAILABLE -> {
                        // Null while the granted set is still being read: neutral, not a red "missing".
                        fun ok(p: String): Boolean? = if (grantedChecked) p in granted else null
                        Access("Steps (Google Health / Fit)", ok(HealthGateway.READ_STEPS))
                        Access("Food (MyFitnessPal)", ok(HealthGateway.READ_NUTRITION))
                        Access("Weight (VeSync scale)", ok(HealthGateway.READ_WEIGHT))
                        Access("Sync in the background", ok(HealthGateway.READ_IN_BACKGROUND))
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Same rule as Today's sync line.
                val failure = if (syncing) null else MainViewModel.syncFailure(st, status, granted, grantedChecked)
                Text(
                    when {
                        syncing -> "Syncing…"
                        failure != null -> failure
                        else -> "${F.ago(st.lastSync, System.currentTimeMillis())}."
                    },
                    style = T.small.copy(color = if (failure != null) C.risk else C.mute),
                    modifier = Modifier.testTag("settings-sync-status"),
                )
                Text(
                    "Syncs when you open the app, when you pull down on Today, and every 15 minutes in the background.",
                    style = T.small,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill2(if (status == HealthStatus.AVAILABLE) "Allow access" else "Get Health Connect", Modifier.weight(1f), primary = status != HealthStatus.AVAILABLE || HealthGateway.CORE.any { it !in granted }) { a.askHealth() }
                    Pill2("Sync now", Modifier.weight(1f).testTag("sync-now")) { vm.sync() }
                }
                if (status == HealthStatus.AVAILABLE) {
                    Spacer(Modifier.height(8.dp))
                    Pill2("Open Health Connect") { runCatching { context.startActivity(vm.repo.health.settingsIntent()) } }
                }
            }
            // Indented like the panels' content (panel padding is 16dp), and clear of the gesture bar / taskbar.
            Text(
                "Habit Tracker ${BuildConfig.VERSION_NAME}",
                style = T.small,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            )
        }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, tag: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.label, modifier = Modifier.weight(1f))
        Round(Icons.Rounded.Remove, "Less", "$tag-minus", onMinus)
        Text(value, style = T.label, maxLines = 1, modifier = Modifier.width(112.dp).padding(horizontal = 8.dp).testTag("$tag-value"), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Round(Icons.Rounded.Add, "More", "$tag-plus", onPlus)
    }
}

@Composable
private fun Round(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(C.s2)
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = C.ink, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun Toggle(label: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = T.body.copy(color = C.ink), modifier = Modifier.weight(1f).padding(end = 16.dp))
        // The whole row is the touch target, so the switch needn't pad itself out to 48dp.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked = on,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = C.bath,
                    checkedBorderColor = C.bath,
                    checkedIconColor = C.bath,
                    uncheckedThumbColor = Color(0xFFB8BDCB),
                    uncheckedTrackColor = C.s3,
                    uncheckedBorderColor = Color(0xFF8D93A6),
                    uncheckedIconColor = C.s3,
                ),
                modifier = Modifier.testTag(tag),
            )
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(C.s2)
            .border(1.dp, C.line, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (i == selected) C.bath else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .testTag("unit-$o"),
            ) { Text(o, style = T.label.copy(color = if (i == selected) Color(0xFF04141A) else C.mute)) }
        }
    }
}

@Composable
private fun Access(label: String, ok: Boolean?) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok == false) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle, null,
            tint = when (ok) {
                true -> C.steps
                false -> C.risk
                null -> C.dim
            },
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = T.body.copy(color = C.ink))
    }
}

@Composable
fun Pill2(text: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (primary) C.bath else C.s2)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.label.copy(color = if (primary) Color(0xFF04141A) else C.ink)) }
}
