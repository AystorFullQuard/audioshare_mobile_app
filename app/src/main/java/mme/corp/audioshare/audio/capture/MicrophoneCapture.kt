package mme.corp.audioshare.audio.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

class MicrophoneCapture(
    private val context: Context
) {

    companion object {
        const val SAMPLE_RATE = 48_000

        // 10 ms at 48 kHz mono PCM16:
        // 48_000 samples/sec * 2 bytes/sample * 0.010 sec = 960 bytes
        const val FRAME_SIZE_BYTES = 960
    }

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val bufferSize = maxOf(minBufferSize, FRAME_SIZE_BYTES * 4)

    private var audioRecord: AudioRecord? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission()) {
            throw SecurityException("RECORD_AUDIO permission is not granted")
        }

        require(minBufferSize > 0) {
            "Invalid AudioRecord minBufferSize=$minBufferSize"
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord is not initialized"
        }

        record.startRecording()
        audioRecord = record
    }

    fun read(buffer: ByteArray): Int {
        val record = audioRecord ?: return AudioRecord.ERROR_INVALID_OPERATION
        return record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
    }

    fun stop() {
        val record = audioRecord ?: return

        try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        } catch (_: IllegalStateException) {
            //todo
        } finally {
            record.release()
            audioRecord = null
        }
    }

    fun getBufferSize(): Int = bufferSize
}