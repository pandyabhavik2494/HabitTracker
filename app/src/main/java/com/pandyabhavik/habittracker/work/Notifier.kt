package com.pandyabhavik.habittracker.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pandyabhavik.habittracker.MainActivity
import com.pandyabhavik.habittracker.R
import com.pandyabhavik.habittracker.core.CalorieState
import com.pandyabhavik.habittracker.core.Catalog
import com.pandyabhavik.habittracker.core.Coach
import com.pandyabhavik.habittracker.core.Habit
import com.pandyabhavik.habittracker.data.HabitEvent
import com.pandyabhavik.habittracker.data.HomeState
import java.time.format.TextStyle
import java.util.Locale

object Notifier {
    const val NUDGE_CHANNEL = "nudges"
    const val REWARD_CHANNEL = "rewards"
    const val ONGOING_CHANNEL = "ongoing"
    const val NUDGE_ID = 10
    const val REWARD_ID = 11
    const val ONGOING_ID = 12

    fun createChannels(context: Context) {
        val m = context.getSystemService(NotificationManager::class.java)
        m.createNotificationChannels(
            listOf(
                NotificationChannel(NUDGE_CHANNEL, "Daily nudges", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Morning pledge, midday pace, 9 PM at-risk and 11 PM last call. Skipped once the day is closed."
                },
                NotificationChannel(REWARD_CHANNEL, "Perfect Days and stickers", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When a Perfect Day earns a sticker pack"
                },
                NotificationChannel(ONGOING_CHANNEL, "Today's rings", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The four rings in the status bar all day (optional)"
                    setShowBadge(false)
                },
            ),
        )
    }

    fun allowed(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun openApp(context: Context, tab: String? = null): PendingIntent = PendingIntent.getActivity(
        context, tab.hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (tab != null) putExtra(MainActivity.EXTRA_TAB, tab) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, ActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    /** What a nudge at [slot] says, or null when it should stay quiet. Public for tests. */
    fun nudgeText(slot: Slot, s: HomeState): Pair<String, String>? {
        if (s.perfect) return null
        // Over the calorie limit, today can't close: no evening pushes that pretend it still can.
        if (s.calorieState == CalorieState.OVER && slot in setOf(Slot.WARN, Slot.RISK, Slot.LAST_CALL)) return null
        val open = Habit.entries.filter { it !in s.closed }
        val n = Coach::n
        return when (slot) {
            Slot.PLEDGE -> if (s.today.pledgedAt != null || !s.settings.notifyPledge) null else
                "Day ${s.dayNumber} starts now." to
                    if (Habit.WEIGH !in s.closed) "Weigh in first and you're one ring closed before coffee."
                    else "Hold to pledge: \"I close all four today.\""
            Slot.MIDDAY -> {
                val expected = Coach.expectedSteps(s.goals, s.now)
                if (!s.settings.notifyMidday || Habit.STEPS in s.closed || s.today.steps >= expected - 500) null else
                    "${n(s.today.steps)} steps." to "You should be near ${n(expected)} by now. A 15-minute lunch walk catches you up."
            }
            Slot.WARN -> {
                val wd = s.settings.warnDay
                val h = s.settings.warnHabit
                if (wd == null || h == null || s.now.dayOfWeek != wd || h in s.closed) null else
                    "It's ${wd.getDisplayName(TextStyle.FULL, Locale.US)}." to when (h) {
                        Habit.CALORIES -> "Your calorie trap day. ${n(s.goals.calories - s.today.calories)} kcal left: plan dinner now."
                        Habit.STEPS -> "Your steps trap day. ${n(s.goals.steps - s.today.steps)} to go: walk before dinner."
                        Habit.WEIGH -> "The day you forget the scale. 30 seconds closes it."
                        Habit.BATH -> "The day bath slips. Get it in before dinner."
                    }
            }
            Slot.RISK -> {
                val streak = s.dayNumber - 1
                when {
                    !s.settings.notifyRisk || open.isEmpty() -> null
                    streak > 0 -> "${Coach.leftText(open.first(), s.today, s.goals, s.now)}${if (open.size > 1) " and ${open.size - 1} more" else ""} between you and Perfect Day #${s.dayNumber}." to
                        "Your $streak-day streak ends at midnight if ${if (open.size == 1) "this ring stays" else "these rings stay"} open."
                    else -> "${open.size} ${if (open.size == 1) "ring" else "rings"} still open." to
                        "Close ${if (open.size == 1) "it" else "them"} before midnight for a Perfect Day and a sticker pack."
                }
            }
            Slot.LAST_CALL -> {
                if (!s.settings.notifyLastCall || open.isEmpty()) null
                else if (open.size == 1) "${label(open.first())} is the last ring." to "60 minutes left. ${lastCallTip(open.first(), s)}"
                else "${open.size} rings open, 60 minutes left." to "Every one you close still counts toward tomorrow's comeback."
            }
        }
    }

    private fun label(h: Habit) = when (h) {
        Habit.STEPS -> "Steps"
        Habit.CALORIES -> "Calories"
        Habit.WEIGH -> "The weigh-in"
        Habit.BATH -> "Bath"
    }

    private fun lastCallTip(h: Habit, s: HomeState) = when (h) {
        Habit.STEPS -> "A ${(s.goals.steps - s.today.steps + 99) / 100}-minute walk closes it."
        Habit.CALORIES -> if (s.calorieState == CalorieState.UNDER) "Close the kitchen to seal it." else "Log today's food in MyFitnessPal."
        Habit.WEIGH -> "30 seconds on the scale closes it."
        Habit.BATH -> "Already had one? Tap to log it."
    }

    fun nudge(context: Context, slot: Slot, s: HomeState) {
        if (!allowed(context)) return
        val (title, text) = nudgeText(slot, s) ?: return
        val b = NotificationCompat.Builder(context, NUDGE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_seal)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .setColor(0xFF43D3EA.toInt())
        if (Habit.BATH !in s.closed && slot in setOf(Slot.RISK, Slot.LAST_CALL)) {
            b.addAction(0, "Log bath", action(context, ActionReceiver.ACTION_BATH))
        }
        if (s.calorieState == CalorieState.UNDER && slot == Slot.WARN) {
            b.addAction(0, "Close kitchen", action(context, ActionReceiver.ACTION_KITCHEN))
        }
        NotificationManagerCompat.from(context).notify(NUDGE_ID, b.build())
    }

    fun cancelNudge(context: Context) = NotificationManagerCompat.from(context).cancel(NUDGE_ID)

    /** Rewards earned in the background. In the app, the screens celebrate instead. */
    fun announce(context: Context, events: List<HabitEvent>, force: Boolean = false) {
        if (events.isEmpty() || !allowed(context) || (!force && appInForeground())) return
        val perfect = events.filterIsInstance<HabitEvent.PerfectDay>().lastOrNull()
        val secret = events.filterIsInstance<HabitEvent.SecretUnlocked>().firstOrNull()
        val (title, text) = when {
            perfect != null && secret != null ->
                "Perfect Day! Two packs are waiting." to "One of them is a secret cat: ${Catalog[secret.catId].breed}."
            perfect != null -> "Perfect Day #${perfect.streak}!" to "All four closed. Your sticker pack is waiting: tap to rip it."
            secret != null -> "Secret cat unlocked" to "A ${Catalog[secret.catId].breed} pack is waiting."
            else -> {
                val saved = events.filterIsInstance<HabitEvent.Saved>().firstOrNull() ?: return
                "Swaps saved your streak" to "3 duplicates kept the streak alive. ${saved.swapsLeft} swaps left."
            }
        }
        NotificationManagerCompat.from(context).notify(
            REWARD_ID,
            NotificationCompat.Builder(context, REWARD_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_seal)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setColor(0xFFA98BFF.toInt())
                .setContentIntent(openApp(context, "today"))
                .build(),
        )
    }
}

/** The optional all-day notification with the four rings. */
object Ongoing {
    fun text(s: HomeState): String {
        val parts = ArrayList<String>()
        parts += "${s.closedCount} of 4"
        if (Habit.STEPS !in s.closed) parts += "${Coach.n(s.goals.steps - s.today.steps)} steps"
        if (Habit.CALORIES !in s.closed && s.calorieState != CalorieState.OVER) parts += "${Coach.n(s.goals.calories - s.today.calories)} kcal left"
        if (Habit.WEIGH !in s.closed) parts += "weigh-in"
        if (Habit.BATH !in s.closed) parts += "bath"
        return parts.joinToString(" · ")
    }

    fun update(context: Context, s: HomeState) {
        val m = NotificationManagerCompat.from(context)
        if (!s.settings.ongoing || !s.settings.onboarded || !Notifier.allowed(context)) {
            m.cancel(Notifier.ONGOING_ID)
            return
        }
        m.notify(
            Notifier.ONGOING_ID,
            NotificationCompat.Builder(context, Notifier.ONGOING_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_seal)
                .setContentTitle(if (s.perfect) "Perfect Day #${s.streak}" else "Today")
                .setContentText(if (s.perfect) "All four closed." else text(s))
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setContentIntent(Notifier.openApp(context))
                .build(),
        )
    }
}
