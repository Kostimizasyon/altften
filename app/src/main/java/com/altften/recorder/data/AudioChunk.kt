package com.altften.recorder.data

data class AudioChunk(
    val slotIndex: Int, // 1, 2, or 3
    val timestampMs: Long,
    val pcmData: ByteArray,
    val sampleRate: Int,
    val durationMs: Long
) {
    val sizeBytes: Int
        get() = pcmData.size

    val sizeMb: Float
        get() = pcmData.size / (1024f * 1024f)

    val durationSec: Int
        get() = (durationMs / 1000L).toInt()

    val chunkName: String
        get() = "chunk$slotIndex"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioChunk
        return slotIndex == other.slotIndex && timestampMs == other.timestampMs
    }

    override fun hashCode(): Int {
        var result = slotIndex
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}
