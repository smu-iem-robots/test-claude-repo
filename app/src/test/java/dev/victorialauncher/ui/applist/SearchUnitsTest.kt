// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit conversions typed into the search, and the Beaufort scale both ways. */
class SearchUnitsTest {

    private fun value(shown: String, from: String) = SearchAnswer.Value(shown, from)

    @Test
    fun `a conversion asked for, however it is asked`() {
        val expected = listOf(value("16.0934 km", "10 mi"))
        assertEquals(expected, conversions("10 mi to km"))
        assertEquals(expected, conversions("10 mi in km"))
        assertEquals(expected, conversions("10 mi = km"))
        assertEquals(expected, conversions("10mi km"))
        assertEquals(expected, conversions("10 miles into km"))
    }

    @Test
    fun `lengths`() {
        assertEquals(listOf(value("30.48 cm", "12 in")), conversions("12\" to cm"))
        assertEquals(listOf(value("1.8288 m", "6 ft")), conversions("6' to m"))
        assertEquals(listOf(value("150 cm", "1.5 m")), conversions("1,5 m to cm"))
        assertEquals(listOf(value("25.4 mm", "1 in")), conversions("1 inch to mm"))
    }

    @Test
    fun `temperatures, which do not start at zero`() {
        assertEquals(listOf(value("68 °F", "20 °C")), conversions("20 C to F"))
        assertEquals(listOf(value("-40 °F", "-40 °C")), conversions("-40 °C in °F"))
        assertEquals(listOf(value("37.7778 °C", "100 °F")), conversions("100 F to C"))
        assertEquals(listOf(value("-273.15 °C", "0 K")), conversions("0 K to C"))
        assertEquals(listOf(value("26.85 °C", "300 K")), conversions("300 kelvin to C"))
    }

    @Test
    fun `a lowercase k is thousands or a resolution, not kelvin`() {
        listOf("4k", "5k", "10k", "10 k to c").forEach { assertTrue(it, conversions(it).isEmpty()) }
    }

    @Test
    fun `NM is the nautical mile a chart writes`() {
        assertEquals(
            listOf(value("18.52 km", "10 nmi"), value("11.5078 mi", "10 nmi")),
            conversions("10 NM"),
        )
    }

    @Test
    fun `pressure, force and torque as an engineer writes them`() {
        assertEquals(listOf(value("2.06843 bar", "30 psi")), conversions("30 psi to bar"))
        assertEquals(listOf(value("51488.4 psi", "355 N/mm²")), conversions("355 N/mm2 to psi"))
        assertEquals(listOf(value("355 MPa", "355 N/mm²")), conversions("355 N/mm² to MPa"))
        assertEquals(listOf(value("10.1972 tf", "100 kN")), conversions("100 kN to tf"))
        assertEquals(listOf(value("73.7562 lbf·ft", "100 N·m")), conversions("100 Nm to lbft"))
        assertEquals(listOf(value("67.7909 N·m", "50 lbf·ft")), conversions("50 ft·lb to Nm"))
    }

    @Test
    fun `volume and area`() {
        assertEquals(listOf(value("3.78541 L", "1 gal")), conversions("1 gal to l"))
        assertEquals(listOf(value("2000 L", "2 m³")), conversions("2 m3 to L"))
        assertEquals(listOf(value("2.47105 acre", "1 ha")), conversions("1 ha to acre"))
        assertEquals(listOf(value("107.639 ft²", "10 m²")), conversions("10 m2 to ft2"))
    }

    @Test
    fun `a unit on its own is shown in its two counterparts`() {
        assertEquals(
            listOf(value("16.0934 km", "10 mi"), value("8.68976 nmi", "10 mi")),
            conversions("10 mi"),
        )
        assertEquals(
            listOf(value("176.37 lb", "80 kg"), value("2821.92 oz", "80 kg")),
            conversions("80 kg"),
        )
        assertEquals(
            listOf(value("68 °F", "20 °C"), value("293.15 K", "20 °C")),
            conversions("20 °C"),
        )
        assertEquals(
            listOf(value("14.5038 psi", "1 bar"), value("100 kPa", "1 bar")),
            conversions("1 bar"),
        )
    }

    @Test
    fun `a wind speed comes with its Beaufort force`() {
        assertEquals(
            listOf(value("32.4 km/h", "9 m/s"), SearchAnswer.Wind(5, "9 m/s", onScale = true)),
            conversions("9 m/s"),
        )
        assertEquals(listOf(SearchAnswer.Wind(5, "9 m/s", onScale = true)), conversions("9 m/s to bft"))
    }

    @Test
    fun `a Beaufort force comes back as the speeds it covers`() {
        assertEquals(
            listOf(
                SearchAnswer.Wind(5, "8.0–10.7 m/s", onScale = false),
                SearchAnswer.Wind(5, "17–21 kn", onScale = false),
            ),
            conversions("5 bft"),
        )
        assertEquals(listOf(SearchAnswer.Wind(5, "28.8–38.5 km/h", onScale = false)), conversions("5 Bft to km/h"))
        assertEquals(
            listOf(
                SearchAnswer.Wind(0, "0.0–0.2 m/s", onScale = false),
                SearchAnswer.Wind(0, "< 1 kn", onScale = false),
            ),
            conversions("0 bft"),
        )
        assertEquals(
            listOf(
                SearchAnswer.Wind(12, "≥ 32.7 m/s", onScale = false),
                SearchAnswer.Wind(12, "≥ 64 kn", onScale = false),
            ),
            conversions("12 beaufort"),
        )
    }

    @Test
    fun `there is no force 13, and no half force`() {
        assertTrue(conversions("13 bft").isEmpty())
        assertTrue(conversions("5.5 bft").isEmpty())
        assertTrue(conversions("-1 bft").isEmpty())
    }

    @Test
    fun `speeds land on the force the table gives them`() {
        val metresPerSecond = unitNamed("m/s")!!
        val knots = unitNamed("kn")!!
        assertEquals(0, beaufortForce(0.2, metresPerSecond))
        assertEquals(1, beaufortForce(0.3, metresPerSecond))
        assertEquals(6, beaufortForce(10.8, metresPerSecond))
        assertEquals(12, beaufortForce(40.0, metresPerSecond))
        // 21 knots is 10.8 m/s, which the metric table puts in force 6; in knots it is 5.
        assertEquals(5, beaufortForce(21.0, knots))
        assertEquals(6, beaufortForce(22.0, knots))
        assertEquals(7, beaufortForce(50.0, unitNamed("km/h")!!))
        assertNull(beaufortForce(-1.0, metresPerSecond))
    }

    @Test
    fun `case is ignored except where it tells two units apart`() {
        assertEquals("kN", unitNamed("kN")?.symbol)
        assertEquals("kN", unitNamed("KN")?.symbol)
        assertEquals("kn", unitNamed("kn")?.symbol)
        assertNull(unitNamed("Kn"))
        assertEquals("m", unitNamed("M")?.symbol)
        assertEquals("MPa", unitNamed("mpa")?.symbol)
        assertEquals("°C", unitNamed("c")?.symbol)
        assertEquals("N·m", unitNamed("nm")?.symbol)
        assertEquals("N·m", unitNamed("Nm")?.symbol)
        assertEquals("nmi", unitNamed("NM")?.symbol)
        assertNull(unitNamed("nM"))
        assertEquals("K", unitNamed("K")?.symbol)
        assertNull(unitNamed("k"))
        assertEquals("L", unitNamed("l")?.symbol)
        assertEquals("t", unitNamed("T")?.symbol)
    }

    @Test
    fun `every spelling the search promises is a unit`() {
        listOf(
            "mm", "cm", "m", "km", "in", "inch", "\"", "ft", "'", "yd", "mi", "nmi", "NM",
            "g", "kg", "t", "tonne", "lb", "lbs", "oz",
            "°C", "C", "°F", "F", "K",
            "m/s", "km/h", "kmh", "kph", "kn", "kt", "knots", "mph", "bft",
            "Pa", "kPa", "MPa", "N/mm2", "N/mm²", "bar", "mbar", "psi",
            "N", "kN", "kgf", "tf", "lbf",
            "Nm", "N·m", "kNm", "lbft", "ft·lb",
            "ml", "l", "L", "m3", "m³", "gal",
            "mm2", "m2", "m²", "ft2", "ha", "acre",
        ).forEach { assertNotNull(it, unitNamed(it)) }
    }

    @Test
    fun `a figure too large to convert is not answered with infinity`() {
        assertTrue(conversions("1e308 mi to mm").isEmpty())
    }

    @Test
    fun `units of different things do not convert`() {
        assertTrue(conversions("10 kg to m").isEmpty())
        assertTrue(conversions("20 °C to km/h").isEmpty())
    }

    @Test
    fun `anything else is not a conversion`() {
        listOf("10", "km", "10 apples", "10 km to mi please", "Spotify", "2048 game", "", "to km").forEach {
            assertTrue(it, conversions(it).isEmpty())
        }
    }
}
