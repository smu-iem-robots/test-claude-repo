// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
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
         * The categories whose title is a person: a message, an email, a call taken or missed.
         * Through androidx, which names missed calls for every release; the framework's own name
         * for it is newer than the oldest Android this runs on.
         */
        private val SENDER_CATEGORIES = setOf(
            NotificationCompat.CATEGORY_MESSAGE,
            NotificationCompat.CATEGORY_EMAIL,
            NotificationCompat.CATEGORY_CALL,
            NotificationCompat.CATEGORY_MISSED_CALL,
        )

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

    /** What each notification gave the line, so a pass reads again only what changed. */
    private val snippets = SnippetMemo()

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
        snippets.clear()
        NotificationPreviewBus.clear()
    }

    override fun onDestroy() {
        scope.cancel()
        // Access taken away can end in an unbind without a disconnect first, and the lines must
        // not outlive the service that keeps them current.
        snippets.clear()
        NotificationPreviewBus.clear()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh(sbn)

    /**
     * An app paused by Focus mode or a timer, Do Not Disturb coming on or going off, and a
     * channel made secret all reach a listener as a ranking update alone: nothing is posted or
     * removed. Without this the line went on showing what the shade had just hidden until some
     * other notification came or went. The media sessions are untouched by any of it.
     */
    override fun onNotificationRankingUpdate(rankingMap: NotificationListenerService.RankingMap?) =
        publishCounts()

    /** [changed] is the notification the callback was for, when it said. */
    private fun refresh(changed: StatusBarNotification?) {
        // Read again whatever its post time says: two posts within the same millisecond would
        // otherwise leave the first one's line standing.
        changed?.key?.let(snippets::forget)
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
     * each notification is kept for them as [previewMode] will show. One the shade is hiding
     * still counts, as it always has, but gives no line: the user has asked the phone not to
     * show it, and a line is the launcher showing it. Each line is kept for the profile that
     * posted it, while the counts stay shared by every copy of an app.
     *
     * Which notifications are hidden or secret is asked again on every pass, since the shade can
     * change its mind about either without anything being posted. What each one says is made
     * again only once it has been posted again ([SnippetMemo]).
     */
    private fun publishCounts() {
        val mode = previewMode
        val active = runCatching { activeNotifications }.getOrNull()
        if (active == null) {
            snippets.clear()
            NotificationCountBus.clear()
            NotificationPreviewBus.clear()
            ClockWidgetProvider.renderAll(this)
            return
        }
        val counts = HashMap<String, Int>()
        val previews = HashMap<PreviewKey, NotificationSnippet>()
        snippets.begin(mode)
        active.forEach { sbn ->
            if (!sbn.isClearable) return@forEach
            val notification = sbn.notification ?: return@forEach
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return@forEach
            counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
            if (mode != NotificationPreview.OFF) {
                // Read only while the line is on, and once for both questions asked of it.
                val ranking = rankingOf(sbn)
                val exposure = exposureOf(notification, ranking)
                // A secret one gives no line and has none of its words read, so there is nothing
                // to keep for it either; one the shade is hiding likewise.
                if (!hiddenFromShade(ranking) && exposure != NotificationExposure.SECRET) {
                    snippets.snippet(sbn.key, sbn.postTime) {
                        snippetOf(notification, sbn.postTime, mode, exposure)
                    }?.let { snippet ->
                        NotificationSnippets.keepNewest(previews, PreviewKey(sbn.packageName, sbn.user), snippet)
                    }
                }
            }
        }
        snippets.end()
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
        exposure: NotificationExposure,
    ): NotificationSnippet? = runCatching {
        NotificationSnippets.of(
            mode = mode,
            exposure = exposure,
            postTime = postTime,
            own = { wordsOf(notification) },
        )
    }.getOrNull()

    /**
     * How much of [notification] its app lets be seen. Secret when it cannot be told, so a
     * notification that will not say gives no line, as it did when this was read with its words.
     */
    private fun exposureOf(
        notification: Notification,
        ranking: NotificationListenerService.Ranking?,
    ): NotificationExposure = runCatching {
        when {
            // An app can say it once for the whole channel rather than on each notification,
            // and the lock screen honours either, so either makes it secret here too.
            notification.visibility == Notification.VISIBILITY_SECRET || isSecretChannel(ranking) ->
                NotificationExposure.SECRET
            notification.visibility == Notification.VISIBILITY_PUBLIC -> NotificationExposure.PUBLIC
            // Private, and anything newer than this knows of.
            else -> NotificationExposure.PRIVATE
        }
    }.getOrDefault(NotificationExposure.SECRET)

    /** The system's current word on [sbn], or null when it has none to give. */
    private fun rankingOf(sbn: StatusBarNotification): NotificationListenerService.Ranking? = runCatching {
        NotificationListenerService.Ranking().takeIf { currentRanking?.getRanking(sbn.key, it) == true }
    }.getOrNull()

    /**
     * Whether the shade is hiding the notification [ranking] is for: its app is paused, by Focus
     * mode, an app timer or a parent, or Do Not Disturb keeps it off the list. Either way the
     * system still hands it to a listener, and still lists it among the active ones.
     */
    private fun hiddenFromShade(ranking: NotificationListenerService.Ranking?): Boolean = runCatching {
        if (ranking == null) return@runCatching false
        val paused = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ranking.isSuspended
        // Set only on a notification Do Not Disturb has intercepted, and never before Android 9.
        val keptOffList =
            (ranking.suppressedVisualEffects and NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST) != 0
        paused || keptOffList
    }.getOrDefault(false)

    /** Whether the channel [ranking] names keeps its notifications off the lock screen. */
    private fun isSecretChannel(ranking: NotificationListenerService.Ranking?): Boolean =
        ranking?.channel?.lockscreenVisibility == Notification.VISIBILITY_SECRET

    /** The words in [notification], with a conversation's last message read out of its style. */
    private fun wordsOf(notification: Notification): NotificationWords {
        val extras = notification.extras
        // Through androidx: the framework has no public way to read a MessagingStyle back out
        // of a notification someone else posted. Asked only of one that is a conversation,
        // since androidx rebuilds whatever style the extras describe before it finds out it is
        // not one, a big picture's bitmap included. Every MessagingStyle names the phone's own
        // user, and one with any message in it carries those too.
        val conversation = extras != null &&
            (extras.containsKey(Notification.EXTRA_MESSAGES) || extras.containsKey(Notification.EXTRA_SELF_DISPLAY_NAME))
        val messaging =
            if (conversation) NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification) else null
        val last = messaging?.messages?.lastOrNull()
        val category: String? = notification.category
        return NotificationWords(
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            conversationTitle = messaging?.conversationTitle?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            // A message with no person is the phone's own user replying, which leaves naming
            // the conversation to the app's own title.
            lastSender = last?.person?.name?.toString(),
            lastMessage = last?.text?.toString(),
            // Only a conversation, or an app that files its notification as a message, an email
            // or a call, names in its title who it is from.
            titleNamesSender = messaging != null || (category != null && category in SENDER_CATEGORIES),
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