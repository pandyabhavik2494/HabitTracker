package com.pandyabhavik.habittracker.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshGranted() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .testTag("settings"),
    ) {
        Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { onBack() }.testTag("settings-back"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.ink) }
            Spacer(Modifier.width(6.dp))
            Text("Settings", style = T.title)
        }
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Text("Tap Close kitchen whenever you're done eating; if you forget, it locks itself at this time. Food logged after that still counts.", style = T.small)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Weight unit", style = T.label, modifier = Modifier.weight(1f))
                    Segmented(listOf("lb", "kg"), if (st.pounds) 0 else 1) { i -> vm.updateSettings { it.copy(pounds = i == 0) } }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Caps("Notifications")
                Text("Each one is skipped once the day is closed.", style = T.small)
                Toggle("Morning pledge · 7:00 AM", st.notifyPledge, "n-pledge") { v -> vm.updateSettings { it.copy(notifyPledge = v) } }
                Toggle("Midday pace, only if behind · 12:30 PM", st.notifyMidday, "n-midday") { v -> vm.updateSettings { it.copy(notifyMidday = v) } }
                Toggle("At risk · 9:00 PM", st.notifyRisk, "n-risk") { v -> vm.updateSettings { it.copy(notifyRisk = v) } }
                Toggle("Last call · 11:00 PM", st.notifyLastCall, "n-last") { v -> vm.updateSettings { it.copy(notifyLastCall = v) } }
                Toggle("The four quarters in the status bar all day", st.ongoing, "n-ongoing") { v ->
                    if (v) a.askNotifications()
                    vm.updateSettings { it.copy(ongoing = v) }
                }
                val wd = st.warnDay
                if (wd != null) {
                    Toggle("5 PM heads-up on ${wd.getDisplayName(TextStyle.FULL, Locale.US)}s", true, "n-warn") { v ->
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
                Toggle("Haptics on closes, pledges and pack rips", st.haptics, "haptics") { v -> vm.updateSettings { it.copy(haptics = v) } }
            }
            Panel(Modifier.fillMaxWidth().testTag("health-panel")) {
                Caps("Health Connect")
                Spacer(Modifier.height(6.dp))
                val status = vm.healthStatus()
                when (status) {
                    HealthStatus.NOT_INSTALLED -> Text("Health Connect isn't installed.", style = T.body)
                    HealthStatus.NEEDS_UPDATE -> Text("Health Connect needs an update.", style = T.body)
                    HealthStatus.AVAILABLE -> {
                        Access("Steps (Google Health / Fit)", HealthGateway.READ_STEPS in granted)
                        Access("Food (MyFitnessPal)", HealthGateway.READ_NUTRITION in granted)
                        Access("Weight (VeSync scale)", HealthGateway.READ_WEIGHT in granted)
                        Access("Sync in the background", HealthGateway.READ_IN_BACKGROUND in granted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${if (syncing) "Syncing…" else F.ago(st.lastSync, System.currentTimeMillis())}. Syncs when you open the app, when you pull down on Today, and every 15 minutes in the background.",
                    style = T.small,
                )
                st.lastSyncError?.let { Text(it, style = T.small.copy(color = C.risk)) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill2(if (status == HealthStatus.AVAILABLE) "Allow access" else "Get Health Connect", Modifier.weight(1f)) { a.askHealth() }
                    Pill2("Sync now", Modifier.weight(1f).testTag("sync-now")) { vm.sync() }
                }
                if (status == HealthStatus.AVAILABLE) {
                    Spacer(Modifier.height(8.dp))
                    Pill2("Open Health Connect") { runCatching { context.startActivity(vm.repo.health.settingsIntent()) } }
                }
            }
            Text("Habit Tracker ${BuildConfig.VERSION_NAME}", style = T.small, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, tag: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.label, modifier = Modifier.weight(1f))
        Round(Icons.Rounded.Remove, "Less", "$tag-minus", onMinus)
        Text(value, style = T.label, modifier = Modifier.widthIn(min = 92.dp).padding(horizontal = 8.dp).testTag("$tag-value"), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = T.body.copy(color = C.ink), modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.bath, checkedThumbColor = Color(0xFF04141A)),
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(C.s2)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (i == selected) C.s3 else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("unit-$o"),
            ) { Text(o, style = T.label.copy(color = if (i == selected) C.ink else C.mute)) }
        }
    }
}

@Composable
private fun Access(label: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
            tint = if (ok) C.steps else C.risk, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = T.body.copy(color = C.ink))
    }
}

@Composable
fun Pill2(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(C.s2)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.label) }
}
