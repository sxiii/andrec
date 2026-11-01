package com.andrec.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var projectViewModel: ProjectViewModel
    private lateinit var audioRecorderManager: AudioRecorderManager
    private lateinit var audioPlayerManager: AudioPlayerManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordGranted) {
            Toast.makeText(this, "Recording permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Recording permission required", Toast.LENGTH_LONG).show()
        }
    }

    private val selectTextFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val text = inputStream.bufferedReader().readText()
                projectViewModel.textFileContent = text
                projectViewModel.textFileName = "Selected file"
            }
        }
    }

    private val openProjectLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val rootDir = File(filesDir, "Projects")
            if (!rootDir.exists()) rootDir.mkdirs()
            val newProj = File(rootDir, "Project_" + System.currentTimeMillis().toString())
            newProj.mkdirs()
            projectViewModel.openProject(newProj)
            Toast.makeText(this, "Opened project: ${newProj.name}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectViewModel = ProjectViewModel()
        audioRecorderManager = AudioRecorderManager(this)
        audioPlayerManager = AudioPlayerManager(this)

        // Request permissions
        checkAndRequestPermissions()

        val defaultProj = File(filesDir, "AndrecDefaultProject")
        if (!defaultProj.exists()) defaultProj.mkdirs()
        projectViewModel.openProject(defaultProj)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = projectViewModel,
                        onNewRec = { handleNewRec() },
                        onReRec = { handleReRec() },
                        onSave = { handleSave() },
                        onDelete = { handleDelete() },
                        onPlay = { handlePlay() },
                        onPickTextFile = { selectTextFileLauncher.launch(arrayOf("text/plain")) },
                        onOpenProjectFolder = { openProjectLauncher.launch(null) },
                        onPlayItem = { item -> handlePlayItem(item) },
                        onExportItem = { item -> handleExportItem(item) }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        // For Android 9 and below, request WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private var currentRecordingFile: File? = null

    private fun handleNewRec() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestPermissions()
            return
        }

        // Stop playback if playing
        if (projectViewModel.isPlaying) {
            audioPlayerManager.stopPlayback()
            projectViewModel.isPlaying = false
        }

        val projFolder = projectViewModel.projectFolder ?: File(filesDir, "AndrecDefaultProject")
        if (!projFolder.exists()) projFolder.mkdirs()

        if (projectViewModel.isRecording) {
            audioRecorderManager.stopRecording()
        }

        val newFile = File(projFolder, "rec_${System.currentTimeMillis()}.wav")
        currentRecordingFile = newFile

        val config = AudioRecorderManager.AudioConfig(
            sampleRate = projectViewModel.sampleRate,
            channelMode = projectViewModel.channels,
            bitDepth = projectViewModel.bitDepth
        )

        val success = audioRecorderManager.startRecording(newFile, config) { amp ->
            projectViewModel.currentAmplitude = amp
        }

        if (success) {
            projectViewModel.isRecording = true
            projectViewModel.addTimelineItem(newFile)
        } else {
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleReRec() {
        if (projectViewModel.isRecording) {
            audioRecorderManager.stopRecording()
            projectViewModel.isRecording = false
            currentRecordingFile?.let {
                if (it.exists()) it.delete()
            }
            projectViewModel.removeLastTimelineItem()
        }
        handleNewRec()
    }

    private fun handleSave() {
        if (projectViewModel.isRecording) {
            audioRecorderManager.stopRecording()
            projectViewModel.isRecording = false
            currentRecordingFile?.let { file ->
                val projFolder = projectViewModel.projectFolder
                if (projFolder != null && file.exists()) {
                    // Now also save to public Music/Andrec folder
                    val uri = audioRecorderManager.saveToPublicFolder(file)
                    if (uri != null) {
                        Toast.makeText(
                            this,
                            "Recording saved to:\n1. Project: ${projFolder.absolutePath}\n2. Music/Andrec folder",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Recording saved to project: ${projFolder.absolutePath}\n(Failed to copy to Music folder)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()
                }
            }
            currentRecordingFile = null
        }
    }

    private fun handleDelete() {
        if (projectViewModel.isRecording) {
            audioRecorderManager.stopRecording()
            projectViewModel.isRecording = false
            currentRecordingFile = null
        }
        projectViewModel.removeLastTimelineItem()
        Toast.makeText(this, "Last recording deleted", Toast.LENGTH_SHORT).show()
    }

    private fun handlePlay() {
        if (projectViewModel.isRecording) {
            return // Cannot play while recording
        }

        if (projectViewModel.isPlaying) {
            audioPlayerManager.stopPlayback()
            projectViewModel.isPlaying = false
            projectViewModel.currentPlayingItem = null
        } else {
            // Play all timeline items in sequence
            val items = projectViewModel.timelineItems
            if (items.isNotEmpty()) {
                projectViewModel.isPlaying = true
                playNextItem(0)
            }
        }
    }

    private fun playNextItem(index: Int) {
        val items = projectViewModel.timelineItems
        if (index >= items.size || !projectViewModel.isPlaying) {
            projectViewModel.isPlaying = false
            projectViewModel.currentPlayingItem = null
            return
        }

        val item = items[index]
        projectViewModel.currentPlayingItem = item
        audioPlayerManager.playFile(item.file, onComplete = {
            playNextItem(index + 1)
        })
    }

    private fun handlePlayItem(item: TimelineItem) {
        if (projectViewModel.isRecording) {
            return // Cannot play while recording
        }

        if (projectViewModel.currentPlayingItem == item && projectViewModel.isPlaying) {
            // Stop current playback
            audioPlayerManager.stopPlayback()
            projectViewModel.isPlaying = false
            projectViewModel.currentPlayingItem = null
        } else {
            // Play this item
            audioPlayerManager.stopPlayback()
            projectViewModel.isPlaying = true
            projectViewModel.currentPlayingItem = item
            audioPlayerManager.playFile(item.file, onComplete = {
                projectViewModel.isPlaying = false
                projectViewModel.currentPlayingItem = null
            })
        }
    }

    private fun handleExportItem(item: TimelineItem) {
        if (!item.file.exists()) {
            Toast.makeText(this, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = audioRecorderManager.saveToPublicFolder(item.file)
        if (uri != null) {
            Toast.makeText(this, "Saved to Music/Andrec folder\nPath: Music/Andrec/${item.file.name}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Failed to save to public folder. Check storage permissions.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_RECORD, KeyEvent.KEYCODE_R -> { handleNewRec(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_P -> { handlePlay(); return true }
            KeyEvent.KEYCODE_S -> { handleSave(); return true }
            KeyEvent.KEYCODE_D -> { handleDelete(); return true }
            KeyEvent.KEYCODE_F5 -> { handleReRec(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorderManager.stopRecording()
        audioPlayerManager.release()
    }
}

@Composable
fun MainScreen(
    viewModel: ProjectViewModel,
    onNewRec: () -> Unit,
    onReRec: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onPickTextFile: () -> Unit,
    onOpenProjectFolder: () -> Unit,
    onPlayItem: (TimelineItem) -> Unit,
    onExportItem: (TimelineItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121218))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .background(Color(0xFF1A1A24), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Project: ${viewModel.projectFolder?.name ?: "None"}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenProjectFolder,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33334D)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Open Folder", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Folder", fontSize = 12.sp)
                    }
                    Text(
                        text = "${viewModel.sampleRate/1000}kHz / ${viewModel.bitDepth}bit / ${if(viewModel.channels==2) "Stereo" else "Mono"}",
                        color = Color(0xFF00E676),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color(0xFF222233), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0D0D12), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF33334D), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (viewModel.timelineItems.isEmpty() && !viewModel.isRecording) {
                    Text("Timeline is empty. Press NewREC to start.", color = Color.Gray, fontSize = 14.sp)
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = if (viewModel.isRecording) {
                                "● RECORDING (${String.format("%.2f", viewModel.currentAmplitude)})"
                            } else if (viewModel.isPlaying) {
                                "▶ PLAYING: ${viewModel.currentPlayingItem?.file?.name ?: "All"}"
                            } else {
                                "Timeline Items: ${viewModel.timelineItems.size}"
                            },
                            color = if (viewModel.isRecording) Color.Red else if (viewModel.isPlaying) Color.Green else Color.Cyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            val width = size.width
                            val height = size.height
                            val centerY = height / 2f
                            drawLine(Color.DarkGray, Offset(0f, centerY), Offset(width, centerY), 1f)

                            val waveColor = when {
                                viewModel.isRecording -> Color.Red
                                viewModel.isPlaying -> Color.Green
                                else -> Color(0xFF00E676)
                            }
                            val step = 10f
                            var x = 0f
                            while (x < width) {
                                val ampHeight = if (viewModel.isRecording) {
                                    (viewModel.currentAmplitude * centerY * (0.5f + Math.random().toFloat() * 0.5f))
                                } else {
                                    (height * 0.2f * (0.3f + Math.sin(x.toDouble() * 0.05).toFloat() * 0.7f))
                                }
                                drawLine(waveColor, Offset(x, centerY - ampHeight), Offset(x, centerY + ampHeight), 2f)
                                x += step
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(viewModel.timelineItems) { item ->
                                val isPlaying = viewModel.currentPlayingItem == item && viewModel.isPlaying
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isPlaying) Color(0xFF2E7D32) else Color(0xFF2A2A3D),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onPlayItem(item) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isPlaying) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Playing",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            item.file.name,
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        IconButton(
                                            onClick = { onExportItem(item) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = "Export to Music folder",
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onReRec,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        enabled = !viewModel.isPlaying
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "ReREC", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ReREC", fontSize = 14.sp)
                    }
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        enabled = !viewModel.isPlaying
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DELETE", fontSize = 14.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                        enabled = !viewModel.isPlaying
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE", fontSize = 14.sp)
                    }
                    Button(
                        onClick = onNewRec,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        enabled = !viewModel.isPlaying
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "NewREC", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("NewREC", fontSize = 14.sp)
                    }
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.isPlaying) Color(0xFFFFB300) else Color(0xFF00796B)
                        ),
                        enabled = viewModel.timelineItems.isNotEmpty() && !viewModel.isRecording
                    ) {
                        Icon(
                            if (viewModel.isPlaying) Icons.Default.Clear else Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (viewModel.isPlaying) "STOP" else "PLAY", fontSize = 14.sp)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.8f)
                .fillMaxHeight()
                .background(Color(0xFF1A1A24), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = viewModel.textFileName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (viewModel.fontSizeSp > 10f) viewModel.fontSizeSp -= 2f },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33334D)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${viewModel.fontSizeSp.toInt()}sp", color = Color.LightGray, fontSize = 12.sp)
                    Button(
                        onClick = { if (viewModel.fontSizeSp < 36f) viewModel.fontSizeSp += 2f },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33334D)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onPickTextFile,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3949AB)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Open .txt", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0D0D12), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF33334D), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val paragraphs = viewModel.textFileContent.split("\n")
                    paragraphs.forEach { paragraph ->
                        Text(
                            text = paragraph,
                            color = Color.White,
                            fontSize = viewModel.fontSizeSp.sp,
                            lineHeight = (viewModel.fontSizeSp * 1.4f).sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Words: ${viewModel.wordCount}",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Text(
                    text = "Characters: ${viewModel.charCount}",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
    }
}
