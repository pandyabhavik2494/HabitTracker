package com.pandyabhavik.habittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandyabhavik.habittracker.ui.C
import com.pandyabhavik.habittracker.ui.HabitTheme
import com.pandyabhavik.habittracker.ui.T

/** Opened by Health Connect ("Why does this app need access?"). */
class PrivacyActivity : ComponentActivity() {
    private val sections = listOf(
        "What the app reads" to "From Health Connect, with your permission: steps (from Google Health / Fit, your watch or the phone) and weight (from your scale's app, such as VeSync).",
        "What the app writes" to "Nothing. It only reads.",
        "Where your data lives" to "On this phone only: in Health Connect and in the app's own storage. No account, no servers, no internet access, no analytics.",
        "Why it reads in the background" to "So the Seal, the widgets and the evening nudges stay current every 15 minutes without opening the app.",
        "Your control" to "Turn off any permission in Health Connect at any time, or uninstall the app to remove its own copy.",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so the bar icons are always light, whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            HabitTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(C.bg)
                        .systemBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                        .widthIn(max = 640.dp),
                ) {
                    Text("Privacy", style = T.title)
                    Spacer(Modifier.height(16.dp))
                    sections.forEach { (title, body) ->
                        Text(title, style = T.h2)
                        Spacer(Modifier.height(4.dp))
                        Text(body, style = T.body)
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
