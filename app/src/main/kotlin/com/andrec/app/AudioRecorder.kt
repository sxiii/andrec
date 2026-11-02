package com.andrec.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorderManager(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    data class AudioConfig(
        val sampleRate: Int = 48000,
        val channelMode: Int = AudioFormat.CHANNEL_IN_STEREO,
        val bitDepth: Int = 16
    )

    fun startRecording(file: File, config: AudioConfig, onAmplitude: (Float) -> Unit): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val channelConfig = if (config.channelMode == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val audioFormat = when (config.bitDepth) {
            32 -> AudioFormat.ENCODING_PCM_FLOAT
            else -> AudioFormat.ENCODING_PCM_16BIT
        }

        val minBufferSize = AudioRecord.getMinBufferSize(config.sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return false
        }

        val bufferSize = maxOf(minBufferSize * 2, config.sampleRate * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                writeWavData(file, config, onAmplitude)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            audioRecord?.release()
            audioRecord = null
            return false
        }
    }

    private fun writeWavData(file: File, config: AudioConfig, onAmplitude: (Float) -> Unit) {
        val channels = if (config.channelMode == 1) 1 else 2
        val bytesPerSample = when (config.bitDepth) {
            24 -> 3
            32 -> 4
            else -> 2
        }

        val dataFile = File(file.absolutePath + ".tmp")
        var outputStream: FileOutputStream? = null

        val bufferSize = config.sampleRate * channels * bytesPerSample / 10
        val buffer = ByteArray(bufferSize)

        var totalAudioLen = 0L

        try {
            outputStream = FileOutputStream(dataFile)

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    totalAudioLen += read

                    // Calculate RMS amplitude
                    var sum = 0.0
                    val sampleCount = read / bytesPerSample
                    for (i in 0 until sampleCount) {
                        val sample = when (bytesPerSample) {
                            2 -> {
                                val low = buffer[i * 2].toInt() and 0xFF
                                val high = buffer[i * 2 + 1].toInt()
                                val s = (high shl 8) or low
                                s.toFloat() / 32768f
                            }
                            4 -> {
                                ByteBuffer.wrap(buffer, i * 4, 4).order(ByteOrder.LITTLE_ENDIAN).float
                            }
                            else -> 0f
                        }
                        sum += sample * sample
                    }
                    val rms = if (sampleCount > 0) Math.sqrt(sum / sampleCount).toFloat() else 0f
                    onAmplitude(rms.coerceIn(0f, 1f))
                } else if (read < 0) {
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                outputStream?.close()
            } catch (_: Exception) {}

            if (totalAudioLen > 0) {
                writeWavHeader(file, dataFile, totalAudioLen, config.sampleRate, channels, config.bitDepth)
            }
            dataFile.delete()
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        recordingJob = null
    }

    private fun writeWavHeader(outputFile: File, tmpFile: File, totalAudioLen: Long, sampleRate: Int, channels: Int, bitDepth: Int) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitDepth / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = if (bitDepth == 32) 3 else 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitDepth / 8).toByte()
        header[33] = 0
        header[34] = bitDepth.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        try {
            val fos = FileOutputStream(outputFile)
            fos.write(header)
            if (tmpFile.exists()) {
                tmpFile.inputStream().use { it.copyTo(fos) }
            }
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Save a WAV file to the public Music/Recordings folder using MediaStore (Android 10+) or legacy storage
     */
    fun saveToPublicFolder(sourceFile: File, displayName: String? = null): Uri? {
        if (!sourceFile.exists()) {
            return null
        }

        val fileName = displayName ?: "Andrec_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.wav"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Andrec")
                }

                val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
                uri
            } else {
                // Legacy storage for Android 9 and below
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val andrecDir = File(musicDir, "Andrec")
                if (!andrecDir.exists()) {
                    andrecDir.mkdirs()
                }

                val destFile = File(andrecDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)

                // Notify media scanner
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, destFile.absolutePath)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                }
                context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
