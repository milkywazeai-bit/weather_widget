package com.milkywaze.weatherwidget

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("weather_compare", Context.MODE_PRIVATE)

    var location: Location
        get() = Location(
            name = sp.getString(KEY_LOC_NAME, null) ?: DEFAULT_LOCATION.name,
            latitude = sp.getFloat(KEY_LAT, DEFAULT_LOCATION.latitude.toFloat()).toDouble(),
            longitude = sp.getFloat(KEY_LON, DEFAULT_LOCATION.longitude.toFloat()).toDouble(),
        )
        set(value) = sp.edit {
            putString(KEY_LOC_NAME, value.name)
            putFloat(KEY_LAT, value.latitude.toFloat())
            putFloat(KEY_LON, value.longitude.toFloat())
        }

    var selectedModels: List<WeatherModel>
        get() {
            val ids = sp.getStringSet(KEY_MODELS, null)
                ?: return WeatherModel.entries.filter { it.enabledByDefault }
            // Keep enum order so rows are stable regardless of set ordering.
            return WeatherModel.entries.filter { it.apiId in ids }
        }
        set(value) = sp.edit { putStringSet(KEY_MODELS, value.map { it.apiId }.toSet()) }

    /** Last successful raw API response plus the models it was requested for. */
    fun saveCache(json: String, models: List<WeatherModel>, fetchedAt: Long) = sp.edit {
        putString(KEY_CACHE_JSON, json)
        putString(KEY_CACHE_MODELS, models.joinToString(",") { it.apiId })
        putLong(KEY_CACHE_TIME, fetchedAt)
        remove(KEY_LAST_ERROR)
    }

    fun loadCache(): ComparisonResult? {
        val json = sp.getString(KEY_CACHE_JSON, null) ?: return null
        val models = sp.getString(KEY_CACHE_MODELS, "").orEmpty()
            .split(",").mapNotNull(WeatherModel::fromApiId)
        if (models.isEmpty()) return null
        return runCatching { OpenMeteoParser.parse(json, models, sp.getLong(KEY_CACHE_TIME, 0L)) }.getOrNull()
    }

    fun clearCache() = sp.edit {
        remove(KEY_CACHE_JSON)
        remove(KEY_CACHE_MODELS)
        remove(KEY_CACHE_TIME)
    }

    var lastError: String?
        get() = sp.getString(KEY_LAST_ERROR, null)
        set(value) = sp.edit { putString(KEY_LAST_ERROR, value) }

    /** True while a refresh is running; expires so a killed worker can't leave it stuck. */
    var isRefreshing: Boolean
        get() = System.currentTimeMillis() - sp.getLong(KEY_REFRESHING_SINCE, 0L) < REFRESH_TIMEOUT_MS
        set(value) = sp.edit {
            if (value) putLong(KEY_REFRESHING_SINCE, System.currentTimeMillis()) else remove(KEY_REFRESHING_SINCE)
        }

    fun dayIndex(widgetId: Int): Int = sp.getInt("$KEY_DAY_PREFIX$widgetId", 0)

    fun setDayIndex(widgetId: Int, index: Int) = sp.edit { putInt("$KEY_DAY_PREFIX$widgetId", index) }

    fun removeWidget(widgetId: Int) = sp.edit { remove("$KEY_DAY_PREFIX$widgetId") }

    companion object {
        val DEFAULT_LOCATION = Location("서울", 37.5665, 126.9780)

        private const val KEY_LOC_NAME = "loc_name"
        private const val KEY_LAT = "loc_lat"
        private const val KEY_LON = "loc_lon"
        private const val KEY_MODELS = "models"
        private const val KEY_CACHE_JSON = "cache_json"
        private const val KEY_CACHE_MODELS = "cache_models"
        private const val KEY_CACHE_TIME = "cache_time"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_REFRESHING_SINCE = "refreshing_since"
        private const val REFRESH_TIMEOUT_MS = 2 * 60 * 1000L
        private const val KEY_DAY_PREFIX = "day_"
    }
}
