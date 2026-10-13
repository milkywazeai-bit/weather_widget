package com.milkywaze.weatherwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object WidgetRenderer {

    fun render(context: Context, widgetId: Int, prefs: Prefs, data: ComparisonResult?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compare)
        val dayIndex = prefs.dayIndex(widgetId)

        views.setTextViewText(R.id.location, prefs.location.name)
        views.setTextViewText(R.id.day_toggle, dayLabel(context, dayIndex) + " ▸")
        views.setOnClickPendingIntent(R.id.refresh, broadcast(context, CompareWidgetProvider.ACTION_REFRESH, widgetId))
        views.setOnClickPendingIntent(R.id.day_toggle, broadcast(context, CompareWidgetProvider.ACTION_NEXT_DAY, widgetId))
        views.setOnClickPendingIntent(R.id.root, openSettings(context))

        views.removeAllViews(R.id.rows)
        val selected = prefs.selectedModels
        val models = data?.models?.filter { it.model in selected }.orEmpty()

        if (models.isEmpty()) {
            val message = when {
                selected.isEmpty() -> context.getString(R.string.widget_no_models)
                prefs.isRefreshing -> context.getString(R.string.widget_loading)
                prefs.lastError != null -> context.getString(R.string.widget_error, prefs.lastError)
                else -> context.getString(R.string.widget_loading)
            }
            views.setTextViewText(R.id.message, message)
            views.setViewVisibility(R.id.message, View.VISIBLE)
            views.setViewVisibility(R.id.table, View.GONE)
            views.setTextViewText(R.id.subtitle, "")
            return views
        }

        views.setViewVisibility(R.id.message, View.GONE)
        views.setViewVisibility(R.id.table, View.VISIBLE)

        val showCurrent = dayIndex == 0
        views.setViewVisibility(R.id.header_current, if (showCurrent) View.VISIBLE else View.GONE)

        val consensus = Consensus.of(models, dayIndex)
        val highestMax = models.mapNotNull { it.days.getOrNull(dayIndex)?.tempMax }.maxOrNull()
        val lowestMin = models.mapNotNull { it.days.getOrNull(dayIndex)?.tempMin }.minOrNull()

        models.forEach { mf ->
            val day = mf.days.getOrNull(dayIndex)
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setTextViewText(R.id.model_name, mf.model.shortName)
            if (day == null || !day.hasData) {
                row.setTextViewText(R.id.icon, "")
                row.setTextViewText(R.id.high, context.getString(R.string.widget_no_data))
                row.setTextViewText(R.id.low, "")
                row.setTextViewText(R.id.precip, "")
                row.setTextViewText(R.id.current, "")
            } else {
                row.setTextViewText(R.id.icon, WeatherCodes.emoji(day.weatherCode))
                row.setTextViewText(R.id.high, formatTemp(day.tempMax) + if (day.tempMax != null && day.tempMax == highestMax && models.size > 1) "▲" else "")
                row.setTextViewText(R.id.low, formatTemp(day.tempMin) + if (day.tempMin != null && day.tempMin == lowestMin && models.size > 1) "▼" else "")
                row.setTextViewText(R.id.precip, formatPrecip(day))
                row.setTextViewText(R.id.current, formatTemp(mf.currentTemp))
            }
            row.setViewVisibility(R.id.current, if (showCurrent) View.VISIBLE else View.GONE)
            views.addView(R.id.rows, row)
        }

        views.setTextViewText(R.id.consensus, consensusText(context, consensus))

        val date = data!!.dates.getOrNull(dayIndex)?.let(::formatDate).orEmpty()
        val time = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(data.fetchedAt))
        val status = when {
            prefs.isRefreshing -> context.getString(R.string.widget_refreshing)
            prefs.lastError != null -> context.getString(R.string.widget_stale, time)
            else -> context.getString(R.string.widget_updated, time)
        }
        views.setTextViewText(R.id.subtitle, "$date · $status")
        return views
    }

    private fun consensusText(context: Context, c: Consensus): String {
        if (c.modelsWithData == 0) return context.getString(R.string.widget_no_data)
        val parts = mutableListOf(
            context.getString(R.string.consensus_avg, formatTemp(c.avgMax), formatTemp(c.avgMin)),
        )
        c.maxSpread?.let { parts += context.getString(R.string.consensus_spread, it.roundToInt()) }
        parts += context.getString(R.string.consensus_rain, c.rainyModels, c.modelsWithData)
        return parts.joinToString(" · ")
    }

    private fun formatTemp(value: Double?): String = value?.let { "${it.roundToInt()}°" } ?: "–"

    private fun formatPrecip(day: DayForecast): String {
        val prob = day.precipitationProbability?.let { "$it%" }
        val sum = day.precipitationSum?.let { if (it < 0.1) "0" else if (it < 10) String.format(Locale.US, "%.1f", it) else it.roundToInt().toString() }
        return when {
            prob != null && sum != null -> "$prob ${sum}㎜"
            sum != null -> "${sum}㎜"
            prob != null -> prob
            else -> "–"
        }
    }

    private fun dayLabel(context: Context, index: Int): String = when (index) {
        0 -> context.getString(R.string.day_today)
        1 -> context.getString(R.string.day_tomorrow)
        2 -> context.getString(R.string.day_after_tomorrow)
        else -> "+$index"
    }

    private fun formatDate(iso: String): String = runCatching {
        val d = LocalDate.parse(iso)
        "${d.monthValue}/${d.dayOfMonth} (${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"
    }.getOrDefault(iso)

    private fun broadcast(context: Context, action: String, widgetId: Int): PendingIntent {
        val intent = Intent(context, CompareWidgetProvider::class.java)
            .setAction(action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openSettings(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
