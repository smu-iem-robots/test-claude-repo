// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.NotificationPreview
import dev.victorialauncher.widget.ClockWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NowPlayingListenerService : NotificationListenerService() {

    companion object {
        /**
         * Asks the system to bind us again, when it is willing to and has not.
         *
         * Replacing the app kills the binding but leaves the permission switched on, so the
         * Now Playing card quietly stops updating and the setting still reads as granted —
         * there is nothing to turn back on, which makes it look like the feature broke. That
         * happens on every update, not only a sideload. requestRebind is the sanctioned way
         * back and does nothing if the permission was never given.
         */
        fun rebindIfPermitted(context: Context) {
            val component = ComponentName(context, NowPlayingListenerService::class.java)
            val enabled = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners",
                )
            }.getOrNull().orEmpty()
            if (!enabled.contains(component.flattenToString()) &&
                !enabled.contains(component.packageName + "/" + component.className)
            ) {
                return
            }
            runCatching { requestRebind(component) }
        }
    }

    private lateinit var sessionManager: MediaSessionManager
    private lateinit var componentName: ComponentName

    private var watched: MediaController? = null

    /**
     * What the line under a favorite is set to, copied here because the shade is read on every
     * post and removal, which is no place to wait on a settings file. OFF until it has been
     * read, so nothing is taken from a notification before anyone is known to want it.
     */
    private var previewMode = NotificationPreview.OFF

    // On the main thread, where the listener's own callbacks arrive, so a pass the setting
    // starts never runs beside one a notification started.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var previewModeWatch: Job? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = republish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = republish()
        override fun onSessionDestroyed() {
            detach()
            republish()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachTo(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        publishCounts()
        sessionManager = getSystemService(MediaSessionManager::class.java)
        componentName = ComponentName(this, NowPlayingListenerService::class.java)
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
            attachTo(sessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            NowPlayingBus.update(null)
        }
        // Turning the line on, off or down to the sender changes nothing on the shade, so
        // nothing else would have the lines read again, or forgotten.
        previewModeWatch?.cancel()
        previewModeWatch = scope.launch {
            (application as VictoriaApp).prefs.notificationPreview.collect { mode ->
                if (mode != previewMode) {
                    previewMode = mode
                    publishCounts()
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        previewModeWatch?.cancel()
        previewModeWatch = null
        // The setting may change before the next connection, and that one's first pass comes
        // before the setting has been read again.
        previewMode = NotificationPreview.OFF
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        detach()
        NowPlayingBus.update(null)
        NotificationCountBus.clear()
        NotificationPreviewBus.clear()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        if (::sessionManager.isInitialized) {
            runCatching { attachTo(sessionManager.getActiveSessions(componentName)) }
        }
        publishCounts()
    }

    /**
     * What is on the shade right now, counted by the app that put it there.
     *
     * Recounted from scratch rather than added to and subtracted from: a posted notification
     * can replace one already there without a removal ever arriving, and a count kept by
     * arithmetic drifts upward until something clears it.
     *
     * Ongoing notifications are left out — a music player or a running download is a state,
     * not something waiting to be read — and so are group summaries, which would count the
     * same messages a second time.
     *
     * The preview lines come out of the same pass with the same exclusions, so a favorite is
     * never given a line about a notification its badge does not count, and only as much of
     * each notification is kept for them as [previewMode] will show.
     */
    private fun publishCounts() {
        val mode = previewMode
        val active = runCatching { activeNotifications }.getOrNull()
        if (active == null) {
            NotificationCountBus.clear()
            NotificationPreviewBus.clear()
            ClockWidgetProvider.renderAll(this)
            return
        }
        val counts = HashMap<String, Int>()
        val previews = HashMap<String, NotificationSnippet>()
        active.forEach { sbn ->
            if (!sbn.isClearable) return@forEach
            val notification = sbn.notification ?: return@forEach
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return@forEach
            counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
            snippetOf(notification, sbn.postTime, mode)?.let { snippet ->
                NotificationSnippets.keepNewest(previews, sbn.packageName, snippet)
            }
        }
        NotificationPreviewBus.update(previews)
        val before = NotificationCountBus.counts.value.values.sum()
        NotificationCountBus.update(counts)
        // A widget is drawn only when something asks. The home screen follows the bus on its
        // own; a RemoteViews cannot, so the total changing is what has to ask for it.
        if (counts.values.sum() != before) ClockWidgetProvider.renderAll(this)
    }

    /**
     * What [notification] may say under its favorite; [NotificationSnippets.of] decides which of
     * its words are read at all. Extras an app filled with something that will not read back
     * give no line, rather than taking the counts down with them.
     */
    private fun snippetOf(
        notification: Notification,
        postTime: Long,
        mode: NotificationPreview,
    ): NotificationSnippet? = runCatching {
        NotificationSnippets.of(
            mode = mode,
            exposure = when (notification.visibility) {
                Notification.VISIBILITY_PUBLIC -> NotificationExposure.PUBLIC
                Notification.VISIBILITY_SECRET -> NotificationExposure.SECRET
                // Private, and anything newer than this knows of.
                else -> NotificationExposure.PRIVATE
            },
            postTime = postTime,
            own = { wordsOf(notification) },
        )
    }.getOrNull()

    /** The words in [notification], with a conversation's last message read out of its style. */
    private fun wordsOf(notification: Notification): NotificationWords {
        val extras = notification.extras
        // Through androidx: the framework has no public way to read a MessagingStyle back out
        // of a notification someone else posted.
        val messaging = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val last = messaging?.messages?.lastOrNull()
        return NotificationWords(
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            conversationTitle = messaging?.conversationTitle?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            // A message with no person is the phone's own user replying, which leaves naming
            // the conversation to the app's own title.
            lastSender = last?.person?.name?.toString(),
            lastMessage = last?.text?.toString(),
        )
    }

    /** Follow whichever session is worth showing, and watch it for changes. */
    private fun attachTo(controllers: List<MediaController>?) {
        val next = controllers?.firstOrNull { isLive(it.playbackState) }
            ?: controllers?.firstOrNull { it.playbackState != null }

        if (next?.sessionToken != watched?.sessionToken) {
            detach()
            watched = next
            next?.registerCallback(controllerCallback)
        }
        republish()
    }

    private fun detach() {
        watched?.let { runCatching { it.unregisterCallback(controllerCallback) } }
        watched = null
    }

    private fun republish() {
        val controller = watched
        val state = controller?.playbackState
        if (controller == null || !isLive(state)) {
            NowPlayingBus.update(null)
            return
        }

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        if (title.isBlank() && artist.isBlank()) {
            NowPlayingBus.update(null)
            return
        }

        NowPlayingBus.update(
            NowPlaying(
                title = title,
                artist = artist,
                isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
                controller = controller,
            )
        )
    }

    /** Playing, paused or buffering counts; stopped, errored or idle does not. */
    private fun isLive(state: PlaybackState?): Boolean = when (state?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_PAUSED,
        PlaybackState.STATE_BUFFERING,
        -> true

        else -> false
    }
}