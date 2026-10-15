// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast
import dev.victorialauncher.R

/**
 * The intent that hands [this] to another app.
 *
 * A call goes to the dialler with the number filled in rather than straight out, which is both
 * the only thing possible without asking for the call permission and the right thing for a
 * number read out of a search box: the dialler shows it before anything rings. The timer and the
 * alarm likewise open the clock app's own screen with the time set, rather than setting them
 * silently.
 */
internal fun SearchAction.intent(): Intent {
    val intent = when (this) {
        is SearchAction.Call -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", dial, null))
        is SearchAction.Text -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", dial, null))
        is SearchAction.OpenLink -> Intent(Intent.ACTION_VIEW, Uri.parse(url))
        is SearchAction.Email -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null))
        is SearchAction.Timer -> Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        is SearchAction.Alarm -> Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
    }
    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Starts [action], or says that nothing on the phone takes it — a tablet with no dialler, a
 * phone with no clock app that takes timers — and answers whether it went.
 */
internal fun startSearchAction(context: Context, action: SearchAction): Boolean {
    val started = runCatching { context.startActivity(action.intent()) }.isSuccess
    if (!started) Toast.makeText(context, R.string.search_action_unavailable, Toast.LENGTH_SHORT).show()
    return started
}

/** Puts an answer on the clipboard, and says so. */
internal fun copySearchAnswer(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val copied = runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(text, text)) }.isSuccess
    if (copied) Toast.makeText(context, R.string.search_answer_copied, Toast.LENGTH_SHORT).show()
}
