package com.example.dynamicisland.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager as AndroidMediaSessionManager
import android.media.session.PlaybackState
import com.example.dynamicisland.service.IslandNotificationListenerService
import com.example.dynamicisland.state.IslandEventBus
import com.example.dynamicisland.state.IslandState
import kotlinx.coroutines.*

class IslandMediaSessionManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var androidSessionManager: AndroidMediaSessionManager? = null
    private var activeController: MediaController? = null

    private val sessionListener = AndroidMediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        onSessionsChanged(controllers ?: emptyList())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            handlePlaybackState(activeController, state)
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val state = activeController?.playbackState ?: return
            handlePlaybackState(activeController, state, metadata)
        }
    }

    fun start() {
        runCatching {
            val componentName = ComponentName(context, IslandNotificationListenerService::class.java)
            androidSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as AndroidMediaSessionManager
            androidSessionManager?.addOnActiveSessionsChangedListener(sessionListener, componentName)
            val sessions = androidSessionManager?.getActiveSessions(componentName) ?: return
            onSessionsChanged(sessions)
        }
    }

    fun stop() {
        runCatching { androidSessionManager?.removeOnActiveSessionsChangedListener(sessionListener) }
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        scope.cancel()
    }

    private fun onSessionsChanged(controllers: List<MediaController>) {
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        activeController = playing
        playing?.registerCallback(controllerCallback)
        handlePlaybackState(playing, playing?.playbackState, playing?.metadata)
    }

    private fun handlePlaybackState(
        controller: MediaController?,
        state: PlaybackState?,
        metadata: MediaMetadata? = controller?.metadata
    ) {
        if (controller == null || state == null) return
        when (state.state) {
            PlaybackState.STATE_PLAYING -> {
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Desconocido"
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                val albumArt: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 1L
                val progress = if (duration > 0) (state.position.toFloat() / duration.toFloat()) else 0f
                scope.launch { IslandEventBus.emit(IslandState.MusicPlaying(title, artist, albumArt, true, progress)) }
            }
            PlaybackState.STATE_PAUSED -> {
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
                scope.launch {
                    IslandEventBus.emit(IslandState.MusicPlaying(
                        title,
                        metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                        metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
                        false, 0f
                    ))
                }
            }
            PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE ->
                scope.launch { IslandEventBus.emit(IslandState.Idle) }
            else -> Unit
        }
    }
}
