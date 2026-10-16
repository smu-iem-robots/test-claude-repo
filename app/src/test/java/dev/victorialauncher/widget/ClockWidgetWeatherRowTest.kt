// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The order of the clock widget's weather row. It is one line cut off at its end, so whatever
 * goes last is what a narrow widget loses.
 */
class ClockWidgetWeatherRowTest {

    private var localeBefore: Locale = Locale.getDefault()

    @Before
    fun pinLocale() {
        // The temperature is formatted for the default locale, and the digits are the point here.
        localeBefore = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(localeBefore)
    }

    // 294 K is 20.85 °C and 69.53 °F.
    private val cloudy = ClockWidgetWeather.Reading(tempK = 294, condition = "Partly cloudy")

    @Test
    fun `the wind comes before the condition so the condition is what gets cut`() {
        assertEquals(
            "21°C  5 Bft SW  Partly cloudy",
            ClockWidgetWeather.row(cloudy, fahrenheit = false, wind = "5 Bft SW"),
        )
    }

    @Test
    fun `without the wind the row is what it always was`() {
        assertEquals("21°C  Partly cloudy", ClockWidgetWeather.row(cloudy, fahrenheit = false, wind = null))
        assertEquals("21°C  Partly cloudy", ClockWidgetWeather.formatted(cloudy, fahrenheit = false))
        assertEquals("70°F  Partly cloudy", ClockWidgetWeather.formatted(cloudy, fahrenheit = true))
    }

    @Test
    fun `no condition leaves no gap behind`() {
        val bare = cloudy.copy(condition = "")
        assertEquals("21°C  5 Bft SW", ClockWidgetWeather.row(bare, fahrenheit = false, wind = "5 Bft SW"))
        assertEquals("21°C", ClockWidgetWeather.row(bare, fahrenheit = false, wind = null))
    }
}
