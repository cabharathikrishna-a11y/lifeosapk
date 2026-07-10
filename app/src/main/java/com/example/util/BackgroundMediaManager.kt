package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object BackgroundMediaManager {
    private val TAG = "BackgroundMediaManager"
    private var mediaPlayer: MediaPlayer? = null

    private val _currentPlayingPath = MutableStateFlow<String?>(null)
    val currentPlayingPath: StateFlow<String?> = _currentPlayingPath

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    fun play(path: String, startOffsetMs: Int = 0, onComplete: () -> Unit = {}) {
        try {
            stop()
            _currentPlayingPath.value = path
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                if (startOffsetMs > 0) {
                    seekTo(startOffsetMs)
                }
                start()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPlayingPath.value = null
                    onComplete()
                }
            }
            _isPlaying.value = true
            Log.d(TAG, "Started playback for: $path from: ${startOffsetMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing media: $path", e)
            _isPlaying.value = false
            _currentPlayingPath.value = null
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _isPlaying.value = false
                    Log.d(TAG, "Paused playback")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing media", e)
        }
    }

    fun resume() {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    _isPlaying.value = true
                    Log.d(TAG, "Resumed playback")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming media", e)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            _currentPlayingPath.value = null
            _isPlaying.value = false
            Log.d(TAG, "Stopped and released previous MediaPlayer")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media", e)
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
