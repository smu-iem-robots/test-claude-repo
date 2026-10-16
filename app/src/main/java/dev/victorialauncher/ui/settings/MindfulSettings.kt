// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.victorialauncher.R
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.DEFAULT_MINDFUL_SECONDS
import dev.victorialauncher.data.MINDFUL_SECONDS_RANGE
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The settings for "Pause before opening": which apps pause, and for how long. The apps are
 * marked from their own long-press menus as well; the row here is for seeing them all at once.
 *
 * Double-tapping the wallpaper to lock is here as well. It is no part of the pause, but it is a
 * row that reads and writes its own value, which is all this file is for.
 *
 * [pausingCount] is how many of the apps that can be listed right now pause, counted by
 * VictoriaNavHost as the hidden-apps row's number is: not the stored keys, which include a locked
 * private space's apps, which a number on this screen would give away, and apps since
 * uninstalled, which the picker no longer shows.
 */
@Composable
internal fun mindfulSettingsEntries(
    onOpenRoute: (String) -> Unit,
    pausingCount: Int,
): List<SettingsEntry> {
    val app = LocalContext.current.applicationContext as VictoriaApp
    val scope = rememberCoroutineScope()
    // Null until the stored length arrives, and the slider waits for it: with the count now
    // known from the first frame, a default standing in for it would show 5s and then jump.
    val mindfulSeconds by app.prefs.mindfulSeconds.collectAsState(initial = null)
    val doubleTapHomeLock by app.prefs.doubleTapHomeLock.collectAsState(initial = false)

    return listOf(
        SettingsEntry(
            SettingsSection.APPS, stringResource(R.string.settings_mindful_apps),
            "pause wait delay mindful distracting habit countdown",
        ) {
            BackupRow(
                label = stringResource(R.string.settings_mindful_apps),
                detail = if (pausingCount == 0) {
                    stringResource(R.string.settings_mindful_none)
                } else {
                    pluralStringResource(R.plurals.settings_mindful_count, pausingCount, pausingCount)
                },
                onClick = { onOpenRoute("settings/mindful") },
            )
        },
        SettingsEntry(
            SettingsSection.APPS, stringResource(R.string.settings_mindful_seconds),
            "pause wait delay mindful countdown seconds",
            // Only once there is something for it to time.
            visible = pausingCount > 0 && mindfulSeconds != null,
        ) {
            val seconds = mindfulSeconds ?: DEFAULT_MINDFUL_SECONDS
            SliderRow(
                label = stringResource(R.string.settings_mindful_seconds),
                value = seconds.toFloat(),
                range = MINDFUL_SECONDS_RANGE.first.toFloat()..MINDFUL_SECONDS_RANGE.last.toFloat(),
                valueLabel = "${seconds}s",
                onValueChange = { scope.launch { app.prefs.setMindfulSeconds(it.roundToInt()) } },
            )
        },
        SettingsEntry(
            SettingsSection.GESTURES, stringResource(R.string.settings_double_tap_home_lock),
            "lock screen sleep double tap wallpaper home",
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_double_tap_home_lock),
                detail = stringResource(R.string.settings_double_tap_home_lock_detail),
                checked = doubleTapHomeLock,
                onCheckedChange = { scope.launch { app.prefs.setDoubleTapHomeLock(it) } },
            )
        },
    )
}
