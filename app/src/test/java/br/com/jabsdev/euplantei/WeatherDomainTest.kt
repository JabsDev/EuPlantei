package br.com.jabsdev.euplantei

import br.com.jabsdev.euplantei.domain.SolarCalculator
import br.com.jabsdev.euplantei.domain.WeatherCodeMapper
import br.com.jabsdev.euplantei.domain.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WeatherDomainTest {
    @Test
    fun `tabela WMO cobre as faixas principais`() {
        assertEquals(WeatherCondition.CLEAR, WeatherCodeMapper.conditionFor(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCodeMapper.conditionFor(1))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCodeMapper.conditionFor(2))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCodeMapper.conditionFor(3))
        assertEquals(WeatherCondition.FOG, WeatherCodeMapper.conditionFor(45))
        assertEquals(WeatherCondition.FOG, WeatherCodeMapper.conditionFor(48))
        assertEquals(WeatherCondition.DRIZZLE, WeatherCodeMapper.conditionFor(51))
        assertEquals(WeatherCondition.RAIN, WeatherCodeMapper.conditionFor(63))
        assertEquals(WeatherCondition.SNOW, WeatherCodeMapper.conditionFor(75))
        assertEquals(WeatherCondition.SHOWERS, WeatherCodeMapper.conditionFor(81))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCodeMapper.conditionFor(96))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCodeMapper.conditionFor(999))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCodeMapper.conditionFor(null))
    }

    @Test
    fun `nascer e por do sol em Sao Paulo ficam em faixas plausiveis`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val sun = SolarCalculator.sunTimes(-23.55, -46.63, LocalDate.of(2026, 11, 11), zone)
        assertNotNull(sun.sunrise)
        assertNotNull(sun.sunset)
        val sunriseMinutes = sun.sunrise!!.toMinutes()
        val sunsetMinutes = sun.sunset!!.toMinutes()
        assertTrue("sunrise=$sunriseMinutes", sunriseMinutes in 4 * 60..7 * 60)
        assertTrue("sunset=$sunsetMinutes", sunsetMinutes in 17 * 60..20 * 60)
        assertTrue((sun.dayLengthMinutes ?: 0) in 11 * 60..14 * 60)
    }

    @Test
    fun `posicao do sol ao meio-dia fica alta`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val position = SolarCalculator.position(
            -23.55,
            -46.63,
            LocalDateTime.of(2026, 11, 11, 12, 0),
            zone,
        )
        assertTrue("elevação=${position.elevationDegrees}", position.elevationDegrees > 55)
        assertTrue(position.azimuthDegrees in 0.0..360.0)
    }

    private fun String.toMinutes(): Int {
        val parts = split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}
