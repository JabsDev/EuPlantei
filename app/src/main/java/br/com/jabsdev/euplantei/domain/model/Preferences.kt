package br.com.jabsdev.euplantei.domain.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT,
}

enum class ConsultationStatus {
    IDLE,
    CAPTURING,
    CLASSIFYING,
    AWAITING_CONFIRMATION,
    AWAITING_MANUAL_INPUT,
    FETCHING_WEATHER,
    GENERATING,
    SUCCESS,
    ERROR,
}
