// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import kotlin.math.abs

/**
 * Where the edge zone takes its touches, kept out of the gesture code so it can be tested
 * without a screen.
 */
internal object EdgeZoneGeometry {

    /** What a touch on the part of the edge shared with the system has turned out to be. */
    enum class SharedMove {
        /** Not far enough yet from where it landed to tell. */
        UNDECIDED,

        /** Sideways: the system's back swipe, which it takes for itself. */
        SIDEWAYS,

        /** Up or down the edge: a scrub, and the zone's. */
        ALONG,
    }

    /**
     * Reads a touch on the shared part of the edge by how far it has travelled since it landed.
     *
     * Decided the way the system decides its back swipe, the larger of the two directions once
     * either passes slop, so the two never both claim one touch. A tie goes sideways: leaving
     * a touch to the system costs a scrub, taking one from it costs someone their way back.
     */
    fun sharedMove(dx: Float, dy: Float, slopPx: Float): SharedMove = when {
        abs(dx) > slopPx && abs(dx) >= abs(dy) -> SharedMove.SIDEWAYS
        abs(dy) > slopPx -> SharedMove.ALONG
        else -> SharedMove.UNDECIDED
    }

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
