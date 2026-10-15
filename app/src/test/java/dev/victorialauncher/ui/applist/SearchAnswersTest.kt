// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** What the search answers by itself: steel weights, the fun ones, and which kind wins. */
class SearchAnswersTest {

    /** A die that comes up with whatever it is told to, in order. */
    private class Loaded(vararg values: Int) : Random() {
        private val queue = ArrayDeque(values.toList())

        override fun nextBits(bitCount: Int): Int = queue.removeFirst()

        override fun nextInt(until: Int): Int = queue.removeFirst()

        override fun nextInt(from: Int, until: Int): Int = queue.removeFirst()

        override fun nextBoolean(): Boolean = queue.removeFirst() != 0
    }

    private fun weight(shown: String, from: String) = SearchAnswer.Value(shown, from)

    // ---- Steel ----

    @Test
    fun `a plate is length times width times thickness`() {
        val expected = weight("314 kg", "2000 × 1000 × 20 mm · 7850 kg/m³")
        assertEquals(expected, steelWeight("plate 2000x1000x20"))
        assertEquals(expected, steelWeight("Plate 2000 x 1000 x 20"))
        assertEquals(expected, steelWeight("pl 2000*1000*20"))
        assertEquals(expected, steelWeight("sheet 2000×1000×20"))
        assertEquals(expected, steelWeight("plate 2000 1000 20"))
        assertEquals(expected, steelWeight("plate 2000x1000x20mm"))
        assertEquals(expected, steelWeight("plate 2,000x1,000x20"))
    }

    @Test
    fun `a pipe is its mean circumference times its wall`() {
        assertEquals(weight("240.7 kg/m", "Ø508 × 20 mm · 7850 kg/m³"), steelWeight("pipe 508x20"))
        assertEquals(weight("240.7 kg/m", "Ø508 × 20 mm · 7850 kg/m³"), steelWeight("tube 508X20"))
        assertEquals(weight("1.44 t", "Ø508 × 20 × 6000 mm · 7850 kg/m³"), steelWeight("pipe 508x20x6000"))
        assertEquals(weight("152.7 kg/m", "Ø508 × 12.5 mm · 7850 kg/m³"), steelWeight("pipe 508x12,5"))
    }

    @Test
    fun `a monopile can weighs what a can weighs`() {
        assertEquals(weight("15.63 t/m", "Ø8000 × 80 mm · 7850 kg/m³"), steelWeight("can 8000x80"))
        assertEquals(weight("46.88 t", "Ø8000 × 80 × 3000 mm · 7850 kg/m³"), steelWeight("can 8000x80x3000"))
    }

    @Test
    fun `a round bar is a solid circle`() {
        assertEquals(weight("15.41 kg/m", "Ø50 mm · 7850 kg/m³"), steelWeight("round 50"))
        assertEquals(weight("15.41 kg", "Ø50 × 1000 mm · 7850 kg/m³"), steelWeight("bar 50x1000"))
        assertEquals(weight("92.48 kg", "Ø50 × 6000 mm · 7850 kg/m³"), steelWeight("round 50x6000"))
        // A wall of half the diameter is the same bar.
        assertEquals(weight("15.41 kg/m", "Ø50 × 25 mm · 7850 kg/m³"), steelWeight("pipe 50x25"))
    }

    @Test
    fun `a material changes the density, before or after the sizes`() {
        assertEquals(weight("108 kg", "2000 × 1000 × 20 mm · 2700 kg/m³"), steelWeight("alu plate 2000x1000x20"))
        assertEquals(weight("108 kg", "2000 × 1000 × 20 mm · 2700 kg/m³"), steelWeight("plate 2000x1000x20 aluminium"))
        assertEquals(weight("320 kg", "2000 × 1000 × 20 mm · 8000 kg/m³"), steelWeight("plate 2000x1000x20 inox"))
        assertEquals(weight("320 kg", "2000 × 1000 × 20 mm · 8000 kg/m³"), steelWeight("stainless plate 2000x1000x20"))
        assertEquals(weight("11.94 kg/m", "Ø100 × 5 mm · 8000 kg/m³"), steelWeight("pipe 100x5 stainless"))
    }

    @Test
    fun `sizes that do not make the shape are not weighed`() {
        listOf(
            "plate 2000x1000", "plate", "pipe 508", "pipe 50x30", "plate 2000x1000x0",
            "plate 2000x1000x-20", "plate steel alu 1x1x1", "plate pipe 1x1x1", "plates 1x1x1",
            "2000x1000x20", "plate 1e200x1e200x1e200",
        ).forEach { assertNull(it, steelWeight(it)) }
    }

    @Test
    fun `a round figure to round is the calculator's, a whole one is a bar`() {
        assertNull(steelWeight("round 2.5"))
        assertEquals(listOf(SearchAnswer.Value("3", "round 2.5")), searchAnswers("round 2.5", Random(1)))
        assertEquals(listOf(weight("15.41 kg/m", "Ø50 mm · 7850 kg/m³")), searchAnswers("round 50", Random(1)))
    }

    @Test
    fun `weights read in kilograms up to a tonne and in tonnes from there`() {
        assertEquals("314 kg", formatWeight(314.0))
        assertEquals("0.785 kg", formatWeight(0.785))
        assertEquals("1.44 t", formatWeight(1444.186))
        assertEquals("1.00 t", formatWeight(999.96))
    }

    // ---- Fun ----

    @Test
    fun `a coin comes up as it is told`() {
        assertEquals(SearchAnswer.Coin(heads = true), funAnswer("coin", Loaded(1)))
        assertEquals(SearchAnswer.Coin(heads = false), funAnswer("Heads or tails?", Loaded(0)))
        assertEquals(SearchAnswer.Coin(heads = true), funAnswer("flip", Loaded(1)))
    }

    @Test
    fun `dice are rolled and added up`() {
        assertEquals(SearchAnswer.Value("4", "d6", rerolls = true), funAnswer("dice", Loaded(4)))
        assertEquals(SearchAnswer.Value("6", "d6", rerolls = true), funAnswer("roll", Loaded(6)))
        assertEquals(SearchAnswer.Value("17", "d20", rerolls = true), funAnswer("d20", Loaded(17)))
        assertEquals(SearchAnswer.Value("8", "2d6 · 3 + 5", rerolls = true), funAnswer("2d6", Loaded(3, 5)))
        assertEquals(SearchAnswer.Value("13", "3d8 · 1 + 8 + 4", rerolls = true), funAnswer("roll 3d8", Loaded(1, 8, 4)))
    }

    @Test
    fun `real dice stay on their faces`() {
        val random = Random(2026)
        repeat(200) {
            val roll = funAnswer("3d8", random) as SearchAnswer.Value
            val faces = roll.detail.substringAfter("· ").split(" + ").map { it.toInt() }
            assertEquals(3, faces.size)
            assertTrue(faces.all { it in 1..8 })
            assertEquals(faces.sum().toString(), roll.value)
        }
    }

    @Test
    fun `dice nobody could roll are not offered`() {
        listOf("0d6", "d1", "21d6", "d0", "dice game").forEach { assertNull(it, funAnswer(it, Loaded(1))) }
    }

    @Test
    fun `the 8-ball answers when asked, and when a question is put to it`() {
        assertEquals(SearchAnswer.EightBall(7), funAnswer("8ball", Loaded(7)))
        assertEquals(SearchAnswer.EightBall(0), funAnswer("magic 8", Loaded(0)))
        assertEquals(SearchAnswer.EightBall(19), funAnswer("Should I go out tonight?", Loaded(19)))
        assertEquals(SearchAnswer.EightBall(3), funAnswer("will it rain?", Loaded(3)))
        assertEquals(SearchAnswer.EightBall(3), funAnswer("am i late?", Loaded(3)))
        assertNull(funAnswer("will it rain", Loaded(3)))
        assertNull(funAnswer("why is the sky blue?", Loaded(3)))
    }

    @Test
    fun `the launcher knows its own name, and the meaning of life`() {
        assertEquals(SearchAnswer.Hello(2), funAnswer("Victoria", Loaded(2)))
        assertEquals(SearchAnswer.MeaningOfLife, funAnswer("meaning of life", Loaded()))
        assertEquals(SearchAnswer.MeaningOfLife, funAnswer("What is the meaning of life?", Loaded()))
    }

    @Test
    fun `an app whose name starts like a game is still just an app`() {
        listOf("coinbase", "dice game", "victoria launcher", "rolls royce", "8 balls").forEach {
            assertNull(it, funAnswer(it, Loaded(1)))
        }
    }

    @Test
    fun `only the fun answers roll again on a tap`() {
        assertTrue(SearchAnswer.Coin(true).rerolls)
        assertTrue(SearchAnswer.EightBall(1).rerolls)
        assertTrue(SearchAnswer.Hello(1).rerolls)
        assertTrue(!SearchAnswer.MeaningOfLife.rerolls)
        assertTrue(!SearchAnswer.Value("4", "2 + 2").rerolls)
        assertTrue(!SearchAnswer.DividedByZero("1/0").rerolls)
    }

    // ---- Which answer ----

    @Test
    fun `each kind of query gets its own kind of answer`() {
        val random = Random(1)
        assertEquals(listOf(SearchAnswer.Value("30", "200*15%")), searchAnswers("200*15%", random))
        assertEquals(listOf(SearchAnswer.DividedByZero("1/0")), searchAnswers("1/0", random))
        assertEquals(listOf(weight("314 kg", "2000 × 1000 × 20 mm · 7850 kg/m³")), searchAnswers(" plate 2000x1000x20 ", random))
        assertEquals(2, searchAnswers("10 mi", random).size)
        assertEquals(listOf(SearchAnswer.MeaningOfLife), searchAnswers("meaning of life", random))
    }

    @Test
    fun `a phone number is not taken for a subtraction`() {
        assertTrue(searchAnswers("123-456-789", Random(1)).isEmpty())
        assertTrue(searchAnswers("06-12345678", Random(1)).isEmpty())
        assertEquals(listOf(SearchAnswer.Value("1250", "1500-250")), searchAnswers("1500-250", Random(1)))
    }

    @Test
    fun `most queries are answered by nothing at all`() {
        listOf("Spotify", "g", "maps", "2048", "", "settings", "e", "pi").forEach {
            assertTrue(it, searchAnswers(it, Random(1)).isEmpty())
        }
    }

    @Test
    fun `there are never more than two answers`() {
        listOf("10 mi", "9 m/s", "5 bft", "80 kg", "20 C", "2d6", "1+1").forEach {
            assertTrue(it, searchAnswers(it, Random(1)).size <= 2)
        }
    }

    @Test
    fun `the same roll comes from the same randomness`() {
        assertEquals(searchAnswers("2d6", Random(42)), searchAnswers("2d6", Random(42)))
        assertEquals(searchAnswers("coin", Random(42)), searchAnswers("coin", Random(42)))
    }
}
