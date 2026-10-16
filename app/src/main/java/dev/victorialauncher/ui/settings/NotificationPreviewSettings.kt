// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.victorialauncher.R
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.NotificationPreview
import dev.victorialauncher.media.isListenerEnabled
import kotlinx.coroutines.launch

/**
 * The setting for the line under a favorite that says what its newest notification is (#121),
 * and a reminder when it is on without the access it needs.
 *
 * The access row the settings screen already has shows only for Now Playing and the badges,
 * and says the access is for the playing track, so this brings a hint of its own.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun notificationPreviewSettingsEntries(onOpenRoute: (String) -> Unit): List<SettingsEntry> {
    val context = LocalContext.current
    val app = context.applicationContext as VictoriaApp
    val scope = rememberCoroutineScope()
    val preview by app.prefs.notificationPreview.collectAsState(initial = NotificationPreview.OFF)
    // Asked again each time this screen comes back to the front, as the home screen does. The
    // chip below sends people to a system screen they leave with BACK, and that bumps nothing
    // the settings screen's own access row is keyed on, so the hint would outlive the grant.
    val lifecycleOwner = LocalLifecycleOwner.current
    var listenerGranted by remember { mutableStateOf(isListenerEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) listenerGranted = isListenerEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return listOf(
        SettingsEntry(
            SettingsSection.NOTIFICATIONS, stringResource(R.string.settings_notification_preview),
            "preview message sender text line unread favorites",
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.settings_notification_preview), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_notification_preview_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NotificationPreview.entries.forEach { option ->
                        FilledChip(stringResource(option.labelRes()), preview == option) {
                            scope.launch { app.prefs.setNotificationPreview(option) }
                        }
                    }
                }
            }
        },
        SettingsEntry(
            SettingsSection.NOTIFICATIONS, stringResource(R.string.settings_notification_access_missing),
            "permission access preview",
            visible = preview != NotificationPreview.OFF && !listenerGranted,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_notification_access_missing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.settings_notification_preview_access_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                FilledChip(stringResource(R.string.settings_open_settings), selected = false) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        },
    )
}

@StringRes
private fun NotificationPreview.labelRes(): Int = when (this) {
    NotificationPreview.OFF -> R.string.notification_preview_off
    NotificationPreview.SENDER -> R.string.notification_preview_sender
    NotificationPreview.FULL -> R.string.notification_preview_full
}
