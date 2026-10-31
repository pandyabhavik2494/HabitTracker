package com.pandyabhavik.habittracker.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import com.pandyabhavik.habittracker.Graph
import com.pandyabhavik.habittracker.data.HomeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The home-screen widgets. Each one shows [Graph.snapshot] at its real size (SizeMode.Exact).
 *
 * Glance keeps a widget's session alive for a while after it draws, and an update() during that time only
 * recomposes with the data the session already holds. So each widget collects [Widgets.redraws] and
 * reloads the snapshot whenever it changes; [Widgets.refresh] bumps it and also starts the sessions that
 * have ended.
 */
abstract class HabitWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext
        val initial = Widgets.load(app)
        provideContent {
            val state by remember { Widgets.redraws.map { Widgets.load(app) } }.collectAsState(initial)
            val s = state
            if (s == null || !s.settings.onboarded) EmptyWidget(app) else Draw(app, s)
        }
    }

    @Composable
    protected abstract fun Draw(context: Context, s: HomeState)
}

class SealWidget : HabitWidget() {
    @Composable
    override fun Draw(context: Context, s: HomeState) = SealView(context, s)
}

class ListWidget : HabitWidget() {
    @Composable
    override fun Draw(context: Context, s: HomeState) = ListView(context, s)
}

class StreakWidget : HabitWidget() {
    @Composable
    override fun Draw(context: Context, s: HomeState) = StreakView(context, s)
}

class BathWidget : HabitWidget() {
    @Composable
    override fun Draw(context: Context, s: HomeState) = BathView(s)
}

class StripWidget : HabitWidget() {
    @Composable
    override fun Draw(context: Context, s: HomeState) = StripView(context, s)
}

class WideWidget : HabitWidget() {
    @Composable
    override fun Draw(context: Context, s: HomeState) = WideView(context, s)
}

class LargeWidget : HabitWidget() {
    @Composable
    override fun Draw(context: Context, s: HomeState) = LargeView(context, s)
}

// ------------------------------------------------------------------------------------------ receivers

class SealWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SealWidget()
}

class ListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ListWidget()
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

class BathWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BathWidget()
}

class StripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StripWidget()
}

class WideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WideWidget()
}

class LargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargeWidget()
}

object Widgets {
    private const val TAG = "Widgets"

    /** Bumped on every refresh, so widget sessions that are still alive reload the snapshot. */
    internal val redraws = MutableStateFlow(0L)

    internal suspend fun load(context: Context): HomeState? = try {
        Graph.snapshot(context)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't load the widget state", e)
        null
    }

    /** Redraws all seven widgets. Errors are logged, never thrown. */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        redraws.value = redraws.value + 1
        val all = listOf(
            SealWidget(), ListWidget(), StreakWidget(), BathWidget(), StripWidget(), WideWidget(), LargeWidget(),
        )
        for (widget in all) {
            try {
                widget.updateAll(app)
            } catch (e: CancellationException) {
                // Only the caller being cancelled; not an error.
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't update ${widget.javaClass.simpleName}", e)
            }
        }
    }
}
