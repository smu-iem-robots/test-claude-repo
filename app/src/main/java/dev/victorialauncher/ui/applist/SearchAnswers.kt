// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import kotlin.math.PI
import kotlin.random.Random

/**
 * Something the search box can answer itself, shown above the apps rather than among them.
 *
 * Words are not spelled out here: the parts of an answer that are prose — a coin's side, a
 * Beaufort force's name — are carried as which one it is, and the screen puts them into words
 * from its resources. What is spelled out is numbers and unit symbols, which read the same in any
 * language.
 */
internal sealed interface SearchAnswer {
    /** Rolled again by a tap rather than copied, since the roll is the whole of what it is for. */
    val rerolls: Boolean get() = false

    /** A figure with its unit, shown large, and what it was worked out from, shown small. */
    data class Value(
        val value: String,
        val detail: String,
        override val rerolls: Boolean = false,
    ) : SearchAnswer

    /** A sum that divides by zero, which deserves better than nothing. */
    data class DividedByZero(val expression: String) : SearchAnswer

    /**
     * A wind speed against the Beaufort scale. [onScale] when the force is the answer and [speed]
     * the question; otherwise [speed] is the range force [force] covers.
     */
    data class Wind(val force: Int, val speed: String, val onScale: Boolean) : SearchAnswer

    data class Coin(val heads: Boolean) : SearchAnswer {
        override val rerolls: Boolean get() = true
    }

    /** One of the Magic 8-Ball's twenty answers, by its place in the classic list. */
    data class EightBall(val index: Int) : SearchAnswer {
        override val rerolls: Boolean get() = true
    }

    /** One of the lines the launcher answers its own name with. */
    data class Hello(val index: Int) : SearchAnswer {
        override val rerolls: Boolean get() = true
    }

    data object MeaningOfLife : SearchAnswer
}

/** How many answers the Magic 8-Ball has; the screen's list of them must hold this many. */
internal const val EIGHT_BALL_ANSWERS = 20

/** How many lines the launcher answers its own name with; likewise. */
internal const val HELLO_LINES = 4

/**
 * At most two answers for [text], or none.
 *
 * Each kind only recognises its own shape of query — a keyword for the fun ones and the steel, a
 * number and a unit for a conversion, arithmetic for the calculator — so trying them in turn is
 * not a contest. [random] is asked for every roll, so a test can pin the dice down.
 *
 * Runs on every keystroke of whatever is typed, so nothing it meets may take the launcher down:
 * a parser that trips over a query answers nothing rather than throwing.
 */
internal fun searchAnswers(text: String, random: Random): List<SearchAnswer> =
    runCatching { answersFor(text.trim(), random) }.getOrElse { emptyList() }

private fun answersFor(input: String, random: Random): List<SearchAnswer> {
    if (input.isEmpty() || input.length > MAX_ANSWER_INPUT) return emptyList()
    funAnswer(input, random)?.let { return listOf(it) }
    steelWeight(input)?.let { return listOf(it) }
    conversions(input).takeIf { it.isNotEmpty() }?.let { return it.take(2) }
    // 123-456-789 is a phone number before it is a subtraction, and the screen offers to call it.
    if (phoneNumber(input) != null) return emptyList()
    return when (val result = calculate(input)) {
        is Calculation.Result -> listOf(SearchAnswer.Value(formatNumber(result.value, 10), input))
        Calculation.DividedByZero -> listOf(SearchAnswer.DividedByZero(input))
        null -> emptyList()
    }
}

// ---- Steel ----

private enum class Section { PLATE, PIPE, ROUND }

private val SECTION_WORDS = mapOf(
    "plate" to Section.PLATE, "pl" to Section.PLATE, "sheet" to Section.PLATE,
    "pipe" to Section.PIPE, "tube" to Section.PIPE, "can" to Section.PIPE,
    "bar" to Section.ROUND, "round" to Section.ROUND,
)

/** Densities in kg/m³. Structural steel unless a material is named. */
private const val STEEL_DENSITY = 7850.0

private val MATERIAL_WORDS = mapOf(
    "steel" to STEEL_DENSITY,
    "stainless" to 8000.0, "inox" to 8000.0,
    "alu" to 2700.0, "aluminium" to 2700.0, "aluminum" to 2700.0,
)

/** The x in 2000x1000x20, and its neighbours, only between two figures so "inox" keeps its x. */
private val DIMENSION_SEPARATOR = Regex("(?<=[0-9.,])\\s*[x×*]\\s*(?=[0-9.,])")

/**
 * What a plate, pipe or round bar weighs, from its sizes in millimetres.
 *
 * "plate 2000x1000x20" is length × width × thickness; "pipe 508x20" is outer diameter × wall,
 * per metre, and a third figure is its length; "round 50" is a solid bar's diameter, likewise.
 * A pipe is the wall's mean circumference times its thickness, π·(D−t)·t, which is exact for a
 * tube, so a rolled can of a monopile weighs what this says it does.
 */
internal fun steelWeight(text: String): SearchAnswer.Value? {
    val words = DIMENSION_SEPARATOR.replace(text.trim().lowercase(), " ")
        .split(STEEL_SPACE).filter { it.isNotEmpty() }
    var sectionWord: String? = null
    var section: Section? = null
    var density: Double? = null
    val sizes = mutableListOf<Double>()
    for (word in words) {
        val size = wholeNumber(word.removeSuffix("mm"))
        when {
            size != null -> {
                if (size <= 0.0) return null
                sizes += size
            }
            word in SECTION_WORDS -> {
                if (section != null) return null
                section = SECTION_WORDS[word]
                sectionWord = word
            }
            word in MATERIAL_WORDS -> {
                if (density != null) return null
                density = MATERIAL_WORDS[word]
            }
            word == "mm" -> Unit
            else -> return null
        }
    }
    val shape = section ?: return null
    // "round" is the calculator's as well. Rounding a whole number changes nothing, so "round 50"
    // can only be a bar; "round 2.5" is someone rounding, and is left for the calculator.
    if (sectionWord == "round" && sizes.size == 1 && sizes[0] % 1.0 != 0.0) return null
    val rho = density ?: STEEL_DENSITY
    val metres = sizes.map { it / 1000.0 }
    val figures = sizes.joinToString(" × ") { formatNumber(it, 10) }
    // The weight, whether it is per metre for want of a length, and the sizes as given.
    val (kilograms, perMetre, described) = when (shape) {
        Section.PLATE -> {
            if (sizes.size != 3) return null
            Triple(metres[0] * metres[1] * metres[2] * rho, false, figures)
        }
        Section.PIPE -> {
            if (sizes.size !in 2..3) return null
            val diameter = metres[0]
            val wall = metres[1]
            // A wall of half the diameter is a solid bar, which the formula still gets right;
            // anything thicker is not a pipe.
            if (wall * 2.0 > diameter) return null
            val kilogramsPerMetre = PI * (diameter - wall) * wall * rho
            if (sizes.size == 3) {
                Triple(kilogramsPerMetre * metres[2], false, "Ø$figures")
            } else {
                Triple(kilogramsPerMetre, true, "Ø$figures")
            }
        }
        Section.ROUND -> {
            if (sizes.size !in 1..2) return null
            val diameter = metres[0]
            val kilogramsPerMetre = PI / 4.0 * diameter * diameter * rho
            if (sizes.size == 2) {
                Triple(kilogramsPerMetre * metres[1], false, "Ø$figures")
            } else {
                Triple(kilogramsPerMetre, true, "Ø$figures")
            }
        }
    }
    // 1e200 is a number as far as the reader is concerned, and three of them multiply past what
    // a double holds.
    if (!kilograms.isFinite()) return null
    val weight = formatWeight(kilograms) + if (perMetre) "/m" else ""
    return SearchAnswer.Value(weight, "$described mm · ${formatNumber(rho, 10)} kg/m³")
}

private val STEEL_SPACE = Regex("\\s+")

/**
 * Kilograms to four figures, and tonnes to two decimals from a tonne up, which is how a weight
 * is read off a drawing or a crane chart.
 */
internal fun formatWeight(kilograms: Double): String {
    val inKilograms = formatNumber(kilograms, 4)
    val rounded = inKilograms.toDoubleOrNull() ?: kilograms
    return if (rounded >= 1000.0) "${formatFixed(kilograms / 1000.0, 2)} t" else "$inKilograms kg"
}

// ---- Fun ----

private val COIN_WORDS = setOf("coin", "flip", "flip a coin", "coin flip", "toss a coin", "heads or tails")

private val DIE_WORDS = setOf("dice", "die", "roll", "roll a die", "roll the dice", "roll dice")

/** "d20", "2d6", "roll 3d8": how many dice, and how many sides each. */
private val DICE = Regex("^(?:roll )?(\\d{0,2})d(\\d{1,4})$")

private const val MOST_DICE = 20
private const val MOST_SIDES = 1000

private val EIGHT_BALL_WORDS = setOf(
    "8ball", "8 ball", "8-ball", "magic 8", "magic8", "magic 8 ball", "magic 8-ball", "magic 8ball",
)

/** How a yes-or-no question starts, for the 8-Ball to answer one that ends in a question mark. */
private val QUESTION_STARTS = listOf("should i ", "will ", "is ", "am i ", "do i ")

private val SPACES = Regex("\\s+")

/**
 * The answers that are there to be played with. Spelled as exact phrases rather than searched
 * for inside a query, so an app called Coinbase is still just an app.
 */
internal fun funAnswer(text: String, random: Random): SearchAnswer? {
    val said = text.trim().lowercase().replace(SPACES, " ")
    val plain = said.removeSuffix("?").trim()
    when {
        said in COIN_WORDS || plain in COIN_WORDS -> return SearchAnswer.Coin(random.nextBoolean())
        said in DIE_WORDS -> return dice(1, 6, random)
        said in EIGHT_BALL_WORDS ||
            (said.endsWith("?") && QUESTION_STARTS.any { said.startsWith(it) }) ->
            return SearchAnswer.EightBall(random.nextInt(EIGHT_BALL_ANSWERS))
        said == "victoria" -> return SearchAnswer.Hello(random.nextInt(HELLO_LINES))
        plain.removePrefix("what is ").removePrefix("what's ").removePrefix("the ") == "meaning of life" ->
            return SearchAnswer.MeaningOfLife
    }
    val match = DICE.matchEntire(said) ?: return null
    val count = match.groupValues[1].ifEmpty { "1" }.toInt()
    val sides = match.groupValues[2].toInt()
    if (count !in 1..MOST_DICE || sides !in 2..MOST_SIDES) return null
    return dice(count, sides, random)
}

private fun dice(count: Int, sides: Int, random: Random): SearchAnswer.Value {
    val rolls = List(count) { random.nextInt(1, sides + 1) }
    val name = if (count == 1) "d$sides" else "${count}d$sides"
    val detail = if (count == 1) name else "$name · ${rolls.joinToString(" + ")}"
    return SearchAnswer.Value(rolls.sum().toString(), detail, rerolls = true)
}
