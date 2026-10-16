// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

/** How one app's pauses have gone today: opened anyway, or let go. */
data class MindfulDay(val opened: Int = 0, val resisted: Int = 0)

/**
 * The daily tally behind "Pause before opening": per app key, one entry reading
 * `epochDay:opened:resisted`.
 *
 * Today's numbers and nothing older, on purpose. The pause is there to make the next tap a
 * choice, and a running total kept since the feature was switched on would turn it into a score
 * to feel bad about. So an entry from any other day reads as nothing, and recording into one
 * starts the day afresh rather than adding to it. The day is passed in, and is the caller's
 * local calendar day, so a tally turns over at the user's midnight rather than at UTC's.
 *
 * Kept free of Android types so all of it can be tested on the JVM.
 */
object MindfulTally {

    /** Today's numbers from one stored entry; nothing for another day's entry or a broken one. */
    fun read(entry: String?, today: Long): MindfulDay {
        val parts = entry?.split(':') ?: return MindfulDay()
        if (parts.size != 3) return MindfulDay()
        val day = parts[0].toLongOrNull()
        val opened = parts[1].toIntOrNull()
        val resisted = parts[2].toIntOrNull()
        if (day != today || opened == null || resisted == null || opened < 0 || resisted < 0) {
            return MindfulDay()
        }
        return MindfulDay(opened, resisted)
    }

    /** The entry to store once one more pause has ended, with the app [opened] or not. */
    fun record(entry: String?, today: Long, opened: Boolean): String {
        val day = read(entry, today)
        val next = if (opened) day.copy(opened = day.opened + 1) else day.copy(resisted = day.resisted + 1)
        return "$today:${next.opened}:${next.resisted}"
    }

    /**
     * The whole stored tally with one more pause recorded for [key].
     *
     * Every other app's entry from an earlier day is dropped on the way. Those already read as
     * nothing, so all keeping them would do is grow the map by one key for every app that was
     * ever paused — and leave a record of which ones they were long after it stopped mattering.
     */
    fun recordInto(tally: Map<String, String>, key: String, today: Long, opened: Boolean): Map<String, String> =
        tally.filter { (k, v) -> k != key && dayOf(v) == today } + (key to record(tally[key], today, opened))

    /** Which of [count] lines to show on [epochDay], so the wording moves on from day to day. */
    fun lineFor(epochDay: Long, count: Int): Int =
        if (count <= 0) 0 else Math.floorMod(epochDay, count.toLong()).toInt()

    private fun dayOf(entry: String): Long? = entry.substringBefore(':').toLongOrNull()
}
