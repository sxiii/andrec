package com.andrec.app

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null
    private var isPlaying = false
    private var onCompletion: (() -> Unit)? = null
    private var onProgress: ((Int, Int) -> Unit)? = null

    fun playFile(file: File, onComplete: () -> Unit = {}, onProgressUpdate: (Int, Int) -> Unit = { _, _ -> }) {
        if (!file.exists()) {
            return
        }

        stopPlayback()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }

            currentFile = file
            isPlaying = true
            onCompletion = onComplete
            onProgress = onProgressUpdate

            mediaPlayer?.setOnCompletionListener {
                isPlaying = false
                onCompletion?.invoke()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            stopPlayback()
        }
    }

    fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
            }
        }
    }

    fun resumePlayback() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                isPlaying = true
            }
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            currentFile = null
            isPlaying = false
        }
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun release() {
        stopPlayback()
    }
}
