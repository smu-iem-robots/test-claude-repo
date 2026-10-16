// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeZoneGeometryTest {

    private val slop = 8f

    @Test
    fun `a touch that has barely moved is not decided yet`() {
        assertEquals(EdgeZoneGeometry.SharedMove.UNDECIDED, EdgeZoneGeometry.sharedMove(0f, 0f, slop))
        assertEquals(EdgeZoneGeometry.SharedMove.UNDECIDED, EdgeZoneGeometry.sharedMove(5f, -7f, slop))
        // Exactly at slop is still inside it.
        assertEquals(EdgeZoneGeometry.SharedMove.UNDECIDED, EdgeZoneGeometry.sharedMove(8f, 8f, slop))
    }

    @Test
    fun `sideways either way is left to the system`() {
        assertEquals(EdgeZoneGeometry.SharedMove.SIDEWAYS, EdgeZoneGeometry.sharedMove(9f, 3f, slop))
        assertEquals(EdgeZoneGeometry.SharedMove.SIDEWAYS, EdgeZoneGeometry.sharedMove(-9f, 3f, slop))
    }

    @Test
    fun `up or down the edge is a scrub`() {
        assertEquals(EdgeZoneGeometry.SharedMove.ALONG, EdgeZoneGeometry.sharedMove(3f, 9f, slop))
        assertEquals(EdgeZoneGeometry.SharedMove.ALONG, EdgeZoneGeometry.sharedMove(-3f, -9f, slop))
        // Past slop sideways too, but further along the edge than across it.
        assertEquals(EdgeZoneGeometry.SharedMove.ALONG, EdgeZoneGeometry.sharedMove(10f, 11f, slop))
    }

    @Test
    fun `a diagonal tie goes to the system`() {
        assertEquals(EdgeZoneGeometry.SharedMove.SIDEWAYS, EdgeZoneGeometry.sharedMove(10f, -10f, slop))
    }

    @Test
    fun `nothing kept clear leaves the band as it is`() {
        val bottom = EdgeZoneGeometry.confinedBottomPx(
            bandTopPx = 400f, bandBottomPx = 1200f, viewportHeightPx = 2000, bottomKeepOutPx = 0f,
        )
        assertEquals(1200f, bottom, 0.01f)
    }

    @Test
    fun `a keep-out below the band does not touch it`() {
        // Kept clear from 1700 down, and the band ends at 1200.
        val bottom = EdgeZoneGeometry.confinedBottomPx(
            bandTopPx = 400f, bandBottomPx = 1200f, viewportHeightPx = 2000, bottomKeepOutPx = 300f,
        )
        assertEquals(1200f, bottom, 0.01f)
    }

    @Test
    fun `a keep-out reaching into the band cuts it short there`() {
        val bottom = EdgeZoneGeometry.confinedBottomPx(
            bandTopPx = 400f, bandBottomPx = 1200f, viewportHeightPx = 2000, bottomKeepOutPx = 1000f,
        )
        assertEquals(1000f, bottom, 0.01f)
    }

    @Test
    fun `a keep-out above the whole band leaves no zone at all`() {
        // 2000 - 1800 is 200, above the band's top: the zone ends where it starts.
        val bottom = EdgeZoneGeometry.confinedBottomPx(
            bandTopPx = 400f, bandBottomPx = 1200f, viewportHeightPx = 2000, bottomKeepOutPx = 1800f,
        )
        assertEquals(400f, bottom, 0.01f)
    }

    @Test
    fun `an unmeasured screen is not cut against`() {
        val bottom = EdgeZoneGeometry.confinedBottomPx(
            bandTopPx = 400f, bandBottomPx = 1200f, viewportHeightPx = 0, bottomKeepOutPx = 300f,
        )
        assertEquals(1200f, bottom, 0.01f)
    }
}
