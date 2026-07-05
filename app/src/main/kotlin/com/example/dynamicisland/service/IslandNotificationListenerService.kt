package com.example.dynamicisland.service

import android.app.Notification
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.dynamicisland.state.IslandEventBus
import com.example.dynamicisland.state.IslandState
import kotlinx.coroutines.*

class IslandNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ignoredPackages = setOf(
        "com.example.dynamicisland", "android",
        "com.android.systemui", "com.google.android.gms"
    )

    // ── Media session tracking ──────────────────────────────────────────────
    private var mediaSessionManager: MediaSessionManager? = null
    private val activeControllers = mutableListOf<MediaController>()

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        rebindControllers(controllers ?: emptyList())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = handleMediaUpdate()
        override fun onMetadataChanged(metadata: MediaMetadata?)   = handleMediaUpdate()
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────
    override fun onListenerConnected() {
        super.onListenerConnected()
        // Called when the system properly binds the service — safe to use MediaSessionManager here
        runCatching {
            val component = ComponentName(this, IslandNotificationListenerService::class.java)
            mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
            val sessions = mediaSessionManager?.getActiveSessions(component) ?: emptyList()
            rebindControllers(sessions)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener) }
        activeControllers.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        activeControllers.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Notification posted ─────────────────────────────────────────────────
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg in ignoredPackages) return
        val notification = sbn.notification ?: return

        // Media/transport notifications → handled via MediaSession callbacks above.
        // Only forward regular notifications to the island.
        val isMedia = notification.category == Notification.CATEGORY_TRANSPORT ||
                (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0

        if (isMedia) {
            // Try to extract track info directly from the media notification extras
            val extras = notification.extras
            val title  = extras.getString(Notification.EXTRA_TITLE) ?: return
            val text   = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            // Only show if it looks like music (has a title and secondary text = artist)
            if (title.isNotBlank()) {
                scope.launch {
                    IslandEventBus.emit(
                        IslandState.MusicPlaying(
                            trackTitle = title,
                            artistName = text,
                            albumArt   = null,
                            isPlaying  = true,
                            progress   = 0f
                        )
                    )
                }
            }
            return
        }

        // Foreground services without media → skip
        if ((notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        scope.launch {
            val extras = notification.extras
            val title  = extras.getString(Notification.EXTRA_TITLE) ?: return@launch
            val text   = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            IslandEventBus.emit(
                IslandState.Notification(
                    appName     = getAppName(pkg),
                    title       = title,
                    text        = text,
                    icon        = getNotificationIcon(sbn),
                    packageName = pkg
                )
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    // ── Media session helpers ───────────────────────────────────────────────
    private fun rebindControllers(controllers: List<MediaController>) {
        activeControllers.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        activeControllers.clear()
        controllers.forEach {
            it.registerCallback(controllerCallback)
            activeControllers.add(it)
        }
        handleMediaUpdate()
    }

    private fun handleMediaUpdate() {
        // Find the first controller that is actively playing
        val playing = activeControllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }

        if (playing == null) {
            // Check if any is paused
            val paused = activeControllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PAUSED
            }
            if (paused != null) {
                emitMusic(paused, isPlaying = false)
            } else {
                scope.launch { IslandEventBus.emit(IslandState.Idle) }
            }
            return
        }
        emitMusic(playing, isPlaying = true)
    }

    private fun emitMusic(controller: MediaController, isPlaying: Boolean) {
        val meta     = controller.metadata ?: return
        val title    = meta.getString(MediaMetadata.METADATA_KEY_TITLE)   ?: return
        val artist   = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)  ?: ""
        val albumArt = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 } ?: 1L
        val position = controller.playbackState?.position ?: 0L
        val progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        scope.launch {
            IslandEventBus.emit(
                IslandState.MusicPlaying(title, artist, albumArt, isPlaying, progress)
            )
        }
    }

    // ── Util ────────────────────────────────────────────────────────────────
    private fun getAppName(packageName: String): String = runCatching {
        val info: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun getNotificationIcon(sbn: StatusBarNotification): Bitmap? = runCatching {
        val icon = sbn.notification.smallIcon?.loadDrawable(this) ?: return@runCatching null
        val size = (40 * resources.displayMetrics.density).toInt()
        if (icon is BitmapDrawable) icon.bitmap
        else {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, size, size); icon.draw(Canvas(bmp)); bmp
        }
    }.getOrNull()
}
