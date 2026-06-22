package com.lixoo.editor.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(UnstableApi::class)
class PreviewEngine(private val context: Context) {
    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    fun initialize() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }
                })
            }
        }
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun setVideoSource(path: String, startMs: Long = 0, endMs: Long = -1, effects: List<Effect> = emptyList()) {
        val clippingConfiguration = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .apply {
                if (endMs > 0) setEndPositionMs(endMs)
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(path)
            .setClippingConfiguration(clippingConfiguration)
            .build()

        // Note: For real-time effects in ExoPlayer preview,
        // we'd typically use a custom RenderersFactory or Effect-enabled composition.
        // For simplicity in this initial version, we focus on the structure.

        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
    }

    fun setSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
    }

    fun play() { exoPlayer?.play() }
    fun pause() { exoPlayer?.pause() }
    fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
