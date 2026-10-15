// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import dev.victorialauncher.data.EntryKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which rows the list sets after everything else instead of filing them under a letter. The
 * list itself is built from rows that need a ComponentName, which the JVM cannot make, so it is
 * the rule that decides the order that is pinned down here.
 */
class ListedLastTest {

    @Test
    fun `the private space padlock goes after everything, never under P`() {
        assertTrue(listedLast(EntryKind.PRIVATE_SPACE))
    }

    @Test
    fun `apps and pinned shortcuts are filed under their letters`() {
        assertFalse(listedLast(EntryKind.APP))
        assertFalse(listedLast(EntryKind.SHORTCUT))
    }

    @Test
    fun `the padlock is the only row taken out of the alphabet`() {
        EntryKind.values()
            .filter { it != EntryKind.PRIVATE_SPACE }
            .forEach { kind -> assertFalse("expected $kind under a letter", listedLast(kind)) }
    }
}
