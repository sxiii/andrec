package com.andrec.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var projectViewModel: ProjectViewModel
    private lateinit var audioRecorderManager: AudioRecorderManager

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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectViewModel = ProjectViewModel()
        audioRecorderManager = AudioRecorderManager(this)

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
                        onOpenProjectFolder = { openProjectLauncher.launch(null) }
                    )
                }
            }
        }
    }

    private var currentRecordingFile: File? = null

    private fun handleNewRec() {
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
    }

    private fun handlePlay() {
        projectViewModel.isPlaying = !projectViewModel.isPlaying
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
    onOpenProjectFolder: () -> Unit
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenProjectFolder, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33334D))) {
                        Text("Open Folder", fontSize = 12.sp)
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
                            text = if (viewModel.isRecording) "● RECORDING (${String.format("%.2f", viewModel.currentAmplitude)})" else "Timeline Items: ${viewModel.timelineItems.size}",
                            color = if (viewModel.isRecording) Color.Red else Color.Cyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            val width = size.width
                            val height = size.height
                            val centerY = height / 2f
                            drawLine(Color.DarkGray, Offset(0f, centerY), Offset(width, centerY), 1f)

                            val waveColor = if (viewModel.isRecording) Color.Red else Color(0xFF00E676)
                            val step = 10f
                            var x = 0f
                            while (x < width) {
                                val ampHeight = if (viewModel.isRecording) (viewModel.currentAmplitude * centerY * (0.5f + Math.random().toFloat() * 0.5f)) else (height * 0.2f * (0.3f + Math.sin(x.toDouble() * 0.05).toFloat() * 0.7f))
                                drawLine(waveColor, Offset(x, centerY - ampHeight), Offset(x, centerY + ampHeight), 2f)
                                x += step
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(viewModel.timelineItems) { item ->
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF2A2A3D), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(item.file.name, color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onReRec,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "ReREC")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ReREC")
                    }
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DELETE")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE")
                    }
                    Button(
                        onClick = onNewRec,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "NewREC")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("NewREC")
                    }
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.isPlaying) Color.Yellow else Color(0xFF00796B))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (viewModel.isPlaying) "PAUSE" else "PLAY")
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { if (viewModel.fontSizeSp > 10f) viewModel.fontSizeSp -= 2f },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33334D)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${viewModel.fontSizeSp.toInt()}sp", color = Color.LightGray, fontSize = 12.sp)
                    Button(
                        onClick = { if (viewModel.fontSizeSp < 36f) viewModel.fontSizeSp += 2f },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33334D)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onPickTextFile,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3949AB)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Open .txt", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(0xFF0D0D12.let { Color(it) }, RoundedCornerShape(8.dp))
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
