package mme.corp.audioshare.audio

import kotlin.math.PI
import kotlin.math.sin

class TestToneGenerator(
    private val sampleRate: Int = 48_000,
    private val frequencyHz: Double = 220.0,
    private val amplitude: Double = 0.35
) {
    private var phase = 0.0

    fun nextFrame(frameSizeBytes: Int): ByteArray {
        val samples = frameSizeBytes / 2 // PCM16 mono
        val buffer = ByteArray(frameSizeBytes)

        for (i in 0 until samples) {
            val sample = (sin(phase) * Short.MAX_VALUE * amplitude).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()

            phase += 2.0 * PI * frequencyHz / sampleRate
            if (phase > 2.0 * PI) {
                phase -= 2.0 * PI
            }
        }

        return buffer
    }
}