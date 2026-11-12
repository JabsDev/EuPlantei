package br.com.jabsdev.euplantei

import br.com.jabsdev.euplantei.data.db.GardenPlantEntity
import br.com.jabsdev.euplantei.domain.DAY_MILLIS
import br.com.jabsdev.euplantei.domain.GardenAlertKind
import br.com.jabsdev.euplantei.domain.GardenWeatherAdvisor
import br.com.jabsdev.euplantei.domain.WateringStatus
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import br.com.jabsdev.euplantei.domain.wateringStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenWeatherAdvisorTest {

    @Test
    fun `chuva por codigo do tempo gera alerta`() {
        val alerts = GardenWeatherAdvisor.alerts(
            WeatherContext(conditionCode = 63, temperatureC = 22.0),
        )
        assertEquals(listOf(GardenAlertKind.RAIN), alerts.map { it.kind })
    }

    @Test
    fun `alta probabilidade de chuva gera alerta mesmo sem volume`() {
        val alerts = GardenWeatherAdvisor.alerts(
            WeatherContext(precipitationProbabilityPercent = 80, temperatureC = 26.0),
        )
        assertTrue(alerts.any { it.kind == GardenAlertKind.RAIN })
    }

    @Test
    fun `calor usa sensacao termica`() {
        val alerts = GardenWeatherAdvisor.alerts(
            WeatherContext(temperatureC = 28.0, apparentTemperatureC = 35.0, humidityPercent = 55),
        )
        assertTrue(alerts.any { it.kind == GardenAlertKind.HEAT })
    }

    @Test
    fun `ar seco so alerta quando combina calor e umidade baixa`() {
        val dry = GardenWeatherAdvisor.alerts(
            WeatherContext(temperatureC = 30.0, humidityPercent = 30),
        )
        assertTrue(dry.any { it.kind == GardenAlertKind.DRY_AIR })

        val mild = GardenWeatherAdvisor.alerts(
            WeatherContext(temperatureC = 20.0, humidityPercent = 30),
        )
        assertTrue(mild.none { it.kind == GardenAlertKind.DRY_AIR })
    }

    @Test
    fun `frio e vento geram alertas proprios`() {
        val alerts = GardenWeatherAdvisor.alerts(
            WeatherContext(temperatureC = 8.0, windSpeedMs = 12.0),
        )
        assertTrue(alerts.any { it.kind == GardenAlertKind.COLD })
        assertTrue(alerts.any { it.kind == GardenAlertKind.WIND })
    }

    @Test
    fun `clima ameno nao gera alerta`() {
        val alerts = GardenWeatherAdvisor.alerts(
            WeatherContext(
                temperatureC = 24.0,
                humidityPercent = 65,
                windSpeedMs = 2.0,
                conditionCode = 1,
            ),
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `sem clima nao gera alerta`() {
        assertTrue(GardenWeatherAdvisor.alerts(null).isEmpty())
        assertTrue(GardenWeatherAdvisor.alerts(WeatherContext()).isEmpty())
    }

    @Test
    fun `previsao antiga do cache nao gera alerta`() {
        val stale = WeatherContext(
            conditionCode = 63,
            temperatureC = 22.0,
            timestampMillis = System.currentTimeMillis() - 9L * 60L * 60L * 1000L,
        )
        assertTrue(GardenWeatherAdvisor.alerts(stale).isEmpty())
    }

    @Test
    fun `status de rega respeita o dia do calendario`() {
        val now = java.time.LocalDate.now()
            .atTime(12, 0)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val overdue = plant(nextWateringAt = now - DAY_MILLIS)
        val today = plant(nextWateringAt = now + 60_000)
        val later = plant(nextWateringAt = now + 4 * DAY_MILLIS)

        assertEquals(WateringStatus.OVERDUE, overdue.wateringStatus(now))
        assertEquals(WateringStatus.TODAY, today.wateringStatus(now))
        assertEquals(WateringStatus.LATER, later.wateringStatus(now))
    }

    private fun plant(nextWateringAt: Long) = GardenPlantEntity(
        id = 1,
        name = "Lírio",
        species = "Lírio",
        speciesId = "lirio",
        coverImagePath = "",
        addedAt = 0,
        nextWateringAt = nextWateringAt,
    )
}
