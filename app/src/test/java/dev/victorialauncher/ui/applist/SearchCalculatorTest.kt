// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search box's calculator, and the reading and writing of numbers it shares with the
 * conversions. Every expectation is what a person would write down, not what a double prints.
 */
class SearchCalculatorTest {

    private fun answer(text: String): String? = when (val result = calculate(text)) {
        is Calculation.Result -> formatAnswer(result.value)
        Calculation.DividedByZero -> "divided by zero"
        null -> null
    }

    @Test
    fun `the four operations bind in the order they are taught`() {
        assertEquals("14", answer("2+3*4"))
        assertEquals("20", answer("(2+3)*4"))
        assertEquals("2.5", answer("10/4"))
        assertEquals("10", answer("5--5"))
    }

    @Test
    fun `the operators a phone keyboard offers mean what they say`() {
        assertEquals("12", answer("3 × 4"))
        assertEquals("4", answer("8 ÷ 2"))
        assertEquals("5", answer("7 − 2"))
    }

    @Test
    fun `powers bind right to left and below a sign`() {
        assertEquals("1024", answer("2^10"))
        assertEquals("512", answer("2^3^2"))
        assertEquals("-4", answer("-2^2"))
        assertEquals("0.5", answer("2^-1"))
    }

    @Test
    fun `a percentage multiplies, and is a share of what it is added to`() {
        assertEquals("30", answer("200*15%"))
        assertEquals("12", answer("15% of 80"))
        assertEquals("88", answer("80 + 10%"))
        assertEquals("72", answer("80 - 10%"))
        assertEquals("0.25", answer("50%*50%"))
    }

    @Test
    fun `roots and logarithms`() {
        assertEquals("4", answer("sqrt 16"))
        assertEquals("4", answer("√16"))
        assertEquals("4", answer("√(16)"))
        assertEquals("8", answer("2√16"))
        assertEquals("8", answer("sqrt 16 * 2"))
        assertEquals("1", answer("ln e"))
        assertEquals("3", answer("log 1000"))
        assertEquals("1.414213562", answer("2^0.5"))
    }

    @Test
    fun `trigonometry is in degrees, and exact at the quarter turns`() {
        assertEquals("0.5", answer("sin 30"))
        assertEquals("0.5", answer("sin(30)"))
        assertEquals("0.5", answer("cos 60"))
        assertEquals("1", answer("tan 45"))
        assertEquals("0", answer("sin 180"))
        assertEquals("0", answer("cos 90"))
        assertEquals("30", answer("asin 0.5"))
        assertEquals("60", answer("acos 0.5"))
        assertEquals("45", answer("atan 1"))
        assertNull(answer("tan 90"))
    }

    @Test
    fun `a bracketed argument ends at its bracket`() {
        assertEquals("0.25", answer("sin(30)^2"))
        assertEquals("1.5", answer("sin 30 + 1"))
        assertEquals("6", answer("abs(-3)*2"))
    }

    @Test
    fun `abs and round`() {
        assertEquals("5", answer("abs -5"))
        assertEquals("3", answer("round 2.5"))
        assertEquals("-3", answer("round -2.5"))
        assertEquals("3", answer("round(2,5)"))
    }

    @Test
    fun `constants work where a number would, and next to one`() {
        assertEquals("6.283185307", answer("2pi"))
        assertEquals("6.283185307", answer("2 π"))
        assertEquals("5.436563657", answer("2*e"))
        assertEquals("14", answer("2(3+4)"))
    }

    @Test
    fun `a missing closing bracket at the end is let go`() {
        assertEquals("5", answer("(2+3"))
    }

    @Test
    fun `a comma is a decimal point only where it cannot be grouping`() {
        assertEquals("3", answer("1,5*2"))
        assertEquals("3000", answer("1,500*2"))
        assertEquals("1", answer("0,125*8"))
        assertEquals("1234568", answer("1,234,567+1"))
    }

    @Test
    fun `answers are rounded in decimal, not printed from the double`() {
        assertEquals("0.3", answer("0.1+0.2"))
        assertEquals("0", answer("0.1+0.2-0.3"))
        assertEquals("0.3333333333", answer("1/3"))
        assertEquals("0.6666666667", answer("2/3"))
        assertEquals("0.1428571429", answer("1/7"))
    }

    @Test
    fun `huge and tiny answers are written with an exponent`() {
        assertEquals("1E20", answer("10^20"))
        assertEquals("1E15", answer("10^15"))
        assertEquals("999999999900000", answer("9999999999*100000"))
        assertEquals("3.333333333E-13", answer("1/3000000000000"))
        assertEquals("0.000001", answer("0.000001*1"))
        assertEquals("1E-7", answer("0.0000001*1"))
    }

    @Test
    fun `large answers keep every whole digit rather than ten and zeros`() {
        assertEquals("1099511627776", answer("2^40"))
        assertEquals("17179869184", answer("2^34"))
        assertEquals("80779853376", answer("123456*654321"))
        assertEquals("12222222111", answer("123456789*99"))
        assertEquals("12345678902", answer("12345678901+1"))
        assertEquals("33333333333.3333", answer("1e11/3"))
    }

    @Test
    fun `dividing by zero is answered rather than ignored`() {
        assertEquals(Calculation.DividedByZero, calculate("1/0"))
        assertEquals(Calculation.DividedByZero, calculate("0/0"))
        // Zero by cancellation is zero too, not 5.6e-17.
        assertEquals(Calculation.DividedByZero, calculate("1/(0.1+0.2-0.3)"))
    }

    @Test
    fun `a number on its own is not a sum`() {
        listOf("5", "-5", "+5", "--5", "15%", "(5)", "1e5", "2048", "pi", "e").forEach {
            assertNull(it, calculate(it))
        }
    }

    @Test
    fun `app names and sums still being typed are not answered`() {
        listOf(
            "Spotify", "1Password", "abs workout", "e5", "1 2", "2+", "()", "sqrt -1",
            "10 % 3", "5!", "1.2.3", "1,5,3+1", "", "   ",
        ).forEach { assertNull(it, calculate(it)) }
    }

    @Test
    fun `a number with a leading zero is a phone number, not a sum`() {
        assertNull(calculate("06-12345678"))
        assertNull(calculate("020 + 1"))
        assertEquals("80", answer("100-20"))
    }

    @Test
    fun `nothing typed makes it throw`() {
        listOf(
            "(".repeat(60) + "1", "√√√√", "^^^", "%%", "1e999999", "9".repeat(400), "sin(",
            "π(π(π(", "1/", "-", "+", "*", ")", "1)", "√", "of", "2 of", "1,", "1.", ".",
            "1e", "1e-", "∞", "NaN", "0^-1", "10^400", "-(".repeat(50),
        ).forEach { calculate(it) }
    }

    @Test
    fun `reading a number`() {
        assertEquals(1.5, wholeNumber("1,5")!!, 0.0)
        assertEquals(1500.0, wholeNumber("1,500")!!, 0.0)
        assertEquals(0.125, wholeNumber("0,125")!!, 0.0)
        assertEquals(12.3456, wholeNumber("12,3456")!!, 0.0)
        assertEquals(1234.5, wholeNumber("1,234.5")!!, 0.0)
        assertEquals(1234567.0, wholeNumber("1,234,567")!!, 0.0)
        assertEquals(0.5, wholeNumber(".5")!!, 0.0)
        assertEquals(2000.0, wholeNumber("2e3")!!, 0.0)
        assertNull(wholeNumber("1,000,5"))
        assertNull(wholeNumber("1e999999"))
        assertNull(wholeNumber(""))
    }

    @Test
    fun `a decimal comma is told from a point and from grouping`() {
        assertTrue(readNumber("2,5", 0)!!.decimalComma)
        assertFalse(readNumber("2.5", 0)!!.decimalComma)
        assertFalse(readNumber("1,500", 0)!!.decimalComma)
        assertTrue(typedDecimalComma("2,5 bar"))
        assertTrue(typedDecimalComma("1.5+2,25"))
        assertTrue(typedDecimalComma("plate 2000x1000x1,5"))
        assertFalse(typedDecimalComma("1,500 kg"))
        assertFalse(typedDecimalComma("1,234.5+1"))
        assertFalse(typedDecimalComma("plate 2000x1000x20"))
        assertFalse(typedDecimalComma("Spotify"))
    }

    @Test
    fun `writing a number`() {
        assertEquals("0", formatNumber(0.0, 10))
        assertEquals("0", formatNumber(-0.0, 10))
        assertEquals("16.0934", formatNumber(16.09344, 6))
        assertEquals("1235000", formatNumber(1234567.891, 4))
        assertEquals("-40", formatNumber(-40.0, 6))
        assertEquals("1.234567891E20", formatNumber(1.2345678912e20, 10))
        assertEquals("2.5E-7", formatNumber(2.5e-7, 10))
        assertEquals("1.44", formatFixed(1.444186, 2))
        assertEquals("2.50", formatFixed(2.5, 2))
    }
}
