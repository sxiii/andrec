package com.andrec.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

data class TimelineItem(
    val id: String,
    val file: File,
    val durationSeconds: Float = 0f,
    val sampleRate: Int = 48000,
    val channels: Int = 2,
    val bitDepth: Int = 16
)

class ProjectViewModel {
    var projectFolder by mutableStateOf<File?>(null)
    var timelineItems = mutableListOf<TimelineItem>()
        private set
    var isRecording by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var currentPlayingItem by mutableStateOf<TimelineItem?>(null)
    var currentAmplitude by mutableStateOf(0f)
    var zoomDurationSeconds by mutableStateOf(10f)

    var sampleRate by mutableStateOf(48000)
    var channels by mutableStateOf(2)
    var bitDepth by mutableStateOf(16)

    var textFileContent by mutableStateOf("No text file loaded. Click 'Open .txt' to select a text file.")
    var textFileName by mutableStateOf("No file")
    var fontSizeSp by mutableStateOf(16f)

    val wordCount: Int
        get() = if (textFileContent.isBlank()) 0 else textFileContent.trim().split(Regex("\\s+")).size
    val charCount: Int
        get() = textFileContent.length

    fun createNewProject(baseDir: File, projectName: String): File {
        val proj = File(baseDir, projectName)
        if (!proj.exists()) proj.mkdirs()
        projectFolder = proj
        timelineItems.clear()
        return proj
    }

    fun openProject(folder: File) {
        projectFolder = folder
        timelineItems.clear()
        folder.listFiles()?.forEach { file ->
            if (file.isFile && (file.extension.lowercase() == "wav" || file.extension.lowercase() == "flac" || file.extension.lowercase() == "mp3")) {
                timelineItems.add(
                    TimelineItem(
                        id = file.nameWithoutExtension,
                        file = file,
                        durationSeconds = 5f
                    )
                )
            }
        }
    }

    fun addTimelineItem(file: File): TimelineItem {
        val item = TimelineItem(
            id = System.currentTimeMillis().toString(),
            file = file,
            durationSeconds = 3f
        )
        timelineItems.add(item)
        return item
    }

    fun removeLastTimelineItem(): TimelineItem? {
        if (timelineItems.isNotEmpty()) {
            val item = timelineItems.removeAt(timelineItems.size - 1)
            if (item.file.exists()) item.file.delete()
            return item
        }
        return null
    }

    fun loadTextFile(file: File) {
        try {
            textFileName = file.name
            textFileContent = file.readText()
        } catch (e: Exception) {
            textFileContent = "Error reading file: ${e.localizedMessage}"
        }
    }
}
