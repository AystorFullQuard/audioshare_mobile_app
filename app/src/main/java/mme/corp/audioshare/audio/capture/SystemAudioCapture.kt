package mme.corp.audioshare.audio.capture

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build

class SystemAudioCapture(
    private val context: Context,
    private val resultCode: Int,
    private val projectionData: Intent
) {

    companion object {
        const val SAMPLE_RATE = 48_000
        const val FRAME_SIZE_BYTES = 960
    }

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val bufferSize = maxOf(minBufferSize, FRAME_SIZE_BYTES * 4)

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null

    @SuppressLint("NewApi", "MissingPermission")
    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw UnsupportedOperationException(
                "System audio capture requires Android 10+"
            )
        }

        require(minBufferSize > 0) {
            "Invalid AudioRecord minBufferSize=$minBufferSize"
        }

        val projectionManager = context.getSystemService(
            MediaProjectionManager::class.java
        )

        val projection = projectionManager.getMediaProjection(
            resultCode,
            projectionData
        ) ?: error("MediaProjection is null")

        mediaProjection = projection

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val record = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()

        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "System AudioRecord is not initialized"
        }

        record.startRecording()
        audioRecord = record
    }

    fun read(buffer: ByteArray): Int {
        val record = audioRecord ?: return AudioRecord.ERROR_INVALID_OPERATION
        return record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
    }

    fun stop() {
        val record = audioRecord
        audioRecord = null

        if (record != null) {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: IllegalStateException) {
                // Already stopped or invalid state.
            } finally {
                record.release()
            }
        }

        mediaProjection?.stop()
        mediaProjection = null
    }
}