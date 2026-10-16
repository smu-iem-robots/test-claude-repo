// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.data.IconSide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLineTest {

    @Test
    fun `an icon against the aligned edge leaves the line its full room`() {
        assertFalse(notificationLineHeldToName(HomeAlignment.LEFT, IconSide.LEFT, showIcons = true))
        assertFalse(notificationLineHeldToName(HomeAlignment.RIGHT, IconSide.RIGHT, showIcons = true))
    }

    @Test
    fun `an icon on the name's inner side holds the line to the name`() {
        assertTrue(notificationLineHeldToName(HomeAlignment.LEFT, IconSide.RIGHT, showIcons = true))
        assertTrue(notificationLineHeldToName(HomeAlignment.RIGHT, IconSide.LEFT, showIcons = true))
    }

    @Test
    fun `a centered row holds the line to the name whichever side its icon is on`() {
        assertTrue(notificationLineHeldToName(HomeAlignment.CENTER, IconSide.LEFT, showIcons = true))
        assertTrue(notificationLineHeldToName(HomeAlignment.CENTER, IconSide.RIGHT, showIcons = true))
    }

    @Test
    fun `with no icon drawn the line is never held back`() {
        for (alignment in HomeAlignment.values()) {
            for (side in IconSide.values()) {
                assertFalse(notificationLineHeldToName(alignment, side, showIcons = false))
            }
        }
    }
}
