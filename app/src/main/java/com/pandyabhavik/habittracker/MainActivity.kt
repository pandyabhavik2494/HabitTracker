package com.pandyabhavik.habittracker

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.PermissionController
import com.pandyabhavik.habittracker.ui.AppRoot
import com.pandyabhavik.habittracker.ui.HabitTheme
import com.pandyabhavik.habittracker.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    /** The tab a notification or widget asked for. */
    val requestedTab = mutableStateOf<String?>(null)

    private val healthPermissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        vm.refreshGranted()
        vm.sync()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab.value = intent?.getStringExtra(EXTRA_TAB)
        setContent {
            HabitTheme {
                AppRoot(
                    vm = vm,
                    requestedTab = requestedTab,
                    askHealth = { askHealth() },
                    askNotifications = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_TAB)?.let { requestedTab.value = it }
    }

    override fun onStart() {
        super.onStart()
        vm.refreshGranted()
        vm.sync()
    }

    fun askHealth() {
        val gateway = vm.repo.health
        when (gateway.status()) {
            com.pandyabhavik.habittracker.health.HealthStatus.AVAILABLE ->
                runCatching { healthPermissions.launch(MainViewModel.PERMISSIONS) }
                    .onFailure { startActivity(gateway.settingsIntent()) }
            else -> runCatching { startActivity(gateway.installIntent()) }
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
