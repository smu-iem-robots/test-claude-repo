// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.victorialauncher.R
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.DEFAULT_MINDFUL_SECONDS
import dev.victorialauncher.data.MINDFUL_SECONDS_RANGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The settings for "Pause before opening": which apps pause, and for how long. The apps are
 * marked from their own long-press menus as well; the row here is for seeing them all at once.
 */
@Composable
internal fun mindfulSettingsEntries(onOpenRoute: (String) -> Unit): List<SettingsEntry> {
    val app = LocalContext.current.applicationContext as VictoriaApp
    val scope = rememberCoroutineScope()
    val mindfulApps by app.prefs.mindfulApps.collectAsState(initial = emptySet())
    val mindfulSeconds by app.prefs.mindfulSeconds.collectAsState(initial = DEFAULT_MINDFUL_SECONDS)
    // Counted against what can be listed right now, as the hidden-apps row is, rather than the
    // stored keys: those include a locked private space's apps, which a number on this screen
    // would give away, and apps since uninstalled, which the picker no longer shows.
    val pausingCount by produceState(initialValue = 0, mindfulApps) {
        val keys = mindfulApps
        value = if (keys.isEmpty()) {
            0
        } else {
            withContext(Dispatchers.Default) {
                runCatching { app.appRepository.queryAllApps().count { it.key in keys } }.getOrDefault(0)
            }
        }
    }

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
            visible = pausingCount > 0,
        ) {
            SliderRow(
                label = stringResource(R.string.settings_mindful_seconds),
                value = mindfulSeconds.toFloat(),
                range = MINDFUL_SECONDS_RANGE.first.toFloat()..MINDFUL_SECONDS_RANGE.last.toFloat(),
                valueLabel = "${mindfulSeconds}s",
                onValueChange = { scope.launch { app.prefs.setMindfulSeconds(it.roundToInt()) } },
            )
        },
    )
}
