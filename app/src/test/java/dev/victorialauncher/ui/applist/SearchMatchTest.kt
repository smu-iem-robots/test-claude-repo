// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * How a query is held up against an app's name. Names are plain strings here: the rows the list
 * keeps them in carry a ComponentName, which the JVM cannot make.
 */
class SearchMatchTest {

    /** A package name that none of the queries below happen to be part of. */
    private fun rank(name: String, term: String, packageName: String = "org.test") =
        searchRank(name, packageName, term)

    private fun typo(name: String, term: String) =
        SearchName(name, "org.test").typoDistance(foldForSearch(term))

    @Test
    fun `the bands search always had keep their meaning and their order`() {
        assertEquals(RANK_PREFIX, rank("Chess", "c"))
        assertEquals(RANK_INSIDE, rank("Facebook", "c"))
        assertEquals(RANK_WORD, rank("Microsoft Word", "wo"))
        assertEquals(RANK_INSIDE, rank("Facebook", "boo"))
        assertEquals(RANK_PACKAGE, rank("設定", "settings", "com.android.settings"))
        assertTrue(RANK_PREFIX < RANK_WORD && RANK_WORD < RANK_INSIDE && RANK_INSIDE < RANK_PACKAGE)
    }

    @Test
    fun `initials come after all of them, and no match after that`() {
        assertTrue(RANK_PACKAGE < RANK_INITIALS && RANK_INITIALS < RANK_NONE)
    }

    @Test
    fun `case does not matter in any band`() {
        assertEquals(RANK_PREFIX, rank("Chess", "CH"))
        assertEquals(RANK_WORD, rank("microsoft word", "Word"))
        assertEquals(RANK_PACKAGE, rank("設定", "SETTINGS", "com.android.settings"))
        assertEquals(RANK_INITIALS, rank("Google Maps", "GM"))
    }

    @Test
    fun `accents are folded out of the name and the query alike`() {
        assertEquals(RANK_PREFIX, rank("Café", "cafe"))
        assertEquals(RANK_PREFIX, rank("Cafe", "café"))
        assertEquals(RANK_WORD, rank("Mein Zürich", "zur"))
        assertEquals(RANK_INSIDE, rank("Ångström", "gstr"))
        assertEquals(RANK_PREFIX, rank("Łódź Bus", "lodz"))
    }

    @Test
    fun `folding takes out case, accents and the letters that do not decompose`() {
        assertEquals("cafe", foldForSearch("Café"))
        assertEquals("zurich", foldForSearch("Zürich"))
        assertEquals("lodz", foldForSearch("Łódź"))
        assertEquals("strasse", foldForSearch("Straße"))
        assertEquals("istanbul", foldForSearch("İstanbul"))
        assertEquals("aeoa", foldForSearch("ÆØÅ"))
    }

    @Test
    fun `a plain ASCII name is only lower-cased, whatever the phone's language`() {
        val before = Locale.getDefault()
        // Turkish lower-cases I to a dotless ı, which would stop "instagram" finding Instagram.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            assertEquals("instagram", foldForSearch("INSTAGRAM"))
            assertEquals("k-9 mail", foldForSearch("K-9 Mail"))
            assertEquals("whatsapp", foldForSearch("WhatsApp"))
            assertEquals("", foldForSearch(""))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `marks already apart from their letter are dropped, outside the basic plane too`() {
        assertEquals("cafe", foldForSearch("Cafe\u0301"))
        // U+1D167, a combining mark written as a surrogate pair.
        assertEquals("ab", foldForSearch("a\uD834\uDD67b"))
        // Not every mark-like letter is a mark: a spacing one stays.
        assertEquals("a\u02BCb", foldForSearch("a\u02BCb"))
    }

    @Test
    fun `initials find a name by the first letter of each word`() {
        assertEquals(RANK_INITIALS, rank("Google Maps", "gm"))
        assertEquals(RANK_INITIALS, rank("YouTube Music", "ytm"))
        assertEquals(RANK_INITIALS, rank("WhatsApp", "wa"))
        assertEquals(RANK_INITIALS, rank("Google Maps Go", "gm"))
    }

    @Test
    fun `the start of a name still beats its initials`() {
        assertEquals(RANK_PREFIX, rank("Gmail", "gm"))
    }

    @Test
    fun `initials must be their start, and more than one letter`() {
        assertEquals(RANK_NONE, rank("Google Maps", "gmx"))
        assertEquals(RANK_NONE, rank("Google Maps", "mg"))
        assertEquals(RANK_NONE, rank("Maps", "mx"))
    }

    @Test
    fun `a capital after a small letter starts a word`() {
        assertEquals(listOf("whats", "app"), searchWords("WhatsApp"))
        assertEquals(listOf("you", "tube", "music"), searchWords("YouTube Music"))
        assertEquals(listOf("i", "phone"), searchWords("iPhone"))
        assertEquals(listOf("hbo", "max"), searchWords("HBO Max"))
        assertEquals(listOf("my", "app", "name", "v2"), searchWords("my-app_name.v2"))
        assertEquals(listOf("arzte", "finder"), searchWords("Ärzte-Finder"))
    }

    @Test
    fun `the old word band still splits only where it always did`() {
        // Kept to what it has always matched, so a query ranks the same apps as it did before.
        assertEquals(RANK_INSIDE, rank("WhatsApp", "app"))
    }

    @Test
    fun `a swap of neighbouring letters counts as one slip`() {
        assertEquals(1, osaDistance("gmial", "gmail", 1))
        assertEquals(0, osaDistance("maps", "maps", 1))
        assertEquals(2, osaDistance("abcd", "badc", 5))
        assertEquals(3, osaDistance("kitten", "sitting", 5))
        assertEquals(3, osaDistance("", "abc", 3))
    }

    @Test
    fun `a swapped pair is not edited again, which is what tells this from full Damerau`() {
        assertEquals(3, osaDistance("ca", "abc", 5))
    }

    @Test
    fun `anything past the limit is reported as just past it`() {
        assertEquals(2, osaDistance("abcdef", "uvwxyz", 1))
        assertEquals(2, osaDistance("ab", "abcdef", 1))
    }

    @Test
    fun `how many slips are forgiven depends on how much was typed`() {
        assertEquals(0, typoLimit("map"))
        assertEquals(1, typoLimit("maps"))
        assertEquals(1, typoLimit("spotify"))
        assertEquals(2, typoLimit("calendar"))
        assertEquals(1, typoLimit("go ma"))
    }

    @Test
    fun `a slip in a name still finds it`() {
        assertEquals(1, typo("Gmail", "gmial"))
        assertEquals(1, typo("Spotify", "spotfy"))
        assertEquals(1, typo("Spotify", "sptoify"))
        assertEquals(1, typo("Maps", "mpas"))
        assertEquals(1, typo("Settings", "setings"))
        assertEquals(1, typo("Calendar", "calender"))
        assertEquals(2, typo("Calculator", "clculatr"))
    }

    @Test
    fun `a slip at the start of a word in a longer name still finds it`() {
        assertEquals(1, typo("Spotify Music", "spotfy"))
        assertEquals(1, typo("Google Maps", "googlemaps"))
        assertEquals(1, typo("Firefox Focus", "firefix"))
    }

    @Test
    fun `short queries and names too far off are not forgiven`() {
        assertEquals(NO_TYPO_MATCH, typo("Maps", "mpa"))
        assertEquals(NO_TYPO_MATCH, typo("Netflix", "notflax"))
        assertEquals(NO_TYPO_MATCH, typo("Calculator", "clclatr"))
    }
}
