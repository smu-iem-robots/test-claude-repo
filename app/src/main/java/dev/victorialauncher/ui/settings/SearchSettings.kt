// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.victorialauncher.R
import dev.victorialauncher.VictoriaApp
import kotlinx.coroutines.launch

/**
 * The search box's answers, actions and matching, under App list.
 *
 * Shown whether or not the search bar is on, unlike the rows that only change how the bar
 * behaves: these are reasons to turn it on, and a reason nobody can see does not persuade
 * anyone. Each one's detail says it needs the bar instead.
 */
@Composable
internal fun searchSettingsEntries(onOpenRoute: (String) -> Unit): List<SettingsEntry> {
    val app = LocalContext.current.applicationContext as VictoriaApp
    val scope = rememberCoroutineScope()
    val answers by app.prefs.searchAnswers.collectAsState(initial = true)
    val actions by app.prefs.searchActions.collectAsState(initial = true)
    val forgiveTypos by app.prefs.searchForgiveTypos.collectAsState(initial = true)
    val openOnlyMatch by app.prefs.searchOpenOnlyMatch.collectAsState(initial = false)
    return listOf(
        SettingsEntry(
            SettingsSection.APP_LIST, stringResource(R.string.settings_search_answers),
            "search calculator sum maths math convert conversion units steel weight coin dice",
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_search_answers),
                detail = stringResource(R.string.settings_search_answers_detail),
                checked = answers,
                onCheckedChange = { v -> scope.launch { app.prefs.setSearchAnswers(v) } },
            )
        },
        SettingsEntry(
            SettingsSection.APP_LIST, stringResource(R.string.settings_search_actions),
            "search call phone dial text sms link url email timer alarm",
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_search_actions),
                detail = stringResource(R.string.settings_search_actions_detail),
                checked = actions,
                onCheckedChange = { v -> scope.launch { app.prefs.setSearchActions(v) } },
            )
        },
        SettingsEntry(
            SettingsSection.APP_LIST, stringResource(R.string.settings_search_typos),
            "search typo spelling fuzzy mistake",
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_search_typos),
                detail = stringResource(R.string.settings_search_typos_detail),
                checked = forgiveTypos,
                onCheckedChange = { v -> scope.launch { app.prefs.setSearchForgiveTypos(v) } },
            )
        },
        SettingsEntry(
            SettingsSection.APP_LIST, stringResource(R.string.settings_search_open_only),
            "search launch automatically auto open single match",
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_search_open_only),
                detail = stringResource(R.string.settings_search_open_only_detail),
                checked = openOnlyMatch,
                onCheckedChange = { v -> scope.launch { app.prefs.setSearchOpenOnlyMatch(v) } },
            )
        },
    )
}
