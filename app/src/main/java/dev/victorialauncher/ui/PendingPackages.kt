// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui

/**
 * The packages a burst of change callbacks has named, gathered while the reload they ask for
 * waits for the burst to end.
 *
 * Messaging apps republish their conversation shortcuts every few seconds, and one update can be
 * reported several times over. Each report used to be a full enumeration of every profile and an
 * icon cache thrown away whole, which was then refilled a row at a time on the main thread while
 * the list scrolled (#119). Gathered, a burst costs one reload, and only the icons of what it
 * named are drawn again.
 *
 * A callback that names nothing stands for every package, and once one has, the burst stays that
 * way: evicting only what the others named would keep whatever that one changed.
 *
 * Main thread only, like the callbacks that feed it.
 */
class PendingPackages {

    /** Null once something in this burst has stood for every package. */
    private var names: MutableSet<String>? = mutableSetOf()

    /** What one callback named, or null when it could not say. */
    fun add(packages: Collection<String>?) {
        val current = names ?: return
        if (packages == null) names = null else current.addAll(packages)
    }

    /** Everything gathered since the last take, null meaning every package, and starts again. */
    fun take(): Set<String>? {
        val taken = names?.toSet()
        names = mutableSetOf()
        return taken
    }
}
