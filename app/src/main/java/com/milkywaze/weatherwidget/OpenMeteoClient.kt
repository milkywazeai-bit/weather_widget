package com.milkywaze.weatherwidget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Minimal client for the free, key-less Open-Meteo forecast and geocoding APIs. */
object OpenMeteoClient {
    private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
    const val FORECAST_DAYS = 3

    fun forecastUrl(location: Location, models: List<WeatherModel>): String = buildString {
        append(FORECAST_URL)
        append("?latitude=").append(String.format(Locale.US, "%.4f", location.latitude))
        append("&longitude=").append(String.format(Locale.US, "%.4f", location.longitude))
        append("&current=temperature_2m,weather_code")
        append("&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max")
        append("&models=").append(models.joinToString(",") { it.apiId })
        append("&timezone=auto")
        append("&forecast_days=").append(FORECAST_DAYS)
    }

    /** Returns the raw JSON body so it can be cached and re-parsed later. */
    suspend fun fetchForecastJson(location: Location, models: List<WeatherModel>): String =
        get(forecastUrl(location, models))

    suspend fun searchLocations(query: String): List<Location> {
        val url = "$GEOCODING_URL?name=${URLEncoder.encode(query, "UTF-8")}&count=10&language=ko&format=json"
        val results = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            val detail = listOf(r.optString("admin1"), r.optString("country"))
                .filter { it.isNotBlank() }
                .joinToString(", ")
            Location(
                name = if (detail.isEmpty()) r.getString("name") else "${r.getString("name")} ($detail)",
                latitude = r.getDouble("latitude"),
                longitude = r.getDouble("longitude"),
            )
        }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("HTTP $code ${err.take(200)}")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
