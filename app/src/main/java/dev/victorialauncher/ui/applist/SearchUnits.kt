// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import kotlin.math.roundToInt

/** What a unit measures; only units of the same one convert into each other. */
internal enum class Quantity { LENGTH, MASS, TEMPERATURE, SPEED, PRESSURE, FORCE, TORQUE, VOLUME, AREA }

/**
 * A unit as [factor] of its quantity's base unit, plus [offset] for the temperature scales that
 * do not start at zero. The bases are the SI ones: metre, kilogram, kelvin, metre per second,
 * pascal, newton, newton metre, cubic metre and square metre.
 */
internal class MeasureUnit(
    /** As the answer writes it. */
    val symbol: String,
    val quantity: Quantity,
    val factor: Double,
    val offset: Double = 0.0,
    /** Other ways of typing it, matched exactly first and then regardless of case. */
    val aliases: List<String> = emptyList(),
) {
    fun toBase(amount: Double): Double = amount * factor + offset

    fun fromBase(amount: Double): Double = (amount - offset) / factor
}

private val MILLIMETRE = MeasureUnit("mm", Quantity.LENGTH, 0.001, aliases = listOf("millimetre", "millimeter", "millimetres", "millimeters"))
private val CENTIMETRE = MeasureUnit("cm", Quantity.LENGTH, 0.01, aliases = listOf("centimetre", "centimeter", "centimetres", "centimeters"))
private val METRE = MeasureUnit("m", Quantity.LENGTH, 1.0, aliases = listOf("metre", "meter", "metres", "meters"))
private val KILOMETRE = MeasureUnit("km", Quantity.LENGTH, 1000.0, aliases = listOf("kilometre", "kilometer", "kilometres", "kilometers"))
private val INCH = MeasureUnit("in", Quantity.LENGTH, 0.0254, aliases = listOf("inch", "inches", "\"", "″", "”"))
private val FOOT = MeasureUnit("ft", Quantity.LENGTH, 0.3048, aliases = listOf("foot", "feet", "'", "′", "’"))
private val YARD = MeasureUnit("yd", Quantity.LENGTH, 0.9144, aliases = listOf("yard", "yards"))
private val MILE = MeasureUnit("mi", Quantity.LENGTH, 1609.344, aliases = listOf("mile", "miles"))
private val NAUTICAL_MILE = MeasureUnit("nmi", Quantity.LENGTH, 1852.0)

private val GRAM = MeasureUnit("g", Quantity.MASS, 0.001, aliases = listOf("gram", "grams"))
private val KILOGRAM = MeasureUnit("kg", Quantity.MASS, 1.0, aliases = listOf("kilo", "kilos", "kilogram", "kilograms"))
private val TONNE = MeasureUnit("t", Quantity.MASS, 1000.0, aliases = listOf("tonne", "tonnes"))
private val POUND = MeasureUnit("lb", Quantity.MASS, 0.45359237, aliases = listOf("lbs", "pound", "pounds"))
private val OUNCE = MeasureUnit("oz", Quantity.MASS, 0.028349523125, aliases = listOf("ounce", "ounces"))

private val CELSIUS = MeasureUnit("°C", Quantity.TEMPERATURE, 1.0, 273.15, aliases = listOf("C", "ºC", "celsius"))
private val FAHRENHEIT = MeasureUnit(
    "°F", Quantity.TEMPERATURE, 5.0 / 9.0, 273.15 - 32.0 * 5.0 / 9.0,
    aliases = listOf("F", "ºF", "fahrenheit"),
)
private val KELVIN = MeasureUnit("K", Quantity.TEMPERATURE, 1.0, aliases = listOf("kelvin"))

private val METRE_PER_SECOND = MeasureUnit("m/s", Quantity.SPEED, 1.0)
private val KILOMETRE_PER_HOUR = MeasureUnit("km/h", Quantity.SPEED, 1.0 / 3.6, aliases = listOf("kmh", "kph", "kmph"))
private val KNOT = MeasureUnit("kn", Quantity.SPEED, 1852.0 / 3600.0, aliases = listOf("kt", "kts", "knot", "knots"))
private val MILE_PER_HOUR = MeasureUnit("mph", Quantity.SPEED, 0.44704)

/** Not a unit with a factor but a scale of bands, so it is converted through [beaufortForce]. */
private val BEAUFORT = MeasureUnit("Bft", Quantity.SPEED, 1.0, aliases = listOf("beaufort"))

private val PASCAL = MeasureUnit("Pa", Quantity.PRESSURE, 1.0)
private val KILOPASCAL = MeasureUnit("kPa", Quantity.PRESSURE, 1e3)
private val MEGAPASCAL = MeasureUnit("MPa", Quantity.PRESSURE, 1e6)
private val NEWTON_PER_SQUARE_MILLIMETRE = MeasureUnit("N/mm²", Quantity.PRESSURE, 1e6, aliases = listOf("N/mm2"))
private val BAR = MeasureUnit("bar", Quantity.PRESSURE, 1e5)
private val MILLIBAR = MeasureUnit("mbar", Quantity.PRESSURE, 100.0)
private val PSI = MeasureUnit("psi", Quantity.PRESSURE, 6894.757293168361)

private val NEWTON = MeasureUnit("N", Quantity.FORCE, 1.0)

// "KN" as well, since that is how kilonewtons are very often written; "kn" stays the knot.
private val KILONEWTON = MeasureUnit("kN", Quantity.FORCE, 1e3, aliases = listOf("KN"))
private val KILOGRAM_FORCE = MeasureUnit("kgf", Quantity.FORCE, 9.80665)
private val TONNE_FORCE = MeasureUnit("tf", Quantity.FORCE, 9806.65)
private val POUND_FORCE = MeasureUnit("lbf", Quantity.FORCE, 4.4482216152605)

private val NEWTON_METRE = MeasureUnit("N·m", Quantity.TORQUE, 1.0, aliases = listOf("Nm", "N.m", "N-m"))
private val KILONEWTON_METRE = MeasureUnit("kN·m", Quantity.TORQUE, 1e3, aliases = listOf("kNm", "KNm", "kN.m", "kN-m"))
private val POUND_FOOT = MeasureUnit(
    "lbf·ft", Quantity.TORQUE, 1.3558179483314004,
    aliases = listOf("lbft", "lb·ft", "lb-ft", "lbf-ft", "ft·lb", "ftlb", "ft-lb", "ft·lbf"),
)

private val MILLILITRE = MeasureUnit("ml", Quantity.VOLUME, 1e-6, aliases = listOf("mL"))
private val LITRE = MeasureUnit("L", Quantity.VOLUME, 1e-3, aliases = listOf("l", "litre", "liter", "litres", "liters"))
private val CUBIC_METRE = MeasureUnit("m³", Quantity.VOLUME, 1.0, aliases = listOf("m3"))
private val GALLON = MeasureUnit("gal", Quantity.VOLUME, 0.003785411784, aliases = listOf("gallon", "gallons"))

private val SQUARE_MILLIMETRE = MeasureUnit("mm²", Quantity.AREA, 1e-6, aliases = listOf("mm2"))
private val SQUARE_METRE = MeasureUnit("m²", Quantity.AREA, 1.0, aliases = listOf("m2"))
private val SQUARE_FOOT = MeasureUnit("ft²", Quantity.AREA, 0.09290304, aliases = listOf("ft2", "sqft"))
private val HECTARE = MeasureUnit("ha", Quantity.AREA, 1e4, aliases = listOf("hectare", "hectares"))
private val ACRE = MeasureUnit("acre", Quantity.AREA, 4046.8564224, aliases = listOf("acres"))

private val UNITS = listOf(
    MILLIMETRE, CENTIMETRE, METRE, KILOMETRE, INCH, FOOT, YARD, MILE, NAUTICAL_MILE,
    GRAM, KILOGRAM, TONNE, POUND, OUNCE,
    CELSIUS, FAHRENHEIT, KELVIN,
    METRE_PER_SECOND, KILOMETRE_PER_HOUR, KNOT, MILE_PER_HOUR, BEAUFORT,
    PASCAL, KILOPASCAL, MEGAPASCAL, NEWTON_PER_SQUARE_MILLIMETRE, BAR, MILLIBAR, PSI,
    NEWTON, KILONEWTON, KILOGRAM_FORCE, TONNE_FORCE, POUND_FORCE,
    NEWTON_METRE, KILONEWTON_METRE, POUND_FOOT,
    MILLILITRE, LITRE, CUBIC_METRE, GALLON,
    SQUARE_MILLIMETRE, SQUARE_METRE, SQUARE_FOOT, HECTARE, ACRE,
)

private fun MeasureUnit.spellings(): List<String> = listOf(symbol) + aliases

private val EXACT_UNITS: Map<String, MeasureUnit> = buildMap {
    UNITS.forEach { unit -> unit.spellings().forEach { put(it, unit) } }
}

/*
 * Case only matters where it tells two units apart: kN is a kilonewton and kn a knot, so "Kn" is
 * neither. Everywhere else a capital typed by the keyboard's own habit is ignored, and "M" is the
 * metre it would be in lower case.
 */
private val FOLDED_UNITS: Map<String, MeasureUnit> = UNITS
    .flatMap { unit -> unit.spellings().map { it.lowercase() to unit } }
    .groupBy({ it.first }, { it.second })
    .filterValues { units -> units.distinct().size == 1 }
    .mapValues { (_, units) -> units.first() }

/** The unit [name] spells, or null for a word that is not one or could be either of two. */
internal fun unitNamed(name: String): MeasureUnit? = EXACT_UNITS[name] ?: FOLDED_UNITS[name.lowercase()]

/**
 * What a unit typed on its own is shown in: the two its readers most likely want it in, which
 * is usually the other system's nearest units.
 */
private val COUNTERPARTS: Map<MeasureUnit, List<MeasureUnit>> = mapOf(
    MILLIMETRE to listOf(INCH, CENTIMETRE),
    CENTIMETRE to listOf(INCH, FOOT),
    METRE to listOf(FOOT, YARD),
    KILOMETRE to listOf(MILE, NAUTICAL_MILE),
    INCH to listOf(MILLIMETRE, CENTIMETRE),
    FOOT to listOf(METRE, CENTIMETRE),
    YARD to listOf(METRE, FOOT),
    MILE to listOf(KILOMETRE, NAUTICAL_MILE),
    NAUTICAL_MILE to listOf(KILOMETRE, MILE),
    GRAM to listOf(OUNCE, POUND),
    KILOGRAM to listOf(POUND, OUNCE),
    TONNE to listOf(KILOGRAM, POUND),
    POUND to listOf(KILOGRAM, GRAM),
    OUNCE to listOf(GRAM, POUND),
    CELSIUS to listOf(FAHRENHEIT, KELVIN),
    FAHRENHEIT to listOf(CELSIUS, KELVIN),
    KELVIN to listOf(CELSIUS, FAHRENHEIT),
    METRE_PER_SECOND to listOf(KILOMETRE_PER_HOUR, BEAUFORT),
    KILOMETRE_PER_HOUR to listOf(METRE_PER_SECOND, MILE_PER_HOUR),
    KNOT to listOf(METRE_PER_SECOND, BEAUFORT),
    MILE_PER_HOUR to listOf(KILOMETRE_PER_HOUR, METRE_PER_SECOND),
    BEAUFORT to listOf(METRE_PER_SECOND, KNOT),
    PASCAL to listOf(KILOPASCAL, PSI),
    KILOPASCAL to listOf(PSI, BAR),
    MEGAPASCAL to listOf(PSI, BAR),
    NEWTON_PER_SQUARE_MILLIMETRE to listOf(MEGAPASCAL, PSI),
    BAR to listOf(PSI, KILOPASCAL),
    MILLIBAR to listOf(KILOPASCAL, PSI),
    PSI to listOf(BAR, KILOPASCAL),
    NEWTON to listOf(KILOGRAM_FORCE, POUND_FORCE),
    KILONEWTON to listOf(TONNE_FORCE, POUND_FORCE),
    KILOGRAM_FORCE to listOf(NEWTON, POUND_FORCE),
    TONNE_FORCE to listOf(KILONEWTON, POUND_FORCE),
    POUND_FORCE to listOf(NEWTON, KILOGRAM_FORCE),
    NEWTON_METRE to listOf(POUND_FOOT, KILONEWTON_METRE),
    KILONEWTON_METRE to listOf(NEWTON_METRE, POUND_FOOT),
    POUND_FOOT to listOf(NEWTON_METRE, KILONEWTON_METRE),
    MILLILITRE to listOf(LITRE, GALLON),
    LITRE to listOf(GALLON, MILLILITRE),
    CUBIC_METRE to listOf(LITRE, GALLON),
    GALLON to listOf(LITRE, CUBIC_METRE),
    SQUARE_MILLIMETRE to listOf(SQUARE_METRE, SQUARE_FOOT),
    SQUARE_METRE to listOf(SQUARE_FOOT, HECTARE),
    SQUARE_FOOT to listOf(SQUARE_METRE, ACRE),
    HECTARE to listOf(ACRE, SQUARE_METRE),
    ACRE to listOf(HECTARE, SQUARE_METRE),
)

/** Words that put the unit wanted after the one given, as in "10 km to mi". */
private val INTO = setOf("to", "in", "into", "as", "=")

/** Significant digits a conversion is given to: enough to measure with, not so many as to read as noise. */
private const val CONVERSION_DIGITS = 6

/**
 * "10 km to mi", "10 km in mi", "10 km = mi" or "10 km mi" as one answer, and "10 km" on its own
 * as two, in its [COUNTERPARTS]. Empty for anything else, including units of different quantities.
 */
internal fun conversions(text: String): List<SearchAnswer> {
    val input = text.trim()
    if (input.isEmpty() || input.length > MAX_ANSWER_INPUT) return emptyList()
    val negative = input[0] == '-' || input[0] == '−'
    val read = readNumber(input, if (negative) 1 else 0) ?: return emptyList()
    val amount = if (negative) -read.value else read.value
    val words = input.substring(read.end).replace("=", " = ").trim()
        .split(WHITESPACE).filter { it.isNotEmpty() }
    val from: MeasureUnit
    val to: MeasureUnit?
    when {
        words.size == 1 -> {
            from = unitNamed(words[0]) ?: return emptyList()
            to = null
        }
        words.size == 2 -> {
            from = unitNamed(words[0]) ?: return emptyList()
            to = unitNamed(words[1]) ?: return emptyList()
        }
        words.size == 3 && words[1].lowercase() in INTO -> {
            from = unitNamed(words[0]) ?: return emptyList()
            to = unitNamed(words[2]) ?: return emptyList()
        }
        else -> return emptyList()
    }
    if (to != null && to.quantity != from.quantity) return emptyList()
    if (from === BEAUFORT) return fromBeaufort(amount, to)
    val targets = if (to != null) listOf(to) else COUNTERPARTS[from].orEmpty()
    val given = "${formatNumber(amount, 10)} ${from.symbol}"
    return targets.mapNotNull { target ->
        if (target === BEAUFORT) {
            beaufortForce(amount, from)?.let { force -> SearchAnswer.Wind(force, given, onScale = true) }
        } else {
            val converted = target.fromBase(from.toBase(amount))
            if (converted.isFinite()) {
                SearchAnswer.Value("${formatNumber(converted, CONVERSION_DIGITS)} ${target.symbol}", given)
            } else {
                null
            }
        }
    }
}

private val WHITESPACE = Regex("\\s+")

/** Number of forces on the Beaufort scale, 0 to 12. */
internal const val BEAUFORT_FORCES = 13

/** Where each force from 1 to 12 starts, in tenths of a metre per second: the WMO table. */
private val BEAUFORT_TENTHS = intArrayOf(3, 16, 34, 55, 80, 108, 139, 172, 208, 245, 285, 327)

/** The same table in knots, which sailors read it in and which does not round to the tenths above. */
private val BEAUFORT_KNOTS = intArrayOf(1, 4, 7, 11, 17, 22, 28, 34, 41, 48, 56, 64)

/**
 * The force a wind of [speed] in [unit] is, or null for a speed that is not one.
 *
 * Rounded to the table's own precision before it is looked up, so a speed on a boundary lands on
 * the force the table gives it rather than on whichever side the conversion's last digit fell.
 */
internal fun beaufortForce(speed: Double, unit: MeasureUnit): Int? {
    if (speed < 0.0 || unit.quantity != Quantity.SPEED || unit === BEAUFORT) return null
    if (unit === KNOT) {
        val knots = speed.roundToInt()
        return BEAUFORT_KNOTS.count { knots >= it }
    }
    val tenths = (unit.toBase(speed) * 10.0).roundToInt()
    return BEAUFORT_TENTHS.count { tenths >= it }
}

private fun fromBeaufort(amount: Double, to: MeasureUnit?): List<SearchAnswer> {
    val force = amount.toInt()
    if (force.toDouble() != amount || force !in 0 until BEAUFORT_FORCES) return emptyList()
    val targets = if (to != null) listOf(to) else COUNTERPARTS[BEAUFORT].orEmpty()
    return targets.mapNotNull { target ->
        if (target === BEAUFORT) null else SearchAnswer.Wind(force, "${beaufortRange(force, target)} ${target.symbol}", onScale = false)
    }
}

/** The speeds force [force] covers, in [unit]: "8.0–10.7" for 5 in metres per second. */
private fun beaufortRange(force: Int, unit: MeasureUnit): String {
    if (unit === KNOT) {
        return when (force) {
            0 -> "< ${BEAUFORT_KNOTS[0]}"
            BEAUFORT_FORCES - 1 -> "≥ ${BEAUFORT_KNOTS[force - 1]}"
            else -> "${BEAUFORT_KNOTS[force - 1]}–${BEAUFORT_KNOTS[force] - 1}"
        }
    }
    val lowTenths = if (force == 0) 0 else BEAUFORT_TENTHS[force - 1]
    val highTenths = if (force == BEAUFORT_FORCES - 1) null else BEAUFORT_TENTHS[force] - 1
    fun shown(tenths: Int): String = if (unit === METRE_PER_SECOND) {
        "${tenths / 10}.${tenths % 10}"
    } else {
        formatNumber(unit.fromBase(tenths / 10.0), 3)
    }
    return if (highTenths == null) "≥ ${shown(lowTenths)}" else "${shown(lowTenths)}–${shown(highTenths)}"
}
