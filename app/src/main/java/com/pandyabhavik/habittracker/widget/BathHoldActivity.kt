package com.pandyabhavik.habittracker.widget

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pandyabhavik.habittracker.Graph
import com.pandyabhavik.habittracker.data.HomeState
import com.pandyabhavik.habittracker.ui.C
import com.pandyabhavik.habittracker.ui.HabitTheme
import com.pandyabhavik.habittracker.ui.HoldButton
import com.pandyabhavik.habittracker.ui.T
import com.pandyabhavik.habittracker.work.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Opened from the Bath widget. Widgets can't detect a long press, so the press-and-hold happens here:
 * hold for one second to log today's bath. If it's already logged, says when.
 */
class BathHoldActivity : ComponentActivity() {
    private var state by mutableStateOf<HomeState?>(null)
    private var loaded by mutableStateOf(false)
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            state = try {
                Graph.snapshot(applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load today", e)
                null
            }
            loaded = true
        }
        setContent {
            HabitTheme {
                Sheet(state, loaded, onLog = ::log, onClose = ::finish)
            }
        }
    }

    private fun log() {
        if (busy) return
        busy = true
        val app = applicationContext
        lifecycleScope.launch {
            try {
                val events = Graph.repo(app).logBath()
                // The app isn't on screen to celebrate, so a Perfect Day gets its notification.
                Notifier.announce(app, events, force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't log the bath", e)
            }
            finish()
        }
    }

    companion object {
        private const val TAG = "BathHoldActivity"
    }
}

@Composable
private fun Sheet(state: HomeState?, loaded: Boolean, onLog: () -> Unit, onClose: () -> Unit) {
    val outside = remember { MutableInteractionSource() }
    val inside = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = outside, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) return@Box
        val time = state?.let { bathTime(it) }
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(C.s1)
                .border(1.dp, C.line, shape)
                // Swallows taps on the card so they don't close it.
                .clickable(interactionSource = inside, indication = null, onClick = {})
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (time != null) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(C.bath),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF04141A), modifier = Modifier.size(30.dp))
                }
                Text("Bath logged at $time", style = T.h2, textAlign = TextAlign.Center)
                Text("Today's bath quarter is closed.", style = T.body, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onClose) { Text("Close", style = T.label.copy(color = C.bath)) }
            } else {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(C.bath.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Bathtub, contentDescription = null, tint = C.bath, modifier = Modifier.size(30.dp))
                }
                Text("Log today's bath?", style = T.h2, textAlign = TextAlign.Center)
                Text("Press and hold for one second.", style = T.body, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                HoldButton(
                    text = "Hold to log bath",
                    haptics = state?.settings?.haptics ?: true,
                    onDone = onLog,
                )
                TextButton(onClick = onClose) { Text("Cancel", style = T.label.copy(color = C.mute)) }
            }
        }
    }
}
