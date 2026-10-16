// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import kotlin.math.roundToInt

/** What a clock widget says the wind in, if it says it at all. */
enum class WindUnit { OFF, BEAUFORT, METRES_PER_SECOND, KILOMETRES_PER_HOUR, KNOTS, MILES_PER_HOUR }

/**
 * The wind as one short piece of the weather row: "5 Bft SW", "8 m/s SW".
 *
 * Plain arithmetic with no Android in it, so it is tested on its own. The speed always arrives in
 * km/h, because that is what Gadgetbridge's weather broadcast carries.
 */
object ClockWidgetWind {

    /** km/h to m/s, written the way the search's own conversion multiplies, so both round alike. */
    private const val KMH_TO_MS = 1.0 / 3.6
    private const val KMH_PER_KNOT = 1.852
    private const val KMH_PER_MPH = 1.609344

    /**
     * Where each force from 1 to 12 starts, in tenths of a metre per second: the WMO table.
     *
     * The same table the search answers Beaufort questions from. The WMO gives it in metres per
     * second and knots, not km/h, so km/h is converted rather than looked up in a km/h table of
     * its own — one of those puts 12 km/h in force 3 where 3.3 m/s is force 2, and the widget
     * would then disagree with the search about the same wind.
     */
    private val BEAUFORT_TENTHS = intArrayOf(3, 16, 34, 55, 80, 108, 139, 172, 208, 245, 285, 327)

    /** Eight, clockwise from north: as many as fit in two letters. */
    const val COMPASS_POINTS = 8

    /**
     * The Beaufort force of [kmh], or null for something that is not a speed.
     *
     * Rounded to the table's tenths before it is looked up, so a speed on a boundary lands where
     * the table puts it rather than on whichever side the conversion's last digit fell.
     */
    fun beaufortForce(kmh: Double): Int? {
        if (kmh.isNaN() || kmh < 0.0) return null
        val tenths = (kmh * KMH_TO_MS * 10.0).roundToInt()
        return BEAUFORT_TENTHS.count { tenths >= it }
    }

    /**
     * Which of the [COMPASS_POINTS] the wind comes from, 0 being north, or null for a direction
     * that is not one.
     *
     * Breezy sends -1 for a wind that keeps changing direction, which is not a point either.
     */
    fun compassPoint(degrees: Int): Int? =
        if (degrees in 0..360) (degrees * 2 + 45) / 90 % COMPASS_POINTS else null

    /**
     * [kmh] in [unit], followed by where it comes from when that is known.
     *
     * Whole numbers only: a forecast is not good to a tenth, and the row has to fit beside the
     * temperature. [compass] is the points' names in order from north; a direction it has no name
     * for is left off rather than guessed at.
     */
    fun formatted(kmh: Double, degrees: Int?, unit: WindUnit, compass: List<String>): String? {
        if (kmh.isNaN() || kmh < 0.0) return null
        val (amount, symbol) = when (unit) {
            WindUnit.OFF -> return null
            WindUnit.BEAUFORT -> (beaufortForce(kmh) ?: return null) to "Bft"
            WindUnit.METRES_PER_SECOND -> (kmh * KMH_TO_MS).roundToInt() to "m/s"
            WindUnit.KILOMETRES_PER_HOUR -> kmh.roundToInt() to "km/h"
            WindUnit.KNOTS -> (kmh / KMH_PER_KNOT).roundToInt() to "kn"
            WindUnit.MILES_PER_HOUR -> (kmh / KMH_PER_MPH).roundToInt() to "mph"
        }
        val point = degrees?.let { compassPoint(it) }?.let { compass.getOrNull(it) }
        // A calm has no direction worth giving, and "0 m/s SW" reads as a contradiction.
        return if (amount == 0 || point == null) "$amount $symbol" else "$amount $symbol $point"
    }
}
