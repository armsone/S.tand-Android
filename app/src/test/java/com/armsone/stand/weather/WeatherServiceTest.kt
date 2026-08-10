package com.armsone.stand.weather

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherServiceTest {
    @Test
    fun openMeteoDecoderReadsCurrentWeather() {
        val weather = OpenMeteoJsonDecoder.decode(
            """
            {
              "latitude": 37.57,
              "longitude": 126.98,
              "current_units": { "temperature_2m": "°C" },
              "current": {
                "time": "2026-08-10T12:00",
                "interval": 900,
                "temperature_2m": 28.4,
                "apparent_temperature": 30.1,
                "precipitation": 0.2,
                "weather_code": 2,
                "is_day": 1
              }
            }
            """.trimIndent(),
        )

        assertEquals(28.4, weather.temperatureCelsius, 0.0001)
        assertEquals(30.1, weather.apparentTemperatureCelsius, 0.0001)
        assertEquals(0.2, weather.precipitationMillimeters, 0.0001)
        assertEquals(2, weather.weatherCode)
        assertTrue(weather.isDay)
        assertEquals("구름 조금", weather.summary)
    }

    @Test
    fun openMeteoDecoderIgnoresOuterFieldsAndSupportsExponentNumbers() {
        val weather = OpenMeteoJsonDecoder.decode(
            """
            {
              "temperature_2m": 999,
              "current": {
                "is_day": 0,
                "weather_code": 95,
                "precipitation": 5e-1,
                "apparent_temperature": -1.15e1,
                "temperature_2m": -12
              }
            }
            """.trimIndent(),
        )

        assertEquals(-12.0, weather.temperatureCelsius, 0.0001)
        assertEquals(-11.5, weather.apparentTemperatureCelsius, 0.0001)
        assertEquals(0.5, weather.precipitationMillimeters, 0.0001)
        assertEquals(95, weather.weatherCode)
        assertFalse(weather.isDay)
        assertEquals("뇌우", weather.summary)
    }

    @Test
    fun openMeteoDecoderRejectsMissingOrInvalidRequiredValues() {
        assertThrows(WeatherDecodingException::class.java) {
            OpenMeteoJsonDecoder.decode("{\"latitude\":37.5}")
        }
        assertThrows(WeatherDecodingException::class.java) {
            OpenMeteoJsonDecoder.decode(
                """
                {"current": {
                  "temperature_2m": 20,
                  "apparent_temperature": 20,
                  "precipitation": 0,
                  "weather_code": 0,
                  "is_day": 2
                }}
                """.trimIndent(),
            )
        }
    }

    @Test
    fun requestUsesRoundedCoordinatesAndRequiredOpenMeteoQuery() {
        val url = OpenMeteoRequest.url(
            latitude = 37.566535,
            longitude = 126.977969,
        )

        assertEquals(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=37.5665" +
                "&longitude=126.9780" +
                "&current=temperature_2m,apparent_temperature,precipitation,weather_code,is_day" +
                "&timezone=auto" +
                "&forecast_days=1",
            url.toString(),
        )
    }

    @Test
    fun requestRejectsCoordinatesOutsideEarthBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenMeteoRequest.url(latitude = 90.0001, longitude = 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OpenMeteoRequest.url(latitude = 0.0, longitude = Double.NaN)
        }
    }

    @Test
    fun cacheIsFreshForLessThanThirtyMinutesOnly() {
        val updated = Instant.parse("2026-08-10T00:00:00Z")

        assertTrue(
            WeatherCachePolicy.isFresh(
                lastUpdated = updated,
                now = updated.plusSeconds(29 * 60 + 59),
            ),
        )
        assertFalse(
            WeatherCachePolicy.isFresh(
                lastUpdated = updated,
                now = updated.plusSeconds(30 * 60),
            ),
        )
        assertFalse(
            WeatherCachePolicy.isFresh(
                lastUpdated = updated,
                now = updated.minusSeconds(1),
            ),
        )
        assertFalse(WeatherCachePolicy.isFresh(lastUpdated = null, now = updated))
    }

    @Test
    fun cachedLocationIsUsableForLessThanThirtyMinutes() {
        val nowNanos = 5_000_000_000_000L

        assertTrue(
            WeatherLocationPolicy.isUsable(
                latitude = 37.5665,
                longitude = 126.9780,
                locationElapsedRealtimeNanos = nowNanos - (29L * 60L + 59L) * 1_000_000_000L,
                nowElapsedRealtimeNanos = nowNanos,
            ),
        )
    }

    @Test
    fun cachedLocationAtExactlyThirtyMinutesIsNotUsable() {
        val nowNanos = 5_000_000_000_000L

        assertFalse(
            WeatherLocationPolicy.isUsable(
                latitude = 37.5665,
                longitude = 126.9780,
                locationElapsedRealtimeNanos = nowNanos - 30L * 60L * 1_000_000_000L,
                nowElapsedRealtimeNanos = nowNanos,
            ),
        )
    }

    @Test
    fun staleCachedLocationIsNotUsable() {
        val nowNanos = 5_000_000_000_000L

        assertFalse(
            WeatherLocationPolicy.isUsable(
                latitude = 37.5665,
                longitude = 126.9780,
                locationElapsedRealtimeNanos = nowNanos - (30L * 60L + 1L) * 1_000_000_000L,
                nowElapsedRealtimeNanos = nowNanos,
            ),
        )
    }

    @Test
    fun futureCachedLocationIsNotUsable() {
        val nowNanos = 5_000_000_000_000L

        assertFalse(
            WeatherLocationPolicy.isUsable(
                latitude = 37.5665,
                longitude = 126.9780,
                locationElapsedRealtimeNanos = nowNanos + 1L,
                nowElapsedRealtimeNanos = nowNanos,
            ),
        )
    }

    @Test
    fun cachedLocationRequiresFiniteCoordinatesInsideEarthBounds() {
        val nowNanos = 5_000_000_000_000L
        val invalidCoordinates = listOf(
            Double.NaN to 126.9780,
            Double.POSITIVE_INFINITY to 126.9780,
            90.0001 to 126.9780,
            37.5665 to Double.NaN,
            37.5665 to Double.NEGATIVE_INFINITY,
            37.5665 to 180.0001,
        )

        invalidCoordinates.forEach { (latitude, longitude) ->
            assertFalse(
                WeatherLocationPolicy.isUsable(
                    latitude = latitude,
                    longitude = longitude,
                    locationElapsedRealtimeNanos = nowNanos,
                    nowElapsedRealtimeNanos = nowNanos,
                ),
            )
        }
    }

    @Test
    fun wmoCodesMatchIosKoreanSummaries() {
        val expected = mapOf(
            0 to "맑음",
            1 to "대체로 맑음",
            2 to "구름 조금",
            3 to "흐림",
            45 to "안개",
            48 to "안개",
            51 to "이슬비",
            57 to "이슬비",
            61 to "비",
            67 to "비",
            71 to "눈",
            77 to "눈",
            80 to "소나기",
            82 to "소나기",
            85 to "눈 소나기",
            86 to "눈 소나기",
            95 to "뇌우",
            99 to "뇌우",
            -1 to "날씨 정보",
            100 to "날씨 정보",
        )

        expected.forEach { (code, summary) ->
            assertEquals(summary, WmoKoreanSummary.forCode(code))
        }
    }

    @Test
    fun locationNameUsesUniqueRegionsAndFallsBackToCountry() {
        assertEquals(
            "서울특별시 종로구 청운동",
            LocationNameFormatter.format(
                administrativeArea = " 서울특별시 ",
                locality = "서울특별시",
                subAdministrativeArea = "종로구",
                subLocality = "청운동",
                country = "대한민국",
            ),
        )
        assertEquals(
            "대한민국",
            LocationNameFormatter.format(
                administrativeArea = " ",
                locality = null,
                subAdministrativeArea = null,
                subLocality = null,
                country = " 대한민국 ",
            ),
        )
        assertNull(
            LocationNameFormatter.format(
                administrativeArea = null,
                locality = null,
                subAdministrativeArea = null,
                subLocality = null,
                country = " ",
            ),
        )
    }
}
