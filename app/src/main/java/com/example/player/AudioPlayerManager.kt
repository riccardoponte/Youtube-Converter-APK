package com.example.player

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlayerState(
    val isPlaying: Boolean = false,
    val trackTitle: String = "",
    val trackAuthor: String = "",
    val thumbnailUrl: String = "",
    val filePath: String = "",
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val isVisible: Boolean = false
)

class AudioPlayerManager(private val scope: CoroutineScope) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun playTrack(filePath: String, title: String, author: String, thumbnailUrl: String) {
        val file = File(filePath)
        if (!file.exists()) return

        stopTrack()

        try {
            val player = MediaPlayer()
            player.setDataSource(filePath)
            player.prepare()
            player.start()
            mediaPlayer = player

            _playerState.value = PlayerState(
                isPlaying = true,
                trackTitle = title,
                trackAuthor = author,
                thumbnailUrl = thumbnailUrl,
                filePath = filePath,
                currentPositionMs = 0,
                durationMs = player.duration.coerceAtLeast(1000),
                isVisible = true
            )

            player.setOnCompletionListener {
                _playerState.value = _playerState.value.copy(isPlaying = false, currentPositionMs = 0)
                progressJob?.cancel()
            }

            startProgressPolling()
        } catch (e: Exception) {
            e.printStackTrace()
            _playerState.value = _playerState.value.copy(isPlaying = false)
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false)
            progressJob?.cancel()
        } else {
            player.start()
            _playerState.value = _playerState.value.copy(isPlaying = true)
            startProgressPolling()
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
    }

    fun stopTrack() {
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    fun closePlayer() {
        stopTrack()
        _playerState.value = PlayerState(isVisible = false)
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playerState.value = _playerState.value.copy(
                            currentPositionMs = player.currentPosition,
                            durationMs = player.duration.coerceAtLeast(1000)
                        )
                    }
                }
                delay(400)
            }
        }
    }
}
