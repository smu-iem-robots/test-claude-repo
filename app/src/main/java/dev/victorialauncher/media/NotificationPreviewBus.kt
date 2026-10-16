// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import android.os.UserHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whose line a [NotificationSnippet] is: an app, in the profile that posted it.
 *
 * Not the package alone, as the badges are. A count says nothing about whose message it is,
 * but a line does: keyed by package, a work email was written under the personal copy of the
 * same mail app, and an open private space's messages under the main profile's copy, in a row
 * that is no part of it. A UserHandle compares by its id, so an unchanged shade still makes an
 * equal map and redraws nothing.
 */
data class PreviewKey(val packageName: String, val user: UserHandle)

/**
 * Each app's newest notification in each profile as a [NotificationSnippet], for the line a
 * favorite can show under its name.
 *
 * The one place this launcher repeats what a notification says, and only once someone has
 * asked for it: upstream declined it outright (#63), and [NotificationCountBus] still says why.
 * The listener follows the setting, so this stays empty while the line is off and holds no
 * message while it shows only the sender. It is held in memory alone, never written anywhere
 * and never logged, and it is only ever what the shade is showing at that moment, cut down by
 * [NotificationSnippets.of] first.
 */
object NotificationPreviewBus {
    private val _previews = MutableStateFlow<Map<PreviewKey, NotificationSnippet>>(emptyMap())
    val previews: StateFlow<Map<PreviewKey, NotificationSnippet>> = _previews

    fun update(value: Map<PreviewKey, NotificationSnippet>) {
        // Every post and removal reads the whole shade again, a download ticking its progress
        // bar included, and most of them change no line at all. A StateFlow already drops an
        // equal value, since snippets compare by content; the check is spelled out so nobody
        // makes them compare otherwise and finds every favorite redrawn on each notification.
        if (value != _previews.value) _previews.value = value
    }

    /** Nothing is known once the service goes, and an old message is worse than no line. */
    fun clear() {
        _previews.value = emptyMap()
    }
}
