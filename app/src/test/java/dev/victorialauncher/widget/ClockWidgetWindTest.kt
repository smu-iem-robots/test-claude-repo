// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import dev.victorialauncher.ui.applist.beaufortForce
import dev.victorialauncher.ui.applist.unitNamed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The wind on the clock widget's weather row, from the km/h Gadgetbridge's broadcast carries. */
class ClockWidgetWindTest {

    private val compass = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    private fun wind(kmh: Double, degrees: Int?, unit: WindUnit) =
        ClockWidgetWind.formatted(kmh, degrees, unit, compass)

    @Test
    fun `km per hour lands on the force the WMO table gives it`() {
        assertEquals(0, ClockWidgetWind.beaufortForce(0.0))
        assertEquals(0, ClockWidgetWind.beaufortForce(0.5))
        assertEquals(1, ClockWidgetWind.beaufortForce(1.0))
        assertEquals(1, ClockWidgetWind.beaufortForce(5.0))
        assertEquals(2, ClockWidgetWind.beaufortForce(6.0))
        // 3.3 m/s, which is still force 2; a table kept in km/h would already say 3.
        assertEquals(2, ClockWidgetWind.beaufortForce(12.0))
        assertEquals(3, ClockWidgetWind.beaufortForce(13.0))
        assertEquals(3, ClockWidgetWind.beaufortForce(19.0))
        assertEquals(4, ClockWidgetWind.beaufortForce(20.0))
        assertEquals(4, ClockWidgetWind.beaufortForce(28.0))
        assertEquals(5, ClockWidgetWind.beaufortForce(29.0))
        assertEquals(5, ClockWidgetWind.beaufortForce(38.0))
        assertEquals(6, ClockWidgetWind.beaufortForce(39.0))
        assertEquals(6, ClockWidgetWind.beaufortForce(49.0))
        assertEquals(7, ClockWidgetWind.beaufortForce(50.0))
        assertEquals(11, ClockWidgetWind.beaufortForce(117.0))
        assertEquals(12, ClockWidgetWind.beaufortForce(118.0))
        assertEquals(12, ClockWidgetWind.beaufortForce(300.0))
    }

    @Test
    fun `what is not a speed has no force`() {
        assertNull(ClockWidgetWind.beaufortForce(-1.0))
        assertNull(ClockWidgetWind.beaufortForce(Double.NaN))
    }

    @Test
    fun `the widget and the search agree about every speed`() {
        val kilometresPerHour = unitNamed("km/h")!!
        for (tenths in 0..2000) {
            val kmh = tenths / 10.0
            assertEquals("$kmh km/h", beaufortForce(kmh, kilometresPerHour), ClockWidgetWind.beaufortForce(kmh))
        }
    }

    @Test
    fun `degrees become the point the wind comes from`() {
        assertEquals(0, ClockWidgetWind.compassPoint(0))
        assertEquals(0, ClockWidgetWind.compassPoint(22))
        assertEquals(1, ClockWidgetWind.compassPoint(23))
        assertEquals(1, ClockWidgetWind.compassPoint(45))
        assertEquals(1, ClockWidgetWind.compassPoint(67))
        assertEquals(2, ClockWidgetWind.compassPoint(68))
        assertEquals(2, ClockWidgetWind.compassPoint(90))
        assertEquals(3, ClockWidgetWind.compassPoint(135))
        assertEquals(4, ClockWidgetWind.compassPoint(180))
        assertEquals(5, ClockWidgetWind.compassPoint(225))
        assertEquals(6, ClockWidgetWind.compassPoint(270))
        assertEquals(7, ClockWidgetWind.compassPoint(315))
        assertEquals(7, ClockWidgetWind.compassPoint(337))
        assertEquals(0, ClockWidgetWind.compassPoint(338))
        assertEquals(0, ClockWidgetWind.compassPoint(359))
        assertEquals(0, ClockWidgetWind.compassPoint(360))
    }

    @Test
    fun `a variable or impossible direction is no point`() {
        assertNull(ClockWidgetWind.compassPoint(-1))
        assertNull(ClockWidgetWind.compassPoint(361))
    }

    @Test
    fun `each unit says the same wind its own way`() {
        assertEquals("5 Bft SW", wind(30.0, 225, WindUnit.BEAUFORT))
        assertEquals("8 m/s SW", wind(30.0, 225, WindUnit.METRES_PER_SECOND))
        assertEquals("30 km/h SW", wind(30.0, 225, WindUnit.KILOMETRES_PER_HOUR))
        assertEquals("16 kn SW", wind(30.0, 225, WindUnit.KNOTS))
        assertEquals("19 mph SW", wind(30.0, 225, WindUnit.MILES_PER_HOUR))
        assertNull(wind(30.0, 225, WindUnit.OFF))
    }

    @Test
    fun `round figures convert to round figures`() {
        assertEquals("10 m/s S", wind(36.0, 200, WindUnit.METRES_PER_SECOND))
        assertEquals("10 kn NE", wind(18.52, 45, WindUnit.KNOTS))
        assertEquals("10 mph NW", wind(16.09344, 315, WindUnit.MILES_PER_HOUR))
        assertEquals("100 km/h S", wind(100.0, 180, WindUnit.KILOMETRES_PER_HOUR))
    }

    @Test
    fun `no direction is given when none came, or when there is no name for it`() {
        assertEquals("5 Bft", wind(30.0, null, WindUnit.BEAUFORT))
        assertEquals("5 Bft", wind(30.0, -1, WindUnit.BEAUFORT))
        assertEquals("5 Bft", ClockWidgetWind.formatted(30.0, 225, WindUnit.BEAUFORT, emptyList()))
    }

    @Test
    fun `a speed shown as zero has no direction`() {
        assertEquals("0 Bft", wind(0.4, 225, WindUnit.BEAUFORT))
        assertEquals("0 m/s", wind(1.0, 225, WindUnit.METRES_PER_SECOND))
        assertEquals("1 Bft E", wind(1.0, 90, WindUnit.BEAUFORT))
        assertEquals("0 km/h", wind(0.0, 90, WindUnit.KILOMETRES_PER_HOUR))
    }

    @Test
    fun `what is not a speed says nothing`() {
        assertNull(wind(-1.0, 0, WindUnit.BEAUFORT))
        assertNull(wind(Double.NaN, 0, WindUnit.KILOMETRES_PER_HOUR))
    }
}
