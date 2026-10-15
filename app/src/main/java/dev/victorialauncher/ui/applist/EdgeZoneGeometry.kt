// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

/**
 * Where the edge zone takes its touches, kept out of the gesture code so it can be tested
 * without a screen.
 */
internal object EdgeZoneGeometry {

    /**
     * Where a zone confined to the band ends: at the band's foot, or higher if something kept
     * clear at the bottom of the screen reaches up into it. Never above the band's own top, so
     * a keep-out taller than the band leaves no zone rather than one turned inside out.
     */
    fun confinedBottomPx(
        bandTopPx: Float,
        bandBottomPx: Float,
        viewportHeightPx: Int,
        bottomKeepOutPx: Float,
    ): Float {
        if (bottomKeepOutPx <= 0f || viewportHeightPx <= 0) return bandBottomPx
        return minOf(bandBottomPx, viewportHeightPx - bottomKeepOutPx).coerceAtLeast(bandTopPx)
    }
}
