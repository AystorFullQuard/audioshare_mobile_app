package mme.corp.audioshare.audio.capture

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class MicrophoneCapture {

    private val sampleRate = 48000

    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )

    fun start(){
        audioRecord.startRecording()
    }

    fun stop(){
        audioRecord.stop()
    }

    fun read(buffer: ByteArray): Int {
        return audioRecord.read(buffer, 0, buffer.size)
    }

    fun release(){
        audioRecord.release()
    }

    fun getBufferSize(): Int {
        return bufferSize
    }
}