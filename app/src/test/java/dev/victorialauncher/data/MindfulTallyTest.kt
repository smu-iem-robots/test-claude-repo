// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The count shown under a paused app: today's opens and today's let-gos, never yesterday's.
 * A tally that carried over would read as a score, and one that broke on a bad entry would
 * stand between someone and the app they asked for.
 */
class MindfulTallyTest {

    private val today = 20000L
    private val a = "com.a/com.a.Main"
    private val b = "com.b/com.b.Main"
    private val c = "com.c/com.c.Main"

    @Test
    fun `today's entry reads back as it was stored`() {
        assertEquals(MindfulDay(opened = 3, resisted = 2), MindfulTally.read("20000:3:2", today))
    }

    @Test
    fun `yesterday's entry reads as nothing today`() {
        assertEquals(MindfulDay(), MindfulTally.read("19999:3:2", today))
    }

    @Test
    fun `a missing or broken entry reads as nothing rather than failing`() {
        assertEquals(MindfulDay(), MindfulTally.read(null, today))
        assertEquals(MindfulDay(), MindfulTally.read("junk", today))
        assertEquals(MindfulDay(), MindfulTally.read("20000:1", today))
        assertEquals(MindfulDay(), MindfulTally.read("20000:x:1", today))
        assertEquals(MindfulDay(), MindfulTally.read("20000:-1:2", today))
    }

    @Test
    fun `opening after the pause counts as opened`() {
        assertEquals("20000:1:0", MindfulTally.record(null, today, opened = true))
        assertEquals("20000:3:3", MindfulTally.record("20000:2:3", today, opened = true))
    }

    @Test
    fun `never mind counts as resisted`() {
        assertEquals("20000:1:1", MindfulTally.record("20000:1:0", today, opened = false))
    }

    @Test
    fun `a new day starts the tally again instead of adding to the old one`() {
        assertEquals("20000:1:0", MindfulTally.record("19999:5:4", today, opened = true))
    }

    @Test
    fun `recording drops other apps' earlier days and keeps their today`() {
        val tally = mapOf(a to "19999:2:2", b to "20000:1:0")
        assertEquals(
            mapOf(b to "20000:1:0", c to "20000:0:1"),
            MindfulTally.recordInto(tally, c, today, opened = false),
        )
    }

    @Test
    fun `recording adds to the same app's count for today`() {
        assertEquals(
            mapOf(a to "20000:3:2"),
            MindfulTally.recordInto(mapOf(a to "20000:2:2"), a, today, opened = true),
        )
    }

    @Test
    fun `the line moves on by one each day and wraps around`() {
        assertEquals(0, MindfulTally.lineFor(0L, 4))
        assertEquals(1, MindfulTally.lineFor(1L, 4))
        assertEquals(1, MindfulTally.lineFor(5L, 4))
        assertEquals(3, MindfulTally.lineFor(-1L, 4))
    }

    @Test
    fun `no lines to choose from still gives an index rather than dividing by zero`() {
        assertEquals(0, MindfulTally.lineFor(7L, 0))
    }
}
