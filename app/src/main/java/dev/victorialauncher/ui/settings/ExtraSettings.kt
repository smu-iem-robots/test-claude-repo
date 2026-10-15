// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.runtime.Composable

/**
 * Settings rows that collect and write their own values instead of arriving as parameters.
 *
 * Every other row on the settings screen is a value and a setter threaded from
 * VictoriaNavHost, which already passes some 150 of them: each row added that way grows two
 * parameter lists and a call site, all in files every change touches. The rows gathered here
 * read [dev.victorialauncher.data.Prefs] from the application directly — the way AppIcon and
 * the icon picker already reach it — so a feature keeps its settings beside the rest of its
 * code. They are still ordinary [SettingsEntry] rows, so the search box finds them and each
 * one lands in the section it names, after the rows already there.
 *
 * [onOpenRoute] navigates to a destination registered in VictoriaNavHost, for a row that
 * opens a screen of its own.
 */
@Composable
internal fun extraSettingsEntries(onOpenRoute: (String) -> Unit): List<SettingsEntry> = emptyList()
