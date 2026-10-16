// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import dev.victorialauncher.data.NotificationPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The listener reads the whole shade on every post and removal, on the main thread. A
 * notification's snippet is made again only once it can have changed, and nothing is kept for
 * one the last pass did not ask about.
 */
class SnippetMemoTest {

    private val memo = SnippetMemo()
    private var made = 0

    private fun snippetOf(title: String, postTime: Long) = NotificationSnippet(title, "", postTime)

    /** One pass under [mode] that asks about [asked], each as (key, post time). */
    private fun pass(
        vararg asked: Pair<String, Long>,
        mode: NotificationPreview = NotificationPreview.FULL,
    ): List<NotificationSnippet?> {
        memo.begin(mode)
        val answers = asked.map { (key, postTime) ->
            memo.snippet(key, postTime) {
                made++
                snippetOf(key, postTime)
            }
        }
        memo.end()
        return answers
    }

    @Test
    fun `a notification not posted again is not read again`() {
        pass("a" to 10L, "b" to 20L)
        val again = pass("a" to 10L, "b" to 20L)
        assertEquals(2, made)
        assertEquals(listOf(snippetOf("a", 10L), snippetOf("b", 20L)), again)
    }

    @Test
    fun `a new post time is read again, and only that one`() {
        pass("a" to 10L, "b" to 20L)
        val again = pass("a" to 11L, "b" to 20L)
        assertEquals(3, made)
        assertEquals(snippetOf("a", 11L), again[0])
    }

    @Test
    fun `one just posted or removed is read again even within the same millisecond`() {
        pass("a" to 10L)
        memo.forget("a")
        pass("a" to 10L)
        assertEquals(2, made)
    }

    @Test
    fun `another setting reads everything again`() {
        pass("a" to 10L, mode = NotificationPreview.FULL)
        pass("a" to 10L, mode = NotificationPreview.SENDER)
        assertEquals(2, made)
        pass("a" to 10L, mode = NotificationPreview.SENDER)
        assertEquals(2, made)
    }

    @Test
    fun `what a pass does not ask about is forgotten`() {
        pass("a" to 10L)
        // Hidden by the shade, made secret, or gone: not asked about.
        pass()
        pass("a" to 10L)
        assertEquals(2, made)
    }

    @Test
    fun `a notification that gives no line is not read again either`() {
        var tries = 0
        repeat(3) {
            memo.begin(NotificationPreview.SENDER)
            val answer = memo.snippet("a", 10L) {
                tries++
                null
            }
            memo.end()
            assertNull(answer)
        }
        assertEquals(1, tries)
    }

    @Test
    fun `clearing forgets everything`() {
        pass("a" to 10L)
        memo.clear()
        pass("a" to 10L)
        assertEquals(2, made)
    }
}
