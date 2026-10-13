package com.milkywaze.weatherwidget

/** WMO weather interpretation codes (used by Open-Meteo) → emoji + Korean label. */
object WeatherCodes {
    fun emoji(code: Int?): String = when (code) {
        null -> "·"
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "❔"
    }

    fun label(code: Int?): String = when (code) {
        null -> "정보 없음"
        0 -> "맑음"
        1 -> "대체로 맑음"
        2 -> "구름 조금"
        3 -> "흐림"
        45, 48 -> "안개"
        51, 53, 55 -> "이슬비"
        56, 57 -> "어는 이슬비"
        61, 63 -> "비"
        65 -> "강한 비"
        66, 67 -> "어는 비"
        71, 73 -> "눈"
        75 -> "강한 눈"
        77 -> "싸락눈"
        80, 81 -> "소나기"
        82 -> "강한 소나기"
        85, 86 -> "소낙눈"
        95 -> "뇌우"
        96, 99 -> "우박 동반 뇌우"
        else -> "알 수 없음"
    }
}
