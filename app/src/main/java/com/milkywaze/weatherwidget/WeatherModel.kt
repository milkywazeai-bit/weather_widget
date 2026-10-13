package com.milkywaze.weatherwidget

/**
 * Forecast models offered by Open-Meteo that can be compared side by side.
 * [apiId] is the value passed to the `models` query parameter and the suffix
 * Open-Meteo appends to every variable when more than one model is requested.
 */
enum class WeatherModel(
    val apiId: String,
    val shortName: String,
    val provider: String,
    val enabledByDefault: Boolean,
) {
    KMA("kma_seamless", "KMA", "기상청 (한국)", true),
    ECMWF("ecmwf_ifs025", "ECMWF", "유럽중기예보센터", true),
    GFS("gfs_seamless", "GFS", "NOAA (미국)", true),
    ICON("icon_seamless", "ICON", "DWD (독일)", true),
    JMA("jma_seamless", "JMA", "기상청 (일본)", true),
    UKMO("ukmo_seamless", "UKMO", "Met Office (영국)", false),
    METEO_FRANCE("meteofrance_seamless", "MF", "Météo-France (프랑스)", false),
    GEM("gem_seamless", "GEM", "캐나다 기상청", false),
    ;

    companion object {
        fun fromApiId(id: String): WeatherModel? = entries.firstOrNull { it.apiId == id }
    }
}
