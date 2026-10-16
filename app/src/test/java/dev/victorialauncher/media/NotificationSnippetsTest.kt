// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import dev.victorialauncher.data.NotificationPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a notification may put under a favorite. The home screen is shown only on an unlocked
 * phone, so a notification gives up what the unlocked shade would show — except one its app
 * marks secret, which gives nothing and must not even be read — and only as much of it as the
 * chosen line will show.
 */
class NotificationSnippetsTest {

    private val message = NotificationWords(title = "Ana", text = "Lunch at one?")

    private fun snippet(
        exposure: NotificationExposure,
        own: NotificationWords = message,
        postTime: Long = 10L,
        mode: NotificationPreview = NotificationPreview.FULL,
    ) = NotificationSnippets.of(mode, exposure, postTime, { own })

    @Test
    fun `a public notification gives its title and text`() {
        assertEquals(NotificationSnippet("Ana", "Lunch at one?", 10L), snippet(NotificationExposure.PUBLIC))
    }

    @Test
    fun `nothing is read while the line is off`() {
        var read = false
        val result = NotificationSnippets.of(
            NotificationPreview.OFF,
            NotificationExposure.PUBLIC,
            10L,
            { read = true; message },
        )
        assertNull(result)
        assertFalse(read)
    }

    @Test
    fun `sender keeps who it is from and not the message`() {
        assertEquals(
            NotificationSnippet("Ana", "", 10L),
            snippet(NotificationExposure.PUBLIC, mode = NotificationPreview.SENDER),
        )
    }

    @Test
    fun `sender keeps no last message of a conversation either`() {
        val chat = NotificationWords(title = "2 new messages", lastSender = "Ben", lastMessage = "On my way")
        assertEquals(
            NotificationSnippet("Ben", "", 10L),
            snippet(NotificationExposure.PUBLIC, own = chat, mode = NotificationPreview.SENDER),
        )
    }

    @Test
    fun `sender gives nothing for a notification that names nobody, full gives its text`() {
        val untitled = NotificationWords(text = "Backup done")
        assertNull(snippet(NotificationExposure.PUBLIC, own = untitled, mode = NotificationPreview.SENDER))
        assertEquals(NotificationSnippet("", "Backup done", 10L), snippet(NotificationExposure.PUBLIC, own = untitled))
    }

    @Test
    fun `a secret notification gives nothing and is never read`() {
        var read = false
        val result = NotificationSnippets.of(
            NotificationPreview.FULL,
            NotificationExposure.SECRET,
            10L,
            { read = true; message },
        )
        assertNull(result)
        assertFalse(read)
    }

    @Test
    fun `a private notification is shown as the unlocked shade shows it`() {
        assertEquals(NotificationSnippet("Ana", "Lunch at one?", 10L), snippet(NotificationExposure.PRIVATE))
    }

    @Test
    fun `a private conversation says its last message under full`() {
        val chat = NotificationWords(title = "Ana", text = "Lunch?", lastSender = "Ana", lastMessage = "Lunch?")
        assertEquals(NotificationSnippet("Ana", "Lunch?", 10L), snippet(NotificationExposure.PRIVATE, own = chat))
    }

    @Test
    fun `sender keeps only who a private notification is from`() {
        assertEquals(
            NotificationSnippet("Ana", "", 10L),
            snippet(NotificationExposure.PRIVATE, mode = NotificationPreview.SENDER),
        )
    }

    @Test
    fun `a group conversation is named for the group and says its last message`() {
        val group = NotificationWords(
            title = "Family (3 messages)",
            text = "3 new messages",
            conversationTitle = "Family",
            lastSender = "Ben",
            lastMessage = "See you there",
        )
        assertEquals(NotificationSnippet("Family", "See you there", 10L), snippet(NotificationExposure.PUBLIC, own = group))
    }

    @Test
    fun `a conversation without a group name is named for whoever wrote last`() {
        val chat = NotificationWords(title = "2 new messages", lastSender = "Ben", lastMessage = "On my way")
        assertEquals(NotificationSnippet("Ben", "On my way", 10L), snippet(NotificationExposure.PUBLIC, own = chat))
    }

    @Test
    fun `a blank group name falls through to the sender`() {
        val chat = NotificationWords(title = "Chat", conversationTitle = "  ", lastSender = "Ben", lastMessage = "Hi")
        assertEquals(NotificationSnippet("Ben", "Hi", 10L), snippet(NotificationExposure.PUBLIC, own = chat))
    }

    @Test
    fun `a reply of one's own leaves the sender to the app's title`() {
        val chat = NotificationWords(title = "Ana", text = "Ana: Lunch?", lastSender = null, lastMessage = "Sure")
        assertEquals(NotificationSnippet("Ana", "Sure", 10L), snippet(NotificationExposure.PUBLIC, own = chat))
    }

    @Test
    fun `line breaks and runs of spaces become one line`() {
        val words = NotificationWords(title = " Ana \n", text = "First line\n\nsecond   line\t")
        assertEquals(
            NotificationSnippet("Ana", "First line second line", 10L),
            snippet(NotificationExposure.PUBLIC, own = words),
        )
    }

    @Test
    fun `a notification with nothing to say gives nothing`() {
        assertNull(snippet(NotificationExposure.PUBLIC, own = NotificationWords(title = "", text = " \n ")))
        assertNull(snippet(NotificationExposure.PUBLIC, own = NotificationWords()))
    }

    @Test
    fun `a long message is cut short in memory`() {
        val words = NotificationWords(title = "Ana", text = "a".repeat(500))
        assertEquals(SNIPPET_MAX_CHARS, snippet(NotificationExposure.PUBLIC, own = words)!!.text.length)
    }

    @Test
    fun `a cut never leaves half of a character behind`() {
        // The emoji is two chars, and the limit falls between them.
        val text = "a".repeat(SNIPPET_MAX_CHARS - 1) + "😀" + "b"
        val cut = snippet(NotificationExposure.PUBLIC, own = NotificationWords(title = "Ana", text = text))!!.text
        assertEquals("a".repeat(SNIPPET_MAX_CHARS - 1), cut)
    }

    @Test
    fun `the newest notification of a package wins whatever order they arrive in`() {
        val previews = HashMap<String, NotificationSnippet>()
        NotificationSnippets.keepNewest(previews, "com.chat", NotificationSnippet("Ana", "first", 10L))
        NotificationSnippets.keepNewest(previews, "com.chat", NotificationSnippet("Ben", "third", 30L))
        NotificationSnippets.keepNewest(previews, "com.chat", NotificationSnippet("Cy", "second", 20L))
        NotificationSnippets.keepNewest(previews, "com.mail", NotificationSnippet("Dee", "other app", 5L))
        assertEquals(
            mapOf(
                "com.chat" to NotificationSnippet("Ben", "third", 30L),
                "com.mail" to NotificationSnippet("Dee", "other app", 5L),
            ),
            previews,
        )
    }

    @Test
    fun `two posted together keep the one seen first`() {
        val previews = HashMap<String, NotificationSnippet>()
        NotificationSnippets.keepNewest(previews, "com.chat", NotificationSnippet("Ana", "first", 10L))
        NotificationSnippets.keepNewest(previews, "com.chat", NotificationSnippet("Ben", "second", 10L))
        assertEquals(NotificationSnippet("Ana", "first", 10L), previews["com.chat"])
    }

    @Test
    fun `sender shows who it is from and nothing it says`() {
        val line = NotificationSnippets.line(NotificationPreview.SENDER, NotificationSnippet("Ana", "Lunch?", 10L))
        assertEquals("Ana", line)
    }

    @Test
    fun `sender shows nothing for a notification that names nobody`() {
        assertNull(NotificationSnippets.line(NotificationPreview.SENDER, NotificationSnippet("", "Lunch?", 10L)))
    }

    @Test
    fun `full shows who and what`() {
        assertEquals(
            "Ana: Lunch?",
            NotificationSnippets.line(NotificationPreview.FULL, NotificationSnippet("Ana", "Lunch?", 10L)),
        )
    }

    @Test
    fun `full shows whichever half there is`() {
        assertEquals("Lunch?", NotificationSnippets.line(NotificationPreview.FULL, NotificationSnippet("", "Lunch?", 10L)))
        assertEquals("Ana", NotificationSnippets.line(NotificationPreview.FULL, NotificationSnippet("Ana", "", 10L)))
    }

    @Test
    fun `off or no notification shows no line`() {
        assertNull(NotificationSnippets.line(NotificationPreview.OFF, NotificationSnippet("Ana", "Lunch?", 10L)))
        assertNull(NotificationSnippets.line(NotificationPreview.FULL, null))
        assertNull(NotificationSnippets.line(NotificationPreview.SENDER, null))
    }

    @Test
    fun `every choice but off can show a line`() {
        val shown = NotificationPreview.entries.filter {
            NotificationSnippets.line(it, NotificationSnippet("Ana", "Lunch?", 10L)) != null
        }
        assertEquals(listOf(NotificationPreview.SENDER, NotificationPreview.FULL), shown)
    }
}
