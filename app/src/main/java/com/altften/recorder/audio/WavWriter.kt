package com.altften.recorder.audio

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Utility to write raw 16-bit PCM Mono audio data into standard RIFF WAV format.
 */
object WavWriter {

    fun writePcmToWav(
        outputFile: File,
        pcmByteArrays: List<ByteArray>,
        sampleRate: Int,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ) {
        val totalAudioLength = pcmByteArrays.sumOf { it.size.toLong() }
        val totalDataLength = totalAudioLength + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        FileOutputStream(outputFile).use { out ->
            writeHeader(
                out = out,
                totalAudioLength = totalAudioLength,
                totalDataLength = totalDataLength,
                sampleRate = sampleRate.toLong(),
                channels = channels,
                byteRate = byteRate,
                bitsPerSample = bitsPerSample
            )
            for (byteArray in pcmByteArrays) {
                out.write(byteArray)
            }
            out.flush()
        }
    }

    private fun writeHeader(
        out: OutputStream,
        totalAudioLength: Long,
        totalDataLength: Long,
        sampleRate: Long,
        channels: Short,
        byteRate: Long,
        bitsPerSample: Short
    ) {
        val header = ByteArray(44)

        // RIFF/WAVE header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLength and 0xff).toByte()
        header[5] = ((totalDataLength shr 8) and 0xff).toByte()
        header[6] = ((totalDataLength shr 16) and 0xff).toByte()
        header[7] = ((totalDataLength shr 24) and 0xff).toByte()
        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        // 16 for PCM format chunk size
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        // Audio format 1 = PCM
        header[20] = 1
        header[21] = 0
        header[22] = (channels.toInt() and 0xff).toByte()
        header[23] = ((channels.toInt() shr 8) and 0xff).toByte()
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        // Block align = channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        header[32] = (blockAlign.toInt() and 0xff).toByte()
        header[33] = ((blockAlign.toInt() shr 8) and 0xff).toByte()
        header[34] = (bitsPerSample.toInt() and 0xff).toByte()
        header[35] = ((bitsPerSample.toInt() shr 8) and 0xff).toByte()
        // 'data' chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLength and 0xff).toByte()
        header[41] = ((totalAudioLength shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLength shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLength shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }
}
