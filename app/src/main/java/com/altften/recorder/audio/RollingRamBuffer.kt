package com.altften.recorder.audio

import com.altften.recorder.data.AudioChunk
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe rolling RAM buffer that holds chunks in slots (chunk1, chunk2, chunk3).
 * When slot 3 is reached, the slot counter wraps back to 1 and overwrites slot 1 in RAM.
 *
 * When the user taps "Save last X chunks":
 * Each requested chunk is saved to persistent storage as a separate .wav file named
 * with its save timestamp and original chunk slot name (e.g. saved_20261031_170500_chunk1_120s.wav)
 * so it will never be overwritten.
 */
class RollingRamBuffer(
    @Volatile var capacity: Int = 3
) {
    // Map of slotIndex (1, 2, 3) -> AudioChunk
    private val slotMap = mutableMapOf<Int, AudioChunk>()
    // Chronological order of chunks pushed
    private val history = mutableListOf<AudioChunk>()
    private val lock = Any()

    /**
     * Stores the chunk in its designated slot (1..capacity), overwriting any previous chunk in that slot.
     */
    fun pushChunk(chunk: AudioChunk) {
        synchronized(lock) {
            val slot = chunk.slotIndex
            slotMap[slot] = chunk

            // Keep chronological history up to capacity
            history.removeAll { it.slotIndex == slot }
            history.add(chunk)
            while (history.size > capacity) {
                history.removeAt(0)
            }
        }
    }

    fun getChunkCount(): Int = synchronized(lock) { history.size }

    fun getTotalMemoryBytes(): Long = synchronized(lock) {
        slotMap.values.sumOf { it.sizeBytes.toLong() }
    }

    /**
     * Returns the last [count] chunks in chronological order (oldest to newest).
     */
    fun getRecentChunks(count: Int): List<AudioChunk> = synchronized(lock) {
        if (history.size <= count) {
            history.toList()
        } else {
            history.takeLast(count)
        }
    }

    fun clear() {
        synchronized(lock) {
            slotMap.clear()
            history.clear()
        }
    }

    /**
     * Saves each of the last [count] chunks as an individual, separate .wav file
     * with the save timestamp so they are permanently preserved.
     */
    fun saveLastChunksToDisk(
        count: Int,
        outputDir: File,
        prefix: String = "saved"
    ): Result<List<File>> = runCatching {
        val targetChunks = getRecentChunks(count)
        if (targetChunks.isEmpty()) {
            throw IllegalStateException("No chunks available in RAM buffer to save")
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val savedFiles = mutableListOf<File>()

        for (chunk in targetChunks) {
            // Distinct file for each chunk: e.g. saved_20261031_170520_chunk1_120s.wav
            val fileName = "${prefix}_${timestamp}_${chunk.chunkName}_${chunk.durationSec}s.wav"
            val outputFile = File(outputDir, fileName)

            WavWriter.writePcmToWav(
                outputFile = outputFile,
                pcmByteArrays = listOf(chunk.pcmData),
                sampleRate = chunk.sampleRate
            )
            savedFiles.add(outputFile)
        }

        savedFiles
    }
}
