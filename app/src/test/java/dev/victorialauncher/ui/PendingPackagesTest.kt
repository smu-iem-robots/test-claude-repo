// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a burst of package callbacks adds up to by the time its one reload runs. Getting it wrong
 * in the narrow direction keeps a stale icon on screen until something clears the whole cache.
 */
class PendingPackagesTest {

    @Test
    fun `a burst names every package any of its callbacks named, once each`() {
        val pending = PendingPackages()
        pending.add(listOf("com.chat"))
        pending.add(listOf("com.chat", "com.mail"))
        pending.add(listOf("com.chat"))
        assertEquals(setOf("com.chat", "com.mail"), pending.take())
    }

    @Test
    fun `a callback that names nothing makes the burst every package`() {
        val pending = PendingPackages()
        pending.add(listOf("com.chat"))
        pending.add(null)
        assertNull(pending.take())
    }

    @Test
    fun `naming a package afterwards does not narrow a burst that is already every package`() {
        val pending = PendingPackages()
        pending.add(null)
        pending.add(listOf("com.chat"))
        assertNull(pending.take())
    }

    @Test
    fun `taking starts the next burst empty`() {
        val pending = PendingPackages()
        pending.add(listOf("com.chat"))
        pending.take()
        assertEquals(emptySet<String>(), pending.take())
    }

    @Test
    fun `the burst after an every-package one is only what it names`() {
        val pending = PendingPackages()
        pending.add(null)
        pending.take()
        pending.add(listOf("com.mail"))
        assertEquals(setOf("com.mail"), pending.take())
    }
}
