// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import java.text.Normalizer
import kotlin.math.abs

/*
 * How a typed query is held up against an app's name. Kept apart from the list model so it can
 * be tested on plain strings: the rows themselves carry a ComponentName, which the JVM tests
 * cannot make.
 *
 * The bands are in the order a person means them. The first four are the ones search has always
 * had, unchanged in meaning and order; the initials come after them because "gm" typed for
 * Google Maps is a guess at a name, where "goo" is the start of one.
 */

/** The query is the start of the whole name. */
internal const val RANK_PREFIX = 0

/** The query is the start of a word in the name, as split by spaces and punctuation. */
internal const val RANK_WORD = 1

/** The query is somewhere inside the name. */
internal const val RANK_INSIDE = 2

/** The query is somewhere in the package name, which is ASCII on phones that name apps otherwise. */
internal const val RANK_PACKAGE = 3

/** The query is the start of the name's initials: "gm" for Google Maps, "wa" for WhatsApp. */
internal const val RANK_INITIALS = 4

/** None of the above. Typos are judged separately; see [SearchName.typoDistance]. */
internal const val RANK_NONE = 5

/** What [SearchName.typoDistance] answers for a name further off than the query forgives. */
internal const val NO_TYPO_MATCH = Int.MAX_VALUE

/**
 * Case and accents taken out, so "cafe" finds Café and "zurich" finds Zürich.
 *
 * Decomposing first leaves each accent as a combining mark beside its letter, and the marks are
 * what is dropped. The handful of Latin letters Unicode keeps whole are spelled out by hand
 * afterwards, since no decomposition reaches the letter underneath: without that, "lodz" could
 * never find Łódź however it was typed.
 *
 * The first letter typed after the list changes folds every app's name several times over, in
 * the frame that shows that letter. So plain ASCII, which most names are, is only lower-cased:
 * it has nothing to decompose, no mark to drop and none of those letters, so the answer is the
 * same. And the marks are dropped by a loop rather than a Regex, which on Android builds a
 * native matcher for every call.
 */
internal fun foldForSearch(text: String): String {
    if (text.all { it.code < 0x80 }) return text.lowercase()
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val lower = withoutCombiningMarks(decomposed).lowercase()
    if (lower.none { it in UNDECOMPOSED }) return lower
    return buildString(lower.length + 4) {
        lower.forEach { c ->
            val spelled = UNDECOMPOSED[c]
            if (spelled != null) append(spelled) else append(c)
        }
    }
}

/**
 * [text] without its non-spacing marks, the category an accent decomposes into. Read by code
 * point, not by char, so a mark outside the Basic Multilingual Plane goes too, as it did when a
 * Regex matched them.
 */
private fun withoutCombiningMarks(text: String): String {
    val kept = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val codePoint = text.codePointAt(i)
        if (Character.getType(codePoint) != Character.NON_SPACING_MARK.toInt()) kept.appendCodePoint(codePoint)
        i += Character.charCount(codePoint)
    }
    return kept.toString()
}

private val UNDECOMPOSED = mapOf(
    'ø' to "o", 'ł' to "l", 'đ' to "d", 'ð' to "d", 'þ' to "th", 'æ' to "ae",
    'œ' to "oe", 'ß' to "ss", 'ħ' to "h", 'ı' to "i",
)

/**
 * The words of a name, folded, for initials and for typos.
 *
 * Split where search has always split it, and also where a lower-case letter is followed by a
 * capital, because that is how a name written without spaces still shows its words: WhatsApp is
 * "whats" and "app", YouTube Music is "you", "tube" and "music". The capitals have to be read
 * before folding, which is why this works from the name as shown rather than from its folded form.
 */
internal fun searchWords(name: String): List<String> {
    val words = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() {
        if (current.isNotEmpty()) {
            val folded = foldForSearch(current.toString())
            if (folded.isNotEmpty()) words += folded
            current.setLength(0)
        }
    }
    var previous: Char? = null
    for (c in name) {
        if (c in WORD_SEPARATORS || c.isWhitespace()) {
            flush()
            previous = null
            continue
        }
        if (previous != null && previous.isLowerCase() && c.isUpperCase()) flush()
        current.append(c)
        previous = c
    }
    flush()
    return words
}

/** Where search has always split a name into words. */
private val WORD_SEPARATORS = charArrayOf(' ', '-', '_', '.')

/**
 * How many edits a query of this length may be off by and still count.
 *
 * Nothing under four letters: "mpas" is plainly Maps misspelt, but one edit away from a
 * three-letter query is half the list. Two edits only from eight letters on, where a name is long
 * enough for two slips to leave it recognisable.
 */
internal fun typoLimit(term: String): Int {
    val letters = term.count { it.isLetterOrDigit() }
    return when {
        letters < 4 -> 0
        letters < 8 -> 1
        else -> 2
    }
}

/**
 * The optimal-string-alignment distance between [a] and [b]: insertions, deletions, substitutions
 * and swaps of two neighbouring letters, each counting one. A swap is a typo of its own rather
 * than two substitutions, since "gmial" is one slip of the fingers, not two.
 *
 * Anything past [limit] is reported as limit + 1, which lets two strings whose lengths alone are
 * too far apart be turned away without filling in the table.
 */
internal fun osaDistance(a: String, b: String, limit: Int): Int {
    if (abs(a.length - b.length) > limit) return limit + 1
    val width = b.length + 1
    var twoAgo = IntArray(width)
    var previous = IntArray(width) { it }
    var current = IntArray(width)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var best = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                best = minOf(best, twoAgo[j - 2] + 1)
            }
            current[j] = best
        }
        val spent = twoAgo
        twoAgo = previous
        previous = current
        current = spent
    }
    return minOf(previous[b.length], limit + 1)
}

/**
 * One app's name, folded and split once, so a query typed a letter at a time does not repeat the
 * same normalising for every app on every keystroke.
 *
 * Every comparison expects the query already through [foldForSearch].
 */
internal class SearchName(name: String, packageName: String) {
    val folded: String = foldForSearch(name)

    /** The words as search has always split them: no camel-case, so the old bands keep their meaning. */
    private val separatedWords: List<String> =
        name.split(*WORD_SEPARATORS).map(::foldForSearch).filter { it.isNotEmpty() }

    /** The words with camel-case split as well, for initials and typos. */
    val words: List<String> = searchWords(name)

    val initials: String = words.joinToString("") { it.take(1) }

    private val foldedPackage: String = packageName.lowercase()

    /** The best band [term] reaches, lower being better; [RANK_NONE] when it reaches none. */
    fun rank(term: String): Int = when {
        folded.startsWith(term) -> RANK_PREFIX
        separatedWords.any { it.startsWith(term) } -> RANK_WORD
        folded.contains(term) -> RANK_INSIDE
        foldedPackage.contains(term) -> RANK_PACKAGE
        matchesInitials(term) -> RANK_INITIALS
        else -> RANK_NONE
    }

    // Two letters at least: one letter is already the start of a word, and a name of one word
    // has an initial that every query starting with that letter would otherwise match.
    private fun matchesInitials(term: String): Boolean {
        val letters = term.filterNot { it.isWhitespace() }
        return letters.length >= 2 && initials.length >= 2 && initials.startsWith(letters)
    }

    /**
     * How many slips away from this name [term] is, or [NO_TYPO_MATCH] when it is further off
     * than [typoLimit] forgives.
     *
     * Held against the whole name, and against the start of each word cut to about the query's
     * length, so a slip in the first few letters of a long name still finds it. A letter either
     * side of the query's length, because the slips that matter most are a letter left out or
     * one doubled: "spotfy" is two edits from "spotif" but one from "spotify".
     */
    fun typoDistance(term: String): Int {
        val limit = typoLimit(term)
        if (limit == 0) return NO_TYPO_MATCH
        var best = osaDistance(term, folded, limit)
        for (word in words) {
            if (best == 0) break
            for (length in term.length - 1..term.length + 1) {
                if (length < 1) continue
                val distance = osaDistance(term, word.take(length), limit)
                if (distance < best) best = distance
            }
        }
        return if (best <= limit) best else NO_TYPO_MATCH
    }
}
