// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.data.IconSide

/**
 * Whether a favorite's notification line is cut off at the width of the name above it, rather
 * than given as much of the row as the name leaves free.
 *
 * Wherever the icon sits on the name's inner side, the name and its line are moved about as one
 * block beside it, so the block's width decides where the icon goes. A long line made the block
 * as wide as the row and put the icon at the far edge of the screen from its name, then put it
 * back when the notification went. With the icon on the outer side, against the edge the rows
 * are aligned to, nothing beside the block moves however wide it gets, so the line keeps all the
 * room there is. With no icon drawn there is nothing to move either.
 */
internal fun notificationLineHeldToName(
    alignment: HomeAlignment,
    iconSide: IconSide,
    showIcons: Boolean,
): Boolean {
    if (!showIcons) return false
    val iconOutside = (alignment == HomeAlignment.LEFT && iconSide == IconSide.LEFT) ||
        (alignment == HomeAlignment.RIGHT && iconSide == IconSide.RIGHT)
    return !iconOutside
}
