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
 * language but for the decimal mark, and that follows the query's: "2,5 bar" is answered in commas.
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
    // "2,5 bar" is answered "36,2594 psi": whoever writes a decimal comma reads one, and the
    // answer is what gets copied into their spreadsheet, where 36.2594 is a date or text.
    val comma = typedDecimalComma(input)
    steelWeight(input)?.let { return listOf(it.withDecimalComma(comma, detailToo = true)) }
    conversions(input).takeIf { it.isNotEmpty() }?.let { found ->
        return found.take(2).map { it.withDecimalComma(comma, detailToo = true) }
    }
    // 123-456-789 is a phone number before it is a subtraction, and the screen offers to call it.
    if (phoneNumber(input) != null) return emptyList()
    return when (val result = calculate(input)) {
        // The detail is the sum as typed, which is already written the way its writer writes.
        is Calculation.Result -> listOf(
            SearchAnswer.Value(formatAnswer(result.value), input).withDecimalComma(comma, detailToo = false),
        )
        Calculation.DividedByZero -> listOf(SearchAnswer.DividedByZero(input))
        null -> emptyList()
    }
}

/**
 * [this] with its figures' decimal points made commas when [comma], in the detail as well when
 * [detailToo] because the detail was written here rather than typed. Unit symbols are spelled
 * with '·' rather than a point, so a point is only ever a figure's.
 */
private fun SearchAnswer.withDecimalComma(comma: Boolean, detailToo: Boolean): SearchAnswer {
    if (!comma) return this
    return when (this) {
        is SearchAnswer.Value -> copy(
            value = value.replace('.', ','),
            detail = if (detailToo) detail.replace('.', ',') else detail,
        )
        is SearchAnswer.Wind -> copy(speed = speed.replace('.', ','))
        else -> this
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

/**
 * The structural grades as a drawing writes them: "S355", "S355J2+N", "S420ML". Each is a carbon
 * steel, so naming one says what naming steel says.
 */
private val STEEL_GRADE = Regex("^s(?:235|275|355|420|460|690)[a-z0-9+]*$")

/**
 * The x in 2000x1000x20, and its neighbours, only after a figure so "inox" keeps its x. The
 * figure may carry its unit up to the x, as the 2m in "2mx1mx20mm" does.
 */
private val DIMENSION_SEPARATOR = Regex("(?<=[0-9.,]|[0-9][cm]?m)\\s*[x×*]\\s*(?=[0-9.,])")

/** How a drawing marks a diameter, which a size may start with: "pipe Ø508x20". */
private val DIAMETER_SIGNS = charArrayOf('ø', '⌀')

/** The units a size may say it is in, millimetres first so 20mm is not taken for 20 metres. */
private val SIZE_UNITS = listOf("mm" to 1.0, "cm" to 10.0, "m" to 1000.0)

/**
 * [word] as a size in millimetres, or null when it is not one. Millimetres unless it says
 * otherwise, since that is what a drawing is dimensioned in; a length is the one people give in
 * metres, and "pipe 508x20x12m" says so.
 */
private fun millimetres(word: String): Double? {
    val figure = word.trimStart(*DIAMETER_SIGNS)
    val unit = SIZE_UNITS.firstOrNull { (suffix, _) -> figure.endsWith(suffix) }
        ?: return wholeNumber(figure)
    return wholeNumber(figure.dropLast(unit.first.length))?.let { it * unit.second }
}

/**
 * What a plate, pipe or round bar weighs, from its sizes in millimetres.
 *
 * "plate 2000x1000x20" is length × width × thickness, and a thickness alone is the plate's
 * weight per square metre; "pipe 508x20" is outer diameter × wall, per metre, and a third figure
 * is its length; "round 50" is a solid bar's diameter, likewise. A pipe is the wall's mean
 * circumference times its thickness, π·(D−t)·t, which is exact for a tube, so a rolled can of a
 * monopile weighs what this says it does.
 */
internal fun steelWeight(text: String): SearchAnswer.Value? {
    val words = DIMENSION_SEPARATOR.replace(text.trim().lowercase(), " ")
        .split(STEEL_SPACE).filter { it.isNotEmpty() }
    var sectionWord: String? = null
    var section: Section? = null
    var density: Double? = null
    val sizes = mutableListOf<Double>()
    for (word in words) {
        val size = millimetres(word)
        val material = MATERIAL_WORDS[word] ?: STEEL_DENSITY.takeIf { STEEL_GRADE.matches(word) }
        when {
            size != null -> {
                // The shape first, as a cutting list writes it. "Bar" is a unit of pressure
                // too, and with the shape allowed anywhere "5 bar" came back as the weight of
                // a 5 mm rod instead of in psi.
                if (section == null || size <= 0.0) return null
                sizes += size
            }
            word in SECTION_WORDS -> {
                if (section != null) return null
                section = SECTION_WORDS[word]
                sectionWord = word
            }
            material != null -> {
                // "steel S355" names one material twice, which is fine; "S355 alu" names two.
                if (density != null && density != material) return null
                density = material
            }
            word == "mm" || word.all { it in DIAMETER_SIGNS } -> Unit
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
    // The weight, what it is a weight per when it is not the whole piece's, and the sizes.
    val (kilograms, per, described) = when (shape) {
        Section.PLATE -> when (sizes.size) {
            // How plate is ordered and priced before anyone has cut it to a size.
            1 -> Triple(metres[0] * rho, "/m²", figures)
            3 -> Triple(metres[0] * metres[1] * metres[2] * rho, "", figures)
            else -> return null
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
                Triple(kilogramsPerMetre * metres[2], "", "Ø$figures")
            } else {
                Triple(kilogramsPerMetre, "/m", "Ø$figures")
            }
        }
        Section.ROUND -> {
            if (sizes.size !in 1..2) return null
            val diameter = metres[0]
            val kilogramsPerMetre = PI / 4.0 * diameter * diameter * rho
            if (sizes.size == 2) {
                Triple(kilogramsPerMetre * metres[1], "", "Ø$figures")
            } else {
                Triple(kilogramsPerMetre, "/m", "Ø$figures")
            }
        }
    }
    // 1e200 is a number as far as the reader is concerned, and three of them multiply past what
    // a double holds.
    if (!kilograms.isFinite()) return null
    return SearchAnswer.Value(formatWeight(kilograms) + per, "$described mm · ${formatNumber(rho, 10)} kg/m³")
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
