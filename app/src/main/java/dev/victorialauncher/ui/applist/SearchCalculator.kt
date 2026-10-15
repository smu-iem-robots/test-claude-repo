// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Longest query any of the answers will read. A search box is for a few words, and a cap keeps a
 * pasted page from being walked through on every keystroke.
 */
internal const val MAX_ANSWER_INPUT = 160

/** How deep brackets and signs may nest before the input is taken for something other than a sum. */
private const val MAX_DEPTH = 40

/** A number read out of a query, and where it stopped. */
internal class ReadNumber(
    val value: Double,
    val end: Int,
    /** Whether the whole part has a leading zero, as "06" or "020" do, which a sum never has. */
    val zeroPadded: Boolean,
)

/**
 * Reads a decimal number at [start]: digits, a fraction after '.' or ',', and an exponent.
 *
 * The comma is a decimal point only where it cannot be a thousands separator. "2,5" is two and a
 * half to most of Europe, and nothing else; "1,500" is fifteen hundred to most of the rest of the
 * world, and would be a strange way to write one and a half. So a comma followed by exactly three
 * digits, after a lead group that could start a grouped number, is read as grouping, and any other
 * comma between digits as the decimal point.
 */
internal fun readNumber(text: String, start: Int): ReadNumber? {
    var i = start
    val whole = StringBuilder()
    while (i < text.length && text[i] in '0'..'9') {
        whole.append(text[i])
        i++
    }
    var grouped = false
    if (whole.length in 1..3 && whole[0] != '0') {
        while (i < text.length && text[i] == ',' && isThousandsGroup(text, i + 1)) {
            whole.append(text, i + 1, i + 4)
            i += 4
            grouped = true
        }
    }
    var fraction = ""
    val mark = text.getOrNull(i)
    if ((mark == '.' || (mark == ',' && !grouped)) && text.getOrNull(i + 1)?.let { it in '0'..'9' } == true) {
        val begin = i + 1
        i = begin
        while (i < text.length && text[i] in '0'..'9') i++
        fraction = text.substring(begin, i)
    }
    if (whole.isEmpty() && fraction.isEmpty()) return null
    var exponent = ""
    if (text.getOrNull(i) == 'e' || text.getOrNull(i) == 'E') {
        var j = i + 1
        val sign = text.getOrNull(j)
        if (sign == '-' || sign == '+') j++
        val digitsFrom = j
        while (j < text.length && text[j] in '0'..'9') j++
        if (j > digitsFrom) {
            exponent = "e" + text.substring(i + 1, j)
            i = j
        }
    }
    val literal = (if (whole.isEmpty()) "0" else whole.toString()) +
        (if (fraction.isEmpty()) "" else ".$fraction") + exponent
    val value = literal.toDoubleOrNull() ?: return null
    if (value.isNaN() || value.isInfinite()) return null
    return ReadNumber(value, i, zeroPadded = whole.length > 1 && whole[0] == '0')
}

private fun isThousandsGroup(text: String, from: Int): Boolean {
    if (from + 3 > text.length) return false
    for (k in from until from + 3) if (text[k] !in '0'..'9') return false
    return text.getOrNull(from + 3)?.let { it in '0'..'9' } != true
}

/** [text] as one number and nothing else, or null. */
internal fun wholeNumber(text: String): Double? =
    readNumber(text, 0)?.takeIf { it.end == text.length }?.value

/**
 * [value] to [significant] digits, with no trailing zeros, and in scientific notation once it is
 * too large or too small to read as digits.
 *
 * Rounded in decimal rather than printed from the double, which is what keeps 0.1 + 0.2 from
 * answering 0.30000000000000004.
 */
internal fun formatNumber(value: Double, significant: Int): String {
    if (value == 0.0) return "0"
    if (value.isNaN() || value.isInfinite()) return value.toString()
    val rounded = BigDecimal(value).round(MathContext(significant, RoundingMode.HALF_UP))
    val magnitude = rounded.abs()
    if (magnitude >= SCIENTIFIC_ABOVE || magnitude < SCIENTIFIC_BELOW) {
        val stripped = rounded.stripTrailingZeros()
        val exponent = stripped.precision() - stripped.scale() - 1
        val mantissa = stripped.movePointLeft(exponent).stripTrailingZeros().toPlainString()
        return "${mantissa}E$exponent"
    }
    return rounded.stripTrailingZeros().toPlainString()
}

private val SCIENTIFIC_ABOVE = BigDecimal("1E15")
private val SCIENTIFIC_BELOW = BigDecimal("1E-6")

/** [value] with exactly [decimals] places, for figures read as a fixed format, like 1.44 t. */
internal fun formatFixed(value: Double, decimals: Int): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    return BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString()
}

/** What a sum came to. */
internal sealed interface Calculation {
    data class Result(val value: Double) : Calculation
    data object DividedByZero : Calculation
}

/**
 * Works out [text] as a sum, or null when it is not one.
 *
 * Only something that does arithmetic counts: an operator or a function. A number on its own is
 * not a sum, and nor is a constant — "e" is the start of a great many app names, and answering
 * 2.718281828 to every one of them would be the calculator getting in the way of the search.
 *
 * A recursive-descent parser over a few tokens rather than anything that evaluates code, and every
 * failure is a null: this runs on each keystroke of whatever was typed, half-finished sums and app
 * names included, and none of that may throw.
 */
internal fun calculate(text: String): Calculation? {
    if (text.isBlank() || text.length > MAX_ANSWER_INPUT) return null
    return try {
        val tokens = tokenize(text) ?: return null
        val parser = CalculationParser(tokens)
        val value = parser.parse()
        when {
            !parser.operated -> null
            value.isNaN() || value.isInfinite() -> null
            else -> Calculation.Result(value)
        }
    } catch (e: DivisionByZero) {
        Calculation.DividedByZero
    } catch (e: Exception) {
        null
    } catch (e: StackOverflowError) {
        null
    }
}

private class Unparseable : Exception()

private class DivisionByZero : Exception()

private sealed interface Token {
    class Num(val value: Double) : Token
    class Word(val name: String) : Token
    class Symbol(val char: Char) : Token
}

/** Operators as typed on a phone keyboard, and the ASCII each one means. */
private val SYMBOLS = mapOf(
    '+' to '+', '-' to '-', '−' to '-', '*' to '*', '×' to '*', '/' to '/', '÷' to '/',
    '^' to '^', '%' to '%', '(' to '(', ')' to ')', '√' to '√',
)

private fun tokenize(text: String): List<Token>? {
    val tokens = mutableListOf<Token>()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c.isWhitespace() -> i++
            c in '0'..'9' || (c == '.' && text.getOrNull(i + 1)?.let { it in '0'..'9' } == true) -> {
                val number = readNumber(text, i) ?: return null
                // A phone number or a code, as 06-12345678 or 020 is: nobody writes a sum that way,
                // and answering one with arithmetic is the calculator misreading the query.
                if (number.zeroPadded) return null
                tokens += Token.Num(number.value)
                i = number.end
            }
            c.isLetter() -> {
                val begin = i
                while (i < text.length && text[i].isLetter()) i++
                tokens += Token.Word(text.substring(begin, i).lowercase())
            }
            else -> {
                val symbol = SYMBOLS[c] ?: return null
                tokens += Token.Symbol(symbol)
                i++
            }
        }
    }
    return tokens.takeIf { it.isNotEmpty() }
}

private val CONSTANTS = mapOf("pi" to PI, "π" to PI, "e" to E)

private val FUNCTIONS: Map<String, (Double) -> Double> = mapOf(
    "sqrt" to { x: Double -> sqrt(x) },
    "sin" to { x: Double -> sinDegrees(x) },
    "cos" to { x: Double -> cosDegrees(x) },
    "tan" to { x: Double -> tanDegrees(x) },
    "asin" to { x: Double -> Math.toDegrees(asin(x)) },
    "acos" to { x: Double -> Math.toDegrees(acos(x)) },
    "atan" to { x: Double -> Math.toDegrees(atan(x)) },
    "ln" to { x: Double -> ln(x) },
    "log" to { x: Double -> log10(x) },
    "abs" to { x: Double -> abs(x) },
    // Half away from zero, as it is taught, rather than to the even neighbour.
    "round" to { x: Double -> sign(x) * floor(abs(x) + 0.5) },
)

/*
 * Degrees, because that is what anyone typing "sin 30" into a search box means. The quarter turns
 * are answered exactly: sin 180 in radians is 1.2e-16, and a calculator that says so is wrong.
 */
private fun sinDegrees(x: Double): Double {
    val turn = x % 360.0
    return when (turn) {
        0.0, 180.0, -180.0 -> 0.0
        90.0, -270.0 -> 1.0
        270.0, -90.0 -> -1.0
        else -> sin(Math.toRadians(x))
    }
}

private fun cosDegrees(x: Double): Double {
    val turn = x % 360.0
    return when (turn) {
        0.0 -> 1.0
        180.0, -180.0 -> -1.0
        90.0, -90.0, 270.0, -270.0 -> 0.0
        else -> cos(Math.toRadians(x))
    }
}

private fun tanDegrees(x: Double): Double {
    val turn = x % 180.0
    return when (turn) {
        0.0 -> 0.0
        90.0, -90.0 -> Double.NaN
        else -> tan(Math.toRadians(x))
    }
}

/** A value, and whether it was written as a percentage, which changes what + and - do with it. */
private class Operand(val number: Double, val percent: Boolean = false)

/** Marks a product nobody typed an operator for, as in 2π or 3(4 + 5). */
private const val IMPLIED = '·'

private class CalculationParser(private val tokens: List<Token>) {
    private var pos = 0
    private var depth = 0

    /** Whether anything was done to the numbers, as opposed to a number standing on its own. */
    var operated = false
        private set

    fun parse(): Double {
        val value = expression()
        if (pos != tokens.size) throw Unparseable()
        return value.number
    }

    private fun nest() {
        depth++
        if (depth > MAX_DEPTH) throw Unparseable()
    }

    private fun unnest() {
        depth--
    }

    private fun symbolAt(index: Int): Char? = (tokens.getOrNull(index) as? Token.Symbol)?.char

    // A percentage added to or taken from something is a percentage *of* it, the way a till
    // reads "80 + 10%": 88, not 80.1.
    private fun expression(): Operand {
        nest()
        var left = term()
        while (true) {
            val op = symbolAt(pos)
            if (op != '+' && op != '-') break
            pos++
            val right = term()
            operated = true
            val amount = if (right.percent) left.number * right.number else right.number
            val sum = if (op == '+') left.number + amount else left.number - amount
            left = Operand(cancelled(sum, left.number, amount))
        }
        unnest()
        return left
    }

    /*
     * A sum that cancels to within rounding of nothing is nothing. 0.1 + 0.2 - 0.3 leaves
     * 5.6e-17 in binary, and that is an artefact of the arithmetic, not an answer.
     */
    private fun cancelled(sum: Double, a: Double, b: Double): Double =
        if (sum != 0.0 && abs(sum) < CANCELLATION * maxOf(abs(a), abs(b))) 0.0 else sum

    private fun term(): Operand {
        var left = unary()
        while (true) {
            val token = tokens.getOrNull(pos) ?: break
            val op = when {
                token is Token.Symbol && (token.char == '*' || token.char == '/') -> token.char
                token is Token.Word && token.name == "of" -> '*'
                startsImpliedProduct(token) -> IMPLIED
                else -> break
            }
            if (op != IMPLIED) pos++
            val right = unary()
            operated = true
            left = if (op == '/') {
                if (right.number == 0.0) throw DivisionByZero()
                Operand(left.number / right.number)
            } else {
                Operand(left.number * right.number)
            }
        }
        return left
    }

    // Never a number after a number: "06 12 34 56 78" is a phone number, not a product.
    private fun startsImpliedProduct(token: Token): Boolean = when (token) {
        is Token.Symbol -> token.char == '(' || token.char == '√'
        is Token.Word -> token.name in CONSTANTS || token.name in FUNCTIONS
        is Token.Num -> false
    }

    // A sign on its own is not arithmetic: -5 is a number, so it does not mark the input a sum.
    private fun unary(): Operand {
        val op = symbolAt(pos)
        if (op == '-' || op == '+') {
            pos++
            nest()
            val inner = unary()
            unnest()
            return if (op == '-') Operand(-inner.number, inner.percent) else inner
        }
        return power()
    }

    // Right to left, as a tower of powers is read, and below a sign, so -2^2 is -4.
    private fun power(): Operand {
        val base = percentage()
        if (symbolAt(pos) != '^') return base
        pos++
        nest()
        val exponent = unary()
        unnest()
        operated = true
        return Operand(base.number.pow(exponent.number))
    }

    private fun percentage(): Operand {
        var value = primary()
        while (symbolAt(pos) == '%') {
            pos++
            value = Operand(value.number / 100.0, percent = true)
        }
        return value
    }

    private fun primary(): Operand {
        val token = tokens.getOrNull(pos) ?: throw Unparseable()
        pos++
        return when (token) {
            is Token.Num -> Operand(token.value)
            is Token.Symbol -> when (token.char) {
                '(' -> Operand(bracketed())
                '√' -> {
                    operated = true
                    Operand(sqrt(argument()))
                }
                else -> throw Unparseable()
            }
            is Token.Word -> {
                val constant = CONSTANTS[token.name]
                if (constant != null) {
                    Operand(constant)
                } else {
                    val function = FUNCTIONS[token.name] ?: throw Unparseable()
                    operated = true
                    Operand(function(argument()))
                }
            }
        }
    }

    /**
     * The inside of a bracket whose opening one has just been read. A missing closing bracket at
     * the very end is let go, so a sum shows its answer while it is still being typed.
     */
    private fun bracketed(): Double {
        nest()
        val inner = expression()
        unnest()
        if (pos < tokens.size) {
            if (symbolAt(pos) != ')') throw Unparseable()
            pos++
        }
        return inner.number
    }

    // sin(30)^2 is the square of sin 30, so a bracketed argument ends at its bracket; without one
    // the argument runs as far as a sign and a power reach, so "sqrt 16 * 2" is 8.
    private fun argument(): Double {
        if (symbolAt(pos) == '(') {
            pos++
            return bracketed()
        }
        nest()
        val value = unary().number
        unnest()
        return value
    }
}

private const val CANCELLATION = 1e-13
