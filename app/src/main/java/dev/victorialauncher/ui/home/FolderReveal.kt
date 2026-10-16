// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

/**
 * How far to bring the home stack up so a folder that has just opened shows its last app,
 * in pixels, measured in the same space as the folder's own edges.
 *
 * Only as far as that takes, and never so far that the folder's own row leaves the top: a
 * folder taller than the screen is better shown by its name and its first apps, which say what
 * it is, than by its last. Zero when it already fits, or when its row is already at the top.
 */
internal fun folderRevealLiftPx(
    folderTopPx: Float,
    folderBottomPx: Float,
    visibleTopPx: Float,
    visibleBottomPx: Float,
): Float = minOf(folderBottomPx - visibleBottomPx, folderTopPx - visibleTopPx).coerceAtLeast(0f)
