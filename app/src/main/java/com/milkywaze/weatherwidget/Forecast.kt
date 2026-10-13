package com.milkywaze.weatherwidget

data class Location(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class DayForecast(
    val date: String,
    val weatherCode: Int?,
    val tempMax: Double?,
    val tempMin: Double?,
    val precipitationSum: Double?,
    val precipitationProbability: Int?,
) {
    val hasData: Boolean get() = tempMax != null || tempMin != null || weatherCode != null
}

data class ModelForecast(
    val model: WeatherModel,
    val currentTemp: Double?,
    val currentCode: Int?,
    val days: List<DayForecast>,
)

data class ComparisonResult(
    val fetchedAt: Long,
    val dates: List<String>,
    val models: List<ModelForecast>,
)

/** Aggregate view across all models for one day. */
data class Consensus(
    val avgMax: Double?,
    val avgMin: Double?,
    val maxSpread: Double?,
    val rainyModels: Int,
    val modelsWithData: Int,
) {
    companion object {
        const val RAIN_THRESHOLD_MM = 1.0

        fun of(models: List<ModelForecast>, dayIndex: Int): Consensus {
            val days = models.mapNotNull { it.days.getOrNull(dayIndex)?.takeIf(DayForecast::hasData) }
            val highs = days.mapNotNull { it.tempMax }
            val lows = days.mapNotNull { it.tempMin }
            return Consensus(
                avgMax = highs.takeIf { it.isNotEmpty() }?.average(),
                avgMin = lows.takeIf { it.isNotEmpty() }?.average(),
                maxSpread = highs.takeIf { it.size >= 2 }?.let { it.max() - it.min() },
                rainyModels = days.count { (it.precipitationSum ?: 0.0) >= RAIN_THRESHOLD_MM },
                modelsWithData = days.size,
            )
        }
    }
}
