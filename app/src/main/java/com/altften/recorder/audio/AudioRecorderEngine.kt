package com.altften.recorder.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.altften.recorder.data.AudioChunk
import com.altften.recorder.data.RecordingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

data class EngineProgress(
    val isRecording: Boolean = false,
    val currentSlotIndex: Int = 1, // 1, 2, or 3 (clamped)
    val currentChunkElapsedSec: Int = 0,
    val targetChunkDurationSec: Int = 120,
    val totalChunksInBuffer: Int = 0,
    val ramUsedBytes: Long = 0,
    val amplitudeRms: Float = 0f
)

class AudioRecorderEngine(
    private val ramBuffer: RollingRamBuffer,
    private val scope: CoroutineScope
) {
    private val isRecording = AtomicBoolean(false)
    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private var activeConfig = RecordingConfig()
    private var currentSlot = 1 // 1, 2, 3 -> wraps back to 1
    private var bytesPerSecond = 16000 * 2

    private val streamLock = Any()
    private var currentChunkStream = ByteArrayOutputStream()
    private var currentChunkStartMs = System.currentTimeMillis()

    private val _progress = MutableStateFlow(EngineProgress())
    val progress: StateFlow<EngineProgress> = _progress.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(config: RecordingConfig) {
        if (isRecording.getAndSet(true)) return

        activeConfig = config
        ramBuffer.capacity = config.bufferCapacity
        val sampleRate = config.quality.sampleRate
        bytesPerSecond = sampleRate * 2 // 16-bit mono = 2 bytes per sample

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val readBufferSize = maxOf(minBufferSize * 2, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                readBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                isRecording.set(false)
                Log.e(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
        } catch (e: Exception) {
            isRecording.set(false)
            Log.e(TAG, "Failed to start AudioRecord", e)
            return
        }

        currentSlot = 1
        synchronized(streamLock) {
            currentChunkStream = ByteArrayOutputStream(config.chunkDurationSec * bytesPerSecond)
            currentChunkStartMs = System.currentTimeMillis()
        }

        recordJob = scope.launch(Dispatchers.IO) {
            val readBuffer = ByteArray(readBufferSize)
            val targetChunkSizeBytes = config.chunkDurationSec * bytesPerSecond

            _progress.value = EngineProgress(
                isRecording = true,
                currentSlotIndex = currentSlot,
                targetChunkDurationSec = config.chunkDurationSec,
                totalChunksInBuffer = ramBuffer.getChunkCount(),
                ramUsedBytes = ramBuffer.getTotalMemoryBytes()
            )

            while (isActive && isRecording.get()) {
                val bytesRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                if (bytesRead > 0) {
                    var shouldRotate = false
                    var streamSize: Int
                    var elapsedSec: Int

                    synchronized(streamLock) {
                        currentChunkStream.write(readBuffer, 0, bytesRead)
                        streamSize = currentChunkStream.size()
                        elapsedSec = (streamSize / bytesPerSecond).coerceAtMost(config.chunkDurationSec)

                        if (streamSize >= targetChunkSizeBytes) {
                            shouldRotate = true
                        }
                    }

                    if (shouldRotate) {
                        finalizeCurrentChunkAndAdvance()
                    }

                    // Compute RMS amplitude for live UI feedback
                    var sumSquares = 0.0
                    val samples = bytesRead / 2
                    for (i in 0 until bytesRead step 2) {
                        val sample = (readBuffer[i].toInt() and 0xFF) or (readBuffer[i + 1].toInt() shl 8)
                        val sampleShort = sample.toShort()
                        sumSquares += sampleShort * sampleShort
                    }
                    val rms = if (samples > 0) sqrt(sumSquares / samples).toFloat() else 0f

                    _progress.value = EngineProgress(
                        isRecording = true,
                        currentSlotIndex = currentSlot,
                        currentChunkElapsedSec = elapsedSec,
                        targetChunkDurationSec = config.chunkDurationSec,
                        totalChunksInBuffer = ramBuffer.getChunkCount(),
                        ramUsedBytes = ramBuffer.getTotalMemoryBytes() + streamSize,
                        amplitudeRms = rms
                    )
                } else {
                    delay(20)
                }
            }

            // Cleanup when stopped
            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audioRecord", e)
            }

            _progress.value = EngineProgress(
                isRecording = false,
                totalChunksInBuffer = ramBuffer.getChunkCount(),
                ramUsedBytes = ramBuffer.getTotalMemoryBytes()
            )
        }
    }

    /**
     * Cuts the active in-progress audio (e.g. 1m 15s) immediately into the current slot,
     * stores it in the RAM buffer, and advances to the next slot (1 -> 2 -> 3 -> 1)
     * without stopping the microphone or dropping any audio samples.
     */
    fun cutCurrentChunkAndAdvance(): AudioChunk? {
        if (!isRecording.get()) return null
        return finalizeCurrentChunkAndAdvance()
    }

    private fun finalizeCurrentChunkAndAdvance(): AudioChunk? {
        val chunkData: ByteArray
        val slot: Int
        val startMs: Long

        synchronized(streamLock) {
            chunkData = currentChunkStream.toByteArray()
            slot = currentSlot
            startMs = currentChunkStartMs

            // Advance slot counter with clamping: 1 -> 2 -> 3 -> 1 -> 2...
            currentSlot = if (currentSlot >= activeConfig.bufferCapacity) 1 else currentSlot + 1

            // Reset stream for next chunk
            currentChunkStream = ByteArrayOutputStream(activeConfig.chunkDurationSec * bytesPerSecond)
            currentChunkStartMs = System.currentTimeMillis()
        }

        if (chunkData.isEmpty()) return null

        val durationMs = (chunkData.size * 1000L) / bytesPerSecond
        val chunk = AudioChunk(
            slotIndex = slot,
            timestampMs = startMs,
            pcmData = chunkData,
            sampleRate = activeConfig.quality.sampleRate,
            durationMs = durationMs
        )

        // Store into RAM buffer (overwriting slot in RAM)
        ramBuffer.pushChunk(chunk)

        _progress.value = _progress.value.copy(
            currentSlotIndex = currentSlot,
            currentChunkElapsedSec = 0,
            totalChunksInBuffer = ramBuffer.getChunkCount(),
            ramUsedBytes = ramBuffer.getTotalMemoryBytes()
        )

        return chunk
    }

    fun stop() {
        if (!isRecording.getAndSet(false)) return
        recordJob?.cancel()
        recordJob = null
    }

    companion object {
        private const val TAG = "AudioRecorderEngine"
    }
}
