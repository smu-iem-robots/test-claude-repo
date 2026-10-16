// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import dev.victorialauncher.data.NotificationPreview

/**
 * The newest notification a package is showing, cut down to what a line under its favorite
 * can say: who it is from and what it says. [text] is empty when the app keeps that private,
 * and under SENDER, which never shows it.
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
        // app's own title is often a summary such as "3 new messages" instead.
        val title = firstNotBlank(words.conversationTitle, words.lastSender, words.title)
        val text = if (mode == NotificationPreview.FULL) firstNotBlank(words.lastMessage, words.text) else ""
        // Under SENDER that leaves out one that names nobody, so an older one from the same
        // app that does name someone keeps the line rather than a newer one with nothing in it.
        if (title.isEmpty() && text.isEmpty()) return null
        return NotificationSnippet(title = title, text = text, postTime = postTime)
    }

    /**
     * Keeps [snippet] for [packageName] if it is newer than the one already held. A tie keeps
     * the one seen first, so two notifications posted together do not swap places each time
     * the shade is read again.
     */
    fun keepNewest(into: MutableMap<String, NotificationSnippet>, packageName: String, snippet: NotificationSnippet) {
        val held = into[packageName]
        if (held == null || snippet.postTime > held.postTime) into[packageName] = snippet
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
