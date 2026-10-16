// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderRevealTest {

    @Test
    fun `a folder that fits is left where it is`() {
        val lift = folderRevealLiftPx(
            folderTopPx = 500f, folderBottomPx = 900f, visibleTopPx = 0f, visibleBottomPx = 1000f,
        )
        assertEquals(0f, lift, 0.01f)
    }

    @Test
    fun `a folder ending exactly at the bottom needs nothing`() {
        val lift = folderRevealLiftPx(
            folderTopPx = 500f, folderBottomPx = 1000f, visibleTopPx = 0f, visibleBottomPx = 1000f,
        )
        assertEquals(0f, lift, 0.01f)
    }

    @Test
    fun `a folder running off the bottom comes up by exactly the overflow`() {
        val lift = folderRevealLiftPx(
            folderTopPx = 800f, folderBottomPx = 1300f, visibleTopPx = 0f, visibleBottomPx = 1000f,
        )
        assertEquals(300f, lift, 0.01f)
    }

    @Test
    fun `a folder taller than the screen stops with its own row at the top`() {
        // 1000 over the bottom, but only 550 between its row and the top.
        val lift = folderRevealLiftPx(
            folderTopPx = 600f, folderBottomPx = 2000f, visibleTopPx = 50f, visibleBottomPx = 1000f,
        )
        assertEquals(550f, lift, 0.01f)
    }

    @Test
    fun `a folder whose row is already above the top is not moved`() {
        val lift = folderRevealLiftPx(
            folderTopPx = 20f, folderBottomPx = 1200f, visibleTopPx = 50f, visibleBottomPx = 1000f,
        )
        assertEquals(0f, lift, 0.01f)
    }
}
