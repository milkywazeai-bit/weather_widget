package com.milkywaze.weatherwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class CompareWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        RefreshWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        RefreshWorker.cancelAll(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = Prefs(context)
        val cache = prefs.loadCache()
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, WidgetRenderer.render(context, id, prefs, cache)) }
        RefreshWorker.schedulePeriodic(context)
        if (cache == null || System.currentTimeMillis() - cache.fetchedAt > STALE_AFTER_MS) {
            RefreshWorker.refreshNow(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = Prefs(context)
        appWidgetIds.forEach(prefs::removeWidget)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> RefreshWorker.refreshNow(context)
            ACTION_NEXT_DAY -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = Prefs(context)
                    prefs.setDayIndex(id, (prefs.dayIndex(id) + 1) % OpenMeteoClient.FORECAST_DAYS)
                    AppWidgetManager.getInstance(context)
                        .updateAppWidget(id, WidgetRenderer.render(context, id, prefs, prefs.loadCache()))
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.milkywaze.weatherwidget.ACTION_REFRESH"
        const val ACTION_NEXT_DAY = "com.milkywaze.weatherwidget.ACTION_NEXT_DAY"
        private const val STALE_AFTER_MS = 30 * 60 * 1000L

        /** Re-renders every placed widget from the cached forecast (no network). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CompareWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val prefs = Prefs(context)
            val cache = prefs.loadCache()
            ids.forEach { id -> manager.updateAppWidget(id, WidgetRenderer.render(context, id, prefs, cache)) }
        }
    }
}
