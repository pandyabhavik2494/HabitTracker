package com.pandyabhavik.habittracker.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pandyabhavik.habittracker.Graph
import com.pandyabhavik.habittracker.data.HabitEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The background sync: every 15 minutes, the shortest period Android gives background work. Health
 * Connect has no push, so this is as fresh as the data can be without the app open.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val events = try {
            Graph.repo(applicationContext).sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SyncWorker", "Sync failed", e)
            emptyList()
        }
        Notifier.announce(applicationContext, events)
        return Result.success()
    }
}

/** The day's nudges at fixed times. Each run books the next day's run of the same slot. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val slot = runCatching { Slot.valueOf(inputData.getString(KEY_SLOT) ?: "") }.getOrNull() ?: return Result.success()
        try {
            val repo = Graph.repo(applicationContext)
            val events = repo.sync()
            Notifier.announce(applicationContext, events)
            val state = Graph.snapshot(applicationContext)
            // Doze can hold a job for hours; a nudge that arrives far too late is worse than none.
            val minute = state.now.hour * 60 + state.now.minute
            if (minute - slot.minute in 0..MAX_LATE_MINUTES) Notifier.nudge(applicationContext, slot, state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ReminderWorker", "Reminder failed", e)
        }
        Schedules.scheduleSlot(applicationContext, slot, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        const val KEY_SLOT = "slot"
        const val MAX_LATE_MINUTES = 45
    }
}

enum class Slot(val minute: Int) {
    PLEDGE(7 * 60),
    WARN(17 * 60),
    MIDDAY(12 * 60 + 30),
    RISK(21 * 60),
    LAST_CALL(23 * 60),
}

object Schedules {
    private const val SYNC = "sync"

    fun scheduleAll(context: Context) {
        scheduleSync(context)
        Slot.entries.forEach { scheduleSlot(context, it, ExistingWorkPolicy.KEEP) }
    }

    fun scheduleSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.NONE)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SYNC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun syncSoon(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync-once", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SyncWorker>().build(),
        )
    }

    fun scheduleSlot(context: Context, slot: Slot, policy: ExistingWorkPolicy) {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = now.toLocalDate().atTime(slot.minute / 60, slot.minute % 60)
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        val delay = next.atZone(zone).toInstant().toEpochMilli() - System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_SLOT to slot.name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("slot-${slot.name}", policy, request)
    }
}

/** Buttons on notifications: log a bath or close the kitchen without opening the app. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val repo = Graph.repo(context)
                val events = when (intent.action) {
                    ACTION_BATH -> repo.logBath()
                    ACTION_KITCHEN -> repo.closeKitchen()
                    else -> emptyList()
                }
                Notifier.cancelNudge(context)
                Notifier.announce(context, events, force = true)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_BATH = "com.pandyabhavik.habittracker.LOG_BATH"
        const val ACTION_KITCHEN = "com.pandyabhavik.habittracker.CLOSE_KITCHEN"
    }
}

/** Books the background sync and the nudges again after a restart or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (Graph.repo(context).settings.current().onboarded) Schedules.scheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

fun appInForeground(): Boolean =
    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

fun List<HabitEvent>.hasPerfectDay() = any { it is HabitEvent.PerfectDay }
