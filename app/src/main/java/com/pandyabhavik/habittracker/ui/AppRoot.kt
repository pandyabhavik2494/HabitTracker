package com.pandyabhavik.habittracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandyabhavik.habittracker.data.HomeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector, val key: String) {
    TODAY("Today", Icons.Rounded.DonutLarge, "today"),
    JOURNEY("Journey", Icons.Rounded.Insights, "journey"),
    STICKERS("Stickers", Icons.Rounded.AutoAwesome, "stickers"),
}

/** Things any screen can do: open a pack, show a message, open settings. */
class AppActions(
    val vm: MainViewModel,
    val snackbar: SnackbarHostState,
    val openPack: (Long) -> Unit,
    val openSettings: () -> Unit,
    val openTab: (Tab) -> Unit,
    val askHealth: () -> Unit,
    val askNotifications: () -> Unit,
    val wide: Boolean,
    /** Main's scope: outlives tab switches, so a snackbar and its action survive the screen that showed it. */
    private val scope: CoroutineScope,
    /** True while the pack overlay is up: messages would only sit on top of the reveal. */
    private val packOpen: () -> Boolean = { false },
) {
    /** Shows a message; [onAction] runs if the user taps [actionLabel] before it times out. */
    fun showMessage(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            if (packOpen()) return@launch
            val r = snackbar.showSnackbar(
                message,
                actionLabel = actionLabel,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

val LocalActions = staticCompositionLocalOf<AppActions> { error("No actions") }

@Composable
fun AppRoot(
    vm: MainViewModel,
    requestedTab: MutableState<String?>,
    askHealth: () -> Unit,
    askNotifications: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state
    Box(
        Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        if (s == null) return@Box
        if (!s.settings.onboarded) {
            Onboarding(vm, s, askHealth, askNotifications)
            return@Box
        }
        Main(vm, s, requestedTab, askHealth, askNotifications)
    }
}

@Composable
private fun Main(
    vm: MainViewModel,
    s: HomeState,
    requestedTab: MutableState<String?>,
    askHealth: () -> Unit,
    askNotifications: () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var packId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(requestedTab.value) {
        val r = requestedTab.value ?: return@LaunchedEffect
        Tab.entries.firstOrNull { it.key == r }?.let { tab = it; settingsOpen = false }
        requestedTab.value = null
    }

    val actions = AppActions(
        vm = vm,
        snackbar = snackbar,
        openPack = { packId = it },
        openSettings = { settingsOpen = true },
        openTab = { tab = it; settingsOpen = false },
        askHealth = askHealth,
        askNotifications = askNotifications,
        wide = wide,
        scope = scope,
        packOpen = { packId != null },
    )

    // Opening a pack clears whatever message was showing ("Bath logged" over the reveal).
    LaunchedEffect(packId) {
        if (packId != null) snackbar.currentSnackbarData?.dismiss()
    }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = !settingsOpen && tab != Tab.TODAY && packId == null) { tab = Tab.TODAY }

    CompositionLocalProvider(LocalActions provides actions) {
        Box(Modifier.fillMaxSize()) {
            if (wide) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    Rail(tab, settingsOpen) { t -> if (t == null) settingsOpen = true else { tab = t; settingsOpen = false } }
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(C.line),
                    )
                    Box(Modifier.weight(1f)) { Screen(tab, settingsOpen, s) { settingsOpen = false } }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .statusBarsPadding(),
                    ) { Screen(tab, settingsOpen, s) { settingsOpen = false } }
                    if (!settingsOpen) BottomBar(tab) { tab = it }
                }
            }
            // The last pack shown, so the overlay still has something to draw while it fades out.
            var shownPackId by remember { mutableStateOf<Long?>(null) }
            SideEffect { packId?.let { shownPackId = it } }
            AnimatedVisibility(packId != null, enter = fadeIn(), exit = fadeOut()) {
                val id = packId ?: shownPackId
                if (id != null) PackFlow(id, s, onClose = { packId = null })
            }
            // After the pack overlay, so messages draw on top of it. Centred over the content pane
            // (right of the rail) on wide screens, above the bottom bar on narrow ones.
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (wide) {
                            Modifier
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(start = if (packId == null) 77.dp else 0.dp)
                        } else {
                            Modifier
                                .navigationBarsPadding()
                                .padding(bottom = if (settingsOpen || packId != null) 0.dp else 64.dp)
                        },
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                SnackbarHost(
                    snackbar,
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) { data ->
                    Snackbar(
                        data,
                        shape = RoundedCornerShape(14.dp),
                        containerColor = Color(0xFF1A1C24),
                        contentColor = C.ink,
                        actionColor = C.bath,
                        actionContentColor = C.bath,
                        dismissActionContentColor = C.mute,
                    )
                }
            }
        }
    }

    // A Perfect Day reached while the app is open goes straight to the pack.
    var celebrated by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(s.perfect, s.unopened.size) {
        val first = s.unopened.firstOrNull { it.date == s.now.toLocalDate().toString() }
        val key = s.now.toLocalDate().toString()
        if (s.perfect && first != null && celebrated != key && packId == null) {
            celebrated = key
            packId = first.id
        }
    }
    // Sync errors are shown in Today's sync line and in Settings, not as a snackbar.
}

@Composable
private fun Screen(tab: Tab, settingsOpen: Boolean, s: HomeState, closeSettings: () -> Unit) {
    AnimatedContent(
        targetState = if (settingsOpen) "settings" else tab.key,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen",
    ) { key ->
        when (key) {
            "settings" -> SettingsScreen(s, closeSettings)
            "journey" -> JourneyScreen(s)
            "stickers" -> AlbumScreen(s)
            else -> TodayScreen(s)
        }
    }
}

@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.s1)
            .navigationBarsPadding()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val on = t == tab
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onTab(t) }
                    .padding(vertical = 8.dp)
                    .testTag("tab-${t.key}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, t.label, tint = if (on) C.ink else C.dim, modifier = Modifier.size(24.dp))
                Text(t.label, style = T.small.copy(color = if (on) C.ink else C.dim, fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun Rail(tab: Tab, settingsOpen: Boolean, onTab: (Tab?) -> Unit) {
    Column(
        Modifier
            .width(76.dp)
            .fillMaxHeight()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Tab.entries.forEach { t ->
            val on = t == tab && !settingsOpen
            Column(
                Modifier
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTab(t) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("tab-${t.key}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) C.s3 else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(t.icon, t.label, tint = if (on) C.ink else C.dim, modifier = Modifier.size(22.dp))
                }
                Text(t.label, style = T.small.copy(color = if (on) C.ink else C.dim, fontSize = 11.sp))
            }
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onTab(null) }
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .testTag("tab-settings"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (settingsOpen) C.s3 else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Rounded.Settings, null, tint = if (settingsOpen) C.ink else C.dim, modifier = Modifier.size(22.dp))
            }
            Text("Settings", style = T.small.copy(color = if (settingsOpen) C.ink else C.dim, fontSize = 11.sp))
        }
    }
}
