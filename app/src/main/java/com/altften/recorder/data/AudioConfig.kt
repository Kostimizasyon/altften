package com.altften.recorder.data

enum class AudioQuality(
    val label: String,
    val sampleRate: Int,
    val description: String
) {
    LOW("Low (Saver)", 16000, "16kHz Mono • Lowest CPU & RAM usage (~3.8MB/chunk)"),
    MEDIUM("Medium", 24000, "24kHz Mono • Balanced voice fidelity (~5.7MB/chunk)"),
    HIGH("High", 44100, "44.1kHz Mono • Highest clarity (~10.5MB/chunk)")
}

data class RecordingConfig(
    val quality: AudioQuality = AudioQuality.LOW,
    val chunkDurationSec: Int = 120, // Max 120 seconds (2 mins)
    val bufferCapacity: Int = 3,    // Rolling buffer size (default 3 chunks)
    val chunksToSave: Int = 2        // Default number of recent chunks to persist when button is clicked
) {
    init {
        require(chunkDurationSec in 10..120) { "Chunk duration must be between 10 and 120 seconds" }
        require(bufferCapacity in 2..10) { "Buffer capacity must be between 2 and 10" }
        require(chunksToSave in 1..bufferCapacity) { "Chunks to save must not exceed buffer capacity" }
    }

    val maxRollingTimeSeconds: Int
        get() = chunkDurationSec * bufferCapacity

    val saveDurationSeconds: Int
        get() = chunkDurationSec * chunksToSave
}
