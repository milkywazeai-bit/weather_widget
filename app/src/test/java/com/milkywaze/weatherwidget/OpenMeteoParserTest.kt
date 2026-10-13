package com.milkywaze.weatherwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OpenMeteoParserTest {

    private val multiModelJson = """
        {
          "current": {
            "time": "2026-10-13T11:00",
            "temperature_2m_kma_seamless": 18.4,
            "weather_code_kma_seamless": 1,
            "temperature_2m_gfs_seamless": 19.6,
            "weather_code_gfs_seamless": 3
          },
          "daily": {
            "time": ["2026-10-13", "2026-10-14"],
            "weather_code_kma_seamless": [1, 61],
            "temperature_2m_max_kma_seamless": [22.1, 19.0],
            "temperature_2m_min_kma_seamless": [12.3, 14.0],
            "precipitation_sum_kma_seamless": [0.0, 6.2],
            "precipitation_probability_max_kma_seamless": [null, null],
            "weather_code_gfs_seamless": [3, 3],
            "temperature_2m_max_gfs_seamless": [24.9, 18.2],
            "temperature_2m_min_gfs_seamless": [13.0, 13.5],
            "precipitation_sum_gfs_seamless": [0.2, 0.4],
            "precipitation_probability_max_gfs_seamless": [10, 35]
          }
        }
    """.trimIndent()

    @Test
    fun parsesSuffixedMultiModelResponse() {
        val result = OpenMeteoParser.parse(multiModelJson, listOf(WeatherModel.KMA, WeatherModel.GFS, WeatherModel.ICON), 1L)

        assertEquals(listOf("2026-10-13", "2026-10-14"), result.dates)
        val kma = result.models[0]
        assertEquals(18.4, kma.currentTemp!!, 0.001)
        assertEquals(1, kma.currentCode)
        assertEquals(61, kma.days[1].weatherCode)
        assertNull(kma.days[0].precipitationProbability)

        val gfs = result.models[1]
        assertEquals(35, gfs.days[1].precipitationProbability)

        // ICON wasn't in the response at all: rows exist but carry no data.
        val icon = result.models[2]
        assertNull(icon.currentTemp)
        assertFalse(icon.days[0].hasData)
    }

    @Test
    fun parsesUnsuffixedSingleModelResponse() {
        val json = """
            {"current": {"temperature_2m": 5.0, "weather_code": 71},
             "daily": {"time": ["2026-12-01"], "weather_code": [71], "temperature_2m_max": [6.0],
                       "temperature_2m_min": [-2.0], "precipitation_sum": [3.0], "precipitation_probability_max": [80]}}
        """.trimIndent()
        val result = OpenMeteoParser.parse(json, listOf(WeatherModel.ECMWF), 1L)
        val day = result.models.single().days.single()
        assertEquals(5.0, result.models.single().currentTemp!!, 0.001)
        assertEquals(-2.0, day.tempMin!!, 0.001)
        assertEquals(80, day.precipitationProbability)
    }

    @Test
    fun consensusIgnoresModelsWithoutData() {
        val result = OpenMeteoParser.parse(multiModelJson, listOf(WeatherModel.KMA, WeatherModel.GFS, WeatherModel.ICON), 1L)

        val today = Consensus.of(result.models, 0)
        assertEquals(2, today.modelsWithData)
        assertEquals(23.5, today.avgMax!!, 0.001)
        assertEquals(2.8, today.maxSpread!!, 0.001)
        assertEquals(0, today.rainyModels)

        val tomorrow = Consensus.of(result.models, 1)
        assertEquals(1, tomorrow.rainyModels)
    }
}
