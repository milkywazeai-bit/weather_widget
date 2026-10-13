package com.milkywaze.weatherwidget

import org.json.JSONObject

/**
 * Parses an Open-Meteo `/v1/forecast` response. When several models are
 * requested every variable is suffixed with `_<model id>`; with a single model
 * the variables are unsuffixed, so both forms are accepted.
 */
object OpenMeteoParser {

    fun parse(json: String, models: List<WeatherModel>, fetchedAt: Long): ComparisonResult {
        val root = JSONObject(json)
        val daily = root.optJSONObject("daily") ?: JSONObject()
        val current = root.optJSONObject("current") ?: JSONObject()
        val times = daily.optJSONArray("time")
        val dates = (0 until (times?.length() ?: 0)).map { times!!.getString(it) }
        val single = models.size == 1

        val forecasts = models.map { model ->
            fun key(name: String) = if (single) name else "${name}_${model.apiId}"
            fun dailyDouble(name: String, i: Int): Double? =
                daily.optJSONArray(key(name))?.let { arr ->
                    if (i >= arr.length() || arr.isNull(i)) null else arr.getDouble(i)
                }

            val days = dates.indices.map { i ->
                DayForecast(
                    date = dates[i],
                    weatherCode = dailyDouble("weather_code", i)?.toInt(),
                    tempMax = dailyDouble("temperature_2m_max", i),
                    tempMin = dailyDouble("temperature_2m_min", i),
                    precipitationSum = dailyDouble("precipitation_sum", i),
                    precipitationProbability = dailyDouble("precipitation_probability_max", i)?.toInt(),
                )
            }
            ModelForecast(
                model = model,
                currentTemp = current.optDoubleOrNull(key("temperature_2m")),
                currentCode = current.optDoubleOrNull(key("weather_code"))?.toInt(),
                days = days,
            )
        }
        return ComparisonResult(fetchedAt, dates, forecasts)
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeUnless { it.isNaN() }
}
