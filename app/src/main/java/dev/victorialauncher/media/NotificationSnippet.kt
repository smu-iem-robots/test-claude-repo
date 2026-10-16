// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import dev.victorialauncher.data.NotificationPreview

/**
 * The newest notification a package is showing, cut down to what a line under its favorite
 * can say: who it is from and what it says. [text] is empty when the notification says nothing
 * beyond who it is from, and under SENDER, which never shows it.
 */
data class NotificationSnippet(
    val title: String,
    val text: String,
    val postTime: Long,
)

/** How much of itself a notification lets a lock screen show, as its app declared it. */
internal enum class NotificationExposure { PUBLIC, PRIVATE, SECRET }

/**
 * The words a notification carries, read out of it and not yet judged. The conversation
 * fields are only there for a messaging notification: its group's name, and the sender and
 * text of the last message in it.
 */
internal data class NotificationWords(
    val title: String? = null,
    val text: String? = null,
    val conversationTitle: String? = null,
    val lastSender: String? = null,
    val lastMessage: String? = null,
    /**
     * Whether [title] names a person or a conversation, rather than what the notification is
     * about. True only for a conversation, an email or a call. Anywhere else the title is the
     * content itself: a calendar's "Couples therapy - Dr Smit", a bank's "€86.40 at Pharmacy",
     * a reminder to take a medicine.
     */
    val titleNamesSender: Boolean = false,
)

/**
 * Longest a snippet's title or text is kept. One line under a name never shows this much,
 * and anything past what can be shown is only more of someone's message held in memory.
 */
internal const val SNIPPET_MAX_CHARS = 200

/**
 * What a notification may put under a favorite, decided apart from reading it.
 *
 * Kept free of Android types so all of it can be tested on the JVM; the listener service does
 * the reading, and only what these functions ask for.
 */
internal object NotificationSnippets {

    private val whitespace = Regex("\\s+")

    /**
     * The snippet one notification gives under [mode], or null for none.
     *
     * Nothing is read while the line is OFF, and SENDER keeps no message: what is never shown
     * is not held either, so someone who did not ask for the line, or asked only who wrote,
     * has none of what their notifications say kept by the launcher.
     *
     * A secret notification gives nothing, and [own] is not even read: its app has said it must
     * not be seen without unlocking, and an app that bothers to say so — a bank, a one-time
     * code — means it. A private one is shown as the shade shows it once the phone is unlocked,
     * which is the only time the home screen is in front of anyone. Holding it to the lock
     * screen's rule instead would have emptied FULL for nearly everything, since private is
     * what every app gets that never says otherwise, and a public version is mostly "1 new
     * message", which says nothing the badge did not.
     *
     * Under SENDER only a conversation, an email or a call says who it is from. Any other
     * notification's title is what it says, not who sent it, so it gives no line there at all.
     */
    fun of(
        mode: NotificationPreview,
        exposure: NotificationExposure,
        postTime: Long,
        own: () -> NotificationWords,
    ): NotificationSnippet? {
        if (mode == NotificationPreview.OFF) return null
        val words = when (exposure) {
            NotificationExposure.SECRET -> return null
            NotificationExposure.PUBLIC, NotificationExposure.PRIVATE -> own()
        }
        // A conversation is named for the group it is in, else for whoever wrote last; the
        // app's own title is often a summary such as "3 new messages" instead. Under SENDER that
        // title is used only where it names someone: elsewhere it is the very thing SENDER was
        // chosen to keep off the screen, an appointment or a payment.
        val ownTitle = if (mode == NotificationPreview.FULL || words.titleNamesSender) words.title else null
        val title = firstNotBlank(words.conversationTitle, words.lastSender, ownTitle)
        val text = if (mode == NotificationPreview.FULL) firstNotBlank(words.lastMessage, words.text) else ""
        // Under SENDER that leaves out one that names nobody, so an older one from the same
        // app that does name someone keeps the line rather than a newer one with nothing in it.
        if (title.isEmpty() && text.isEmpty()) return null
        return NotificationSnippet(title = title, text = text, postTime = postTime)
    }

    /**
     * Keeps [snippet] under [key] if it is newer than the one already held. A tie keeps the one
     * seen first, so two notifications posted together do not swap places each time the shade
     * is read again. The listener keys by app and profile ([PreviewKey]); any key will do here,
     * so this stays testable without a UserHandle.
     */
    fun <K> keepNewest(into: MutableMap<K, NotificationSnippet>, key: K, snippet: NotificationSnippet) {
        val held = into[key]
        if (held == null || snippet.postTime > held.postTime) into[key] = snippet
    }

    /** The line to draw for [snippet] under [mode], or null to draw none. */
    fun line(mode: NotificationPreview, snippet: NotificationSnippet?): String? {
        if (snippet == null) return null
        return when (mode) {
            NotificationPreview.OFF -> null
            NotificationPreview.SENDER -> snippet.title.takeIf { it.isNotEmpty() }
            NotificationPreview.FULL -> when {
                snippet.title.isEmpty() -> snippet.text.takeIf { it.isNotEmpty() }
                snippet.text.isEmpty() -> snippet.title
                else -> "${snippet.title}: ${snippet.text}"
            }
        }
    }

    private fun firstNotBlank(vararg candidates: String?): String {
        for (candidate in candidates) {
            val cleaned = clean(candidate)
            if (cleaned.isNotEmpty()) return cleaned
        }
        return ""
    }

    /**
     * One line of at most [SNIPPET_MAX_CHARS]. A message's line breaks would otherwise end the
     * line at its first one, and a cut is moved back off a surrogate pair so it never leaves
     * half a character.
     */
    private fun clean(value: String?): String {
        if (value == null) return ""
        val flat = value.replace(whitespace, " ").trim()
        if (flat.length <= SNIPPET_MAX_CHARS) return flat
        val end = if (flat[SNIPPET_MAX_CHARS - 1].isHighSurrogate()) SNIPPET_MAX_CHARS - 1 else SNIPPET_MAX_CHARS
        return flat.substring(0, end).trimEnd()
    }
}

/**
 * The snippet each notification gave on the last pass over the shade, by its key, so the next
 * pass makes one again only for a notification that has changed.
 *
 * The listener reads the whole shade on every post and removal, a download ticking its progress
 * bar included, and it does so on the main thread, the one the home screen draws on. Making
 * every snippet again each time read every conversation in the shade back out of its style,
 * several times a second while something showed progress, for lines that could not have changed:
 * a notification says nothing new until it is posted again. One is made again when its post time
 * moves, when the listener [forget]s it because it was just posted or removed, and when the
 * setting changes, since a snippet made for one setting holds too much or too little for another.
 *
 * Only snippets are held, never the words they were cut from, so SENDER still keeps no message.
 * A pass keeps only what it asked for: a notification gone from the shade, or one no longer
 * asked about because the shade is hiding it or its app has made it secret, is dropped with that
 * pass rather than kept for later.
 *
 * Main thread only, like the listener's callbacks.
 */
internal class SnippetMemo {
    private class Made(val postTime: Long, val snippet: NotificationSnippet?)

    private var mode = NotificationPreview.OFF
    private var held = HashMap<String, Made>()
    private var asked = HashMap<String, Made>()

    /** Starts a pass over the shade under [mode]. */
    fun begin(mode: NotificationPreview) {
        if (mode != this.mode) held.clear()
        this.mode = mode
        asked = HashMap()
    }

    /**
     * The snippet for the notification [key] posted at [postTime]: the one already made for it,
     * or what [make] makes now. A null from [make] is kept like any other answer.
     */
    fun snippet(key: String, postTime: Long, make: () -> NotificationSnippet?): NotificationSnippet? {
        val made = held[key]?.takeIf { it.postTime == postTime } ?: Made(postTime, make())
        asked[key] = made
        return made.snippet
    }

    /** Ends the pass: what it did not ask about is forgotten. */
    fun end() {
        held = asked
        asked = HashMap()
    }

    /** Has the notification [key] read again on the next pass, its post time moved or not. */
    fun forget(key: String) {
        held.remove(key)
    }

    /** Forgets everything, for when nothing is to be shown or the listener goes. */
    fun clear() {
        held = HashMap()
        asked = HashMap()
    }
}
