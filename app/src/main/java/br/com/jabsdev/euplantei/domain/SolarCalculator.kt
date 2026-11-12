package br.com.jabsdev.euplantei.domain

import br.com.jabsdev.euplantei.domain.model.SunTimes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Cálculo solar offline baseado no algoritmo NOAA.
 *
 * É usado quando o Open-Meteo não está acessível e também para preencher
 * nascer/pôr do sol em climas informados manualmente. Não faz chamadas de rede.
 */
object SolarCalculator {
    private const val ZENITH_DEGREES = 90.833
    private const val MINUTES_PER_DAY = 1440

    data class SolarPosition(
        val elevationDegrees: Double,
        val azimuthDegrees: Double,
    )

    fun sunTimes(
        latitude: Double,
        longitude: Double,
        date: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): SunTimes {
        val dayOfYear = date.dayOfYear
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1 + 0.5)
        val equationOfTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )
        val declination = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(latitude)
        val cosHourAngle = (
            cos(Math.toRadians(ZENITH_DEGREES)) /
                (cos(latRad) * cos(declination)) -
                tan(latRad) * tan(declination)
            )

        if (cosHourAngle > 1.0) {
            // Noite polar: o sol não nasce.
            return SunTimes(sunrise = null, sunset = null, dayLengthMinutes = 0)
        }
        if (cosHourAngle < -1.0) {
            // Dia polar: o sol não se põe.
            return SunTimes(sunrise = null, sunset = null, dayLengthMinutes = MINUTES_PER_DAY)
        }

        val hourAngle = Math.toDegrees(acos(cosHourAngle))
        val sunriseUtc = 720.0 - 4.0 * (longitude + hourAngle) - equationOfTime
        val sunsetUtc = 720.0 - 4.0 * (longitude - hourAngle) - equationOfTime

        val offsetMinutes = date.atStartOfDay(zoneId).offset.totalSeconds / 60
        val sunriseLocal = wrapMinutes(sunriseUtc + offsetMinutes)
        val sunsetLocal = wrapMinutes(sunsetUtc + offsetMinutes)
        val dayLength = ((sunsetLocal - sunriseLocal + MINUTES_PER_DAY) % MINUTES_PER_DAY).toInt()

        return SunTimes(
            sunrise = formatMinutes(sunriseLocal),
            sunset = formatMinutes(sunsetLocal),
            dayLengthMinutes = dayLength,
        )
    }

    /** Posição aproximada do sol, útil para orientar sombreamento e horário de rega. */
    fun position(
        latitude: Double,
        longitude: Double,
        dateTime: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): SolarPosition {
        val date = dateTime.toLocalDate()
        val dayOfYear = date.dayOfYear
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1 + (dateTime.hour - 12) / 24.0)
        val equationOfTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )
        val declination = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
                0.00148 * sin(3 * gamma)

        val zoneOffsetMinutes = dateTime.atZone(zoneId).offset.totalSeconds / 60
        val minutesOfDay = dateTime.hour * 60 + dateTime.minute
        val trueSolarTime = (
            minutesOfDay +
                equationOfTime +
                4.0 * longitude -
                60.0 * zoneOffsetMinutes / 60.0
            ) % MINUTES_PER_DAY
        val hourAngle = Math.toRadians(trueSolarTime / 4.0 - 180.0)
        val latRad = Math.toRadians(latitude)

        val cosZenith = sin(latRad) * sin(declination) +
            cos(latRad) * cos(declination) * cos(hourAngle)
        val zenith = acos(cosZenith.coerceIn(-1.0, 1.0))
        val elevation = 90.0 - Math.toDegrees(zenith)

        val azimuth = Math.toDegrees(
            Math.atan2(
                sin(hourAngle),
                cos(hourAngle) * sin(latRad) - tan(declination) * cos(latRad),
            ),
        ) + 180.0

        return SolarPosition(
            elevationDegrees = elevation,
            azimuthDegrees = (azimuth + 360.0) % 360.0,
        )
    }

    fun dayOfYear(date: LocalDate): Long = ChronoUnit.DAYS.between(
        LocalDate.of(date.year, 1, 1),
        date,
    ) + 1

    fun localTimeLabel(dateTime: LocalDateTime): String =
        dateTime.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString()

    private fun wrapMinutes(minutes: Double): Double {
        val wrapped = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return wrapped
    }

    private fun formatMinutes(minutes: Double): String {
        val total = minutes.toInt().coerceIn(0, MINUTES_PER_DAY - 1)
        val hour = total / 60
        val minute = total % 60
        return "%02d:%02d".format(hour, minute)
    }
}
